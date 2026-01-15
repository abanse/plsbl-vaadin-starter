package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotDTO;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Session-Scoped Service für den Beladungs-Status.
 * Speichert den Status über View-Wechsel hinweg.
 */
@Service
@VaadinSessionScope
public class BeladungStateService {

    private boolean beladungAktiv = false;
    private boolean beladungLaeuft = false;
    private int beladungsNummer = 0;
    private String beladungsNr = "";
    private boolean langBarrenModus = false;
    private boolean kranKommandoGesendet = false;

    private List<IngotDTO> geplanteBarren = new ArrayList<>();
    private List<IngotDTO> geladeneBarren = new ArrayList<>();

    // === Getters & Setters ===

    public boolean isBeladungAktiv() {
        return beladungAktiv;
    }

    public void setBeladungAktiv(boolean beladungAktiv) {
        this.beladungAktiv = beladungAktiv;
    }

    public boolean isBeladungLaeuft() {
        return beladungLaeuft;
    }

    public void setBeladungLaeuft(boolean beladungLaeuft) {
        this.beladungLaeuft = beladungLaeuft;
    }

    public int getBeladungsNummer() {
        return beladungsNummer;
    }

    public void setBeladungsNummer(int beladungsNummer) {
        this.beladungsNummer = beladungsNummer;
    }

    public String getBeladungsNr() {
        return beladungsNr;
    }

    public void setBeladungsNr(String beladungsNr) {
        this.beladungsNr = beladungsNr;
    }

    public boolean isLangBarrenModus() {
        return langBarrenModus;
    }

    public void setLangBarrenModus(boolean langBarrenModus) {
        this.langBarrenModus = langBarrenModus;
    }

    public boolean isKranKommandoGesendet() {
        return kranKommandoGesendet;
    }

    public void setKranKommandoGesendet(boolean kranKommandoGesendet) {
        this.kranKommandoGesendet = kranKommandoGesendet;
    }

    public List<IngotDTO> getGeplanteBarren() {
        return geplanteBarren;
    }

    public void setGeplanteBarren(List<IngotDTO> geplanteBarren) {
        this.geplanteBarren = new ArrayList<>(geplanteBarren);
    }

    public List<IngotDTO> getGeladeneBarren() {
        return geladeneBarren;
    }

    public void setGeladeneBarren(List<IngotDTO> geladeneBarren) {
        this.geladeneBarren = new ArrayList<>(geladeneBarren);
    }

    /**
     * Setzt den kompletten Status zurück
     */
    public void reset() {
        beladungAktiv = false;
        beladungLaeuft = false;
        beladungsNr = "";
        langBarrenModus = false;
        kranKommandoGesendet = false;
        geplanteBarren.clear();
        geladeneBarren.clear();
    }

    /**
     * Nächste Beladungsnummer generieren
     */
    public String nextBeladungsNr() {
        beladungsNummer++;
        beladungsNr = "BEL-" + String.format("%05d", beladungsNummer);
        return beladungsNr;
    }

    /**
     * Verschiebt einen Barren von geplant nach geladen
     */
    public IngotDTO moveBarrenToGeladen() {
        if (!geplanteBarren.isEmpty()) {
            IngotDTO barren = geplanteBarren.remove(0);
            geladeneBarren.add(barren);
            return barren;
        }
        return null;
    }

    /**
     * Gibt den nächsten geplanten Barren zurück (ohne zu entfernen)
     */
    public IngotDTO peekNextBarren() {
        return geplanteBarren.isEmpty() ? null : geplanteBarren.get(0);
    }

    /**
     * Prüft ob noch Barren geplant sind
     */
    public boolean hasGeplanteBarren() {
        return !geplanteBarren.isEmpty();
    }

    /**
     * Gibt die Anzahl geladener Barren zurück
     */
    public int getGeladeneCount() {
        return geladeneBarren.size();
    }

    /**
     * Gibt die Gesamtanzahl (geplant + geladen) zurück
     */
    public int getTotalCount() {
        return geplanteBarren.size() + geladeneBarren.size();
    }

    /**
     * Berechnet das Gesamtgewicht der geladenen Barren
     */
    public int getGeladenGewicht() {
        return geladeneBarren.stream()
            .mapToInt(b -> b.getWeight() != null ? b.getWeight() : 0)
            .sum();
    }
}
