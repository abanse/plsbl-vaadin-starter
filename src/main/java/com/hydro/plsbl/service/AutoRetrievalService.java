package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.IngotTypeDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.masterdata.Stockyard;
import com.hydro.plsbl.repository.StockyardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service fuer die automatische Auslagerung von Barren.
 *
 * MITTEL-Barren (autoRetrieval=true) werden automatisch vom externen Lager
 * zum Belade-Bereich gebracht, wenn ein Abruf genehmigt wird.
 *
 * Workflow:
 * 1. Abruf wird genehmigt
 * 2. System prueft ob das Produkt einen Barrentyp mit autoRetrieval=true hat
 * 3. Falls ja: Passende Barren auf externen Plaetzen suchen
 * 4. Transport-Auftraege (Stapler) zum Belade-Bereich erstellen
 * 5. Stapler-Fahrer wird benachrichtigt (via StaplerAnforderungBroadcaster)
 */
@Service
public class AutoRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(AutoRetrievalService.class);

    // Externe Lagerplaetze (Stapler-Bereich)
    private static final String YARD_TYPE_EXTERNAL = "E";
    // Belade-Bereich (Ziel fuer Auto-Auslagerung)
    private static final String YARD_TYPE_LOADING = "L";
    // SWAPIN-Plaetze (Eingang vom externen Lager)
    private static final String YARD_TYPE_SWAPIN = "N";

    private final IngotService ingotService;
    private final IngotTypeService ingotTypeService;
    private final TransportOrderService transportOrderService;
    private final StockyardRepository stockyardRepository;
    private final StaplerAnforderungBroadcaster staplerBroadcaster;
    private final JdbcTemplate jdbcTemplate;

    public AutoRetrievalService(
            IngotService ingotService,
            IngotTypeService ingotTypeService,
            TransportOrderService transportOrderService,
            StockyardRepository stockyardRepository,
            StaplerAnforderungBroadcaster staplerBroadcaster,
            JdbcTemplate jdbcTemplate) {
        this.ingotService = ingotService;
        this.ingotTypeService = ingotTypeService;
        this.transportOrderService = transportOrderService;
        this.stockyardRepository = stockyardRepository;
        this.staplerBroadcaster = staplerBroadcaster;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Prueft bei Abruf-Genehmigung ob automatische Auslagerung erforderlich ist.
     * Wird von CalloffService.approve() aufgerufen.
     *
     * @param calloff Der genehmigte Abruf (als DTO)
     * @return Anzahl der erstellten Stapler-Anforderungen
     */
    @Transactional
    public int processCalloffApproval(CalloffDTO calloff) {
        if (calloff == null) {
            log.warn("Abruf ist null");
            return 0;
        }

        log.info("=== Auto-Auslagerung pruefen fuer Abruf {} ===", calloff.getCalloffNumber());
        log.info("Abruf: {}, Produkt-ID={}, Restmenge={}",
            calloff.getCalloffNumber(), calloff.getProductId(), calloff.getRemainingAmount());

        // Keine Restmenge -> nichts zu tun
        if (calloff.getRemainingAmount() <= 0) {
            log.info("Abruf bereits vollstaendig geliefert");
            return 0;
        }

        // Barren auf externen Plaetzen fuer dieses Produkt suchen
        List<IngotDTO> externeBarren = findExternalIngots(calloff.getProductId());
        if (externeBarren.isEmpty()) {
            log.info("Keine Barren auf externen Plaetzen gefunden");
            return 0;
        }

        log.info("Gefunden: {} Barren auf externen Plaetzen", externeBarren.size());

        // Pruefen ob Barrentyp autoRetrieval=true hat
        if (!hasAutoRetrievalBarren(externeBarren)) {
            log.info("Keine Barren mit autoRetrieval=true gefunden");
            return 0;
        }

        // Anzahl benoetigter Barren berechnen
        int benoetigteMenge = calloff.getRemainingAmount();
        int anzahlZuHolen = Math.min(benoetigteMenge, externeBarren.size());

        log.info("*** AUTO-AUSLAGERUNG ERFORDERLICH ***");
        log.info("  Benoetigt: {} Barren", benoetigteMenge);
        log.info("  Verfuegbar (extern): {} Barren", externeBarren.size());
        log.info("  Zu holen: {} Barren", anzahlZuHolen);

        // Ziel-Lagerplatz finden (SWAPIN oder LOADING)
        Stockyard zielPlatz = findSwapinOrLoadingYard()
            .orElse(null);

        if (zielPlatz == null) {
            log.warn("Kein SWAPIN/LOADING Platz gefunden - Anforderung ohne Ziel erstellen");
        } else {
            log.info("Ziel-Platz: {} ({})", zielPlatz.getYardNumber(), zielPlatz.getType());
        }

        // Stapler-Anforderungen erstellen
        List<StaplerAnforderung> anforderungen = new ArrayList<>();
        for (int i = 0; i < anzahlZuHolen; i++) {
            IngotDTO barren = externeBarren.get(i);

            StaplerAnforderung anforderung = createStaplerAnforderung(
                barren, calloff, zielPlatz);

            if (anforderung != null) {
                anforderungen.add(anforderung);
                log.info("  Anforderung erstellt: Barren {} von {} nach {}",
                    barren.getIngotNo(),
                    barren.getStockyardNo(),
                    zielPlatz != null ? zielPlatz.getYardNumber() : "Belade-Bereich");
            }
        }

        // Benachrichtigung an Stapler-Fahrer senden
        if (!anforderungen.isEmpty()) {
            staplerBroadcaster.broadcast(anforderungen);
            log.info("=== {} Stapler-Anforderungen gesendet ===", anforderungen.size());
        }

        return anforderungen.size();
    }

    /**
     * Findet Barren auf externen Lagerplaetzen fuer ein Produkt.
     * Sortiert nach FIFO (aelteste zuerst).
     */
    private List<IngotDTO> findExternalIngots(Long productId) {
        try {
            // Alle externen Plaetze finden
            List<Long> externalYardIds = new ArrayList<>();
            for (Stockyard yard : stockyardRepository.findByType(YARD_TYPE_EXTERNAL)) {
                externalYardIds.add(yard.getId());
            }

            if (externalYardIds.isEmpty()) {
                return List.of();
            }

            // Barren auf externen Plaetzen suchen
            List<IngotDTO> result = new ArrayList<>();
            for (IngotDTO ingot : ingotService.findAllInStock()) {
                if (ingot.getStockyardId() != null &&
                    externalYardIds.contains(ingot.getStockyardId())) {
                    // Bei Produkt-Filter: nur passende Barren
                    if (productId == null || productId.equals(ingot.getProductId())) {
                        result.add(ingot);
                    }
                }
            }

            // Nach Einlagerungsdatum sortieren (FIFO)
            result.sort(Comparator.comparing(
                i -> i.getInStockSince() != null ? i.getInStockSince() : java.time.LocalDateTime.MIN));

            return result;

        } catch (Exception e) {
            log.error("Fehler beim Suchen externer Barren: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Prueft ob mindestens ein Barren einen Typ mit autoRetrieval=true hat.
     */
    private boolean hasAutoRetrievalBarren(List<IngotDTO> barren) {
        for (IngotDTO ingot : barren) {
            Integer length = ingot.getLength();
            if (length == null) continue;

            Optional<IngotTypeDTO> typeOpt = ingotTypeService.determineIngotType(
                length, ingot.getWidth(), ingot.getThickness(), ingot.getWeight(), null);

            if (typeOpt.isPresent() && Boolean.TRUE.equals(typeOpt.get().getAutoRetrieval())) {
                log.info("Barren {} hat Typ {} mit autoRetrieval=true",
                    ingot.getIngotNo(), typeOpt.get().getName());
                return true;
            }
        }
        return false;
    }

    /**
     * Findet einen SWAPIN oder LOADING Platz als Ziel fuer Stapler.
     */
    private Optional<Stockyard> findSwapinOrLoadingYard() {
        // Zuerst SWAPIN Plaetze suchen
        List<Stockyard> swapinYards = stockyardRepository.findByType(YARD_TYPE_SWAPIN);
        for (Stockyard yard : swapinYards) {
            if (yard.isToStockAllowed() && isYardEmpty(yard.getId())) {
                return Optional.of(yard);
            }
        }

        // Fallback: LOADING Plaetze
        List<Stockyard> loadingYards = stockyardRepository.findByType(YARD_TYPE_LOADING);
        for (Stockyard yard : loadingYards) {
            if (yard.isToStockAllowed() && isYardEmpty(yard.getId())) {
                return Optional.of(yard);
            }
        }

        return Optional.empty();
    }

    /**
     * Prueft ob ein Lagerplatz leer ist.
     */
    private boolean isYardEmpty(Long stockyardId) {
        return ingotService.countByStockyardId(stockyardId) == 0;
    }

    /**
     * Erstellt eine Stapler-Anforderung fuer einen Barren.
     */
    private StaplerAnforderung createStaplerAnforderung(
            IngotDTO barren, CalloffDTO calloff, Stockyard zielPlatz) {

        try {
            // Transport-Auftrag in DB erstellen (Typ: STAPLER)
            TransportOrderDTO order = new TransportOrderDTO();
            order.setTransportNo(generateStaplerTransportNo());
            order.setNormText("AUTO-AUSLAGERUNG fuer Abruf " + calloff.getCalloffNumber());
            order.setIngotId(barren.getId());
            order.setFromYardId(barren.getStockyardId());
            order.setFromPilePosition(barren.getPilePosition());
            order.setToYardId(zielPlatz != null ? zielPlatz.getId() : null);
            order.setPriority(20); // Hohe Prioritaet fuer Auto-Auslagerung

            TransportOrderDTO savedOrder = transportOrderService.save(order);

            // Stapler-Anforderung erstellen
            StaplerAnforderung anforderung = new StaplerAnforderung();
            anforderung.setTransportOrderId(savedOrder.getId());
            anforderung.setTransportNo(savedOrder.getTransportNo());
            anforderung.setBarrenNo(barren.getIngotNo());
            anforderung.setVonPlatz(barren.getStockyardNo());
            anforderung.setNachPlatz(zielPlatz != null ? zielPlatz.getYardNumber() : "Belade-Bereich");
            anforderung.setCalloffNo(calloff.getCalloffNumber());
            anforderung.setAuftragNo(calloff.getOrderNumber());
            anforderung.setGewicht(barren.getWeight());
            anforderung.setLaenge(barren.getLength());

            return anforderung;

        } catch (Exception e) {
            log.error("Fehler beim Erstellen der Stapler-Anforderung: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generiert eine Transportnummer fuer Stapler-Auftraege.
     * Format: STYY-NNNN (z.B. ST26-0001)
     */
    private String generateStaplerTransportNo() {
        int year = LocalDate.now().getYear() % 100;
        String prefix = "ST" + String.format("%02d", year) + "-";
        try {
            Integer maxNo = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(TO_NUMBER(SUBSTR(TRANSPORT_NO, 6))), 0) + 1 FROM TD_TRANSPORTORDER WHERE TRANSPORT_NO LIKE ?",
                Integer.class, prefix + "%");
            return prefix + String.format("%04d", maxNo != null ? maxNo : 1);
        } catch (Exception e) {
            return prefix + String.format("%04d", (System.currentTimeMillis() % 10000));
        }
    }

    /**
     * Prueft offene Abrufe und erstellt Stapler-Anforderungen fuer faellige.
     * Kann periodisch aufgerufen werden (@Scheduled).
     *
     * @param faelligeAbrufe Liste der faelligen Abrufe (von CalloffService bereitgestellt)
     * @return Anzahl der erstellten Anforderungen
     */
    @Transactional
    public int checkPendingCalloffs(List<CalloffDTO> faelligeAbrufe) {
        log.debug("Pruefe {} offene Abrufe fuer Auto-Auslagerung...", faelligeAbrufe.size());

        int totalAnforderungen = 0;

        try {
            for (CalloffDTO calloff : faelligeAbrufe) {
                if (calloff.isApproved() && !calloff.isCompleted() && calloff.getRemainingAmount() > 0) {
                    // Pruefen ob bereits Anforderungen existieren
                    if (!hasOpenStaplerAnforderung(calloff.getId())) {
                        int created = processCalloffApproval(calloff);
                        totalAnforderungen += created;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Fehler beim Pruefen offener Abrufe: {}", e.getMessage());
        }

        return totalAnforderungen;
    }

    /**
     * Prueft ob fuer einen Abruf bereits offene Stapler-Anforderungen existieren.
     */
    private boolean hasOpenStaplerAnforderung(Long calloffId) {
        try {
            // Pruefen ob Transport-Auftraege mit "AUTO-AUSLAGERUNG" im Normtext existieren
            // die noch nicht abgeschlossen sind
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_TRANSPORTORDER " +
                "WHERE NORMTEXT LIKE '%AUTO-AUSLAGERUNG%' " +
                "AND STATUS IN ('P', 'I', 'U', 'H')",
                Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // === Inner class fuer Stapler-Anforderung ===

    /**
     * DTO fuer eine Stapler-Anforderung.
     * Wird an den Stapler-Fahrer gesendet.
     */
    public static class StaplerAnforderung {
        private Long transportOrderId;
        private String transportNo;
        private String barrenNo;
        private String vonPlatz;
        private String nachPlatz;
        private String calloffNo;
        private String auftragNo;
        private Integer gewicht;
        private Integer laenge;

        // Getters & Setters
        public Long getTransportOrderId() { return transportOrderId; }
        public void setTransportOrderId(Long transportOrderId) { this.transportOrderId = transportOrderId; }

        public String getTransportNo() { return transportNo; }
        public void setTransportNo(String transportNo) { this.transportNo = transportNo; }

        public String getBarrenNo() { return barrenNo; }
        public void setBarrenNo(String barrenNo) { this.barrenNo = barrenNo; }

        public String getVonPlatz() { return vonPlatz; }
        public void setVonPlatz(String vonPlatz) { this.vonPlatz = vonPlatz; }

        public String getNachPlatz() { return nachPlatz; }
        public void setNachPlatz(String nachPlatz) { this.nachPlatz = nachPlatz; }

        public String getCalloffNo() { return calloffNo; }
        public void setCalloffNo(String calloffNo) { this.calloffNo = calloffNo; }

        public String getAuftragNo() { return auftragNo; }
        public void setAuftragNo(String auftragNo) { this.auftragNo = auftragNo; }

        public Integer getGewicht() { return gewicht; }
        public void setGewicht(Integer gewicht) { this.gewicht = gewicht; }

        public Integer getLaenge() { return laenge; }
        public void setLaenge(Integer laenge) { this.laenge = laenge; }

        @Override
        public String toString() {
            return String.format("StaplerAnforderung{%s: %s -> %s, Barren=%s, Abruf=%s}",
                transportNo, vonPlatz, nachPlatz, barrenNo, calloffNo);
        }
    }
}
