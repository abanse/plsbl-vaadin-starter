package com.hydro.plsbl.api;

import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.masterdata.Stockyard;
import com.hydro.plsbl.kafka.dto.KafkaPickupOrderMessage;
import com.hydro.plsbl.repository.StockyardRepository;
import com.hydro.plsbl.dto.SawStatusDTO;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.IngotStorageService;
import com.hydro.plsbl.service.SawStatusService;
import com.hydro.plsbl.service.TransportOrderProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * REST-Controller fuer Test-Endpunkte zur Simulation der Einlagerung
 *
 * Diese Endpunkte ermöglichen das Testen der Einlagerungs-Funktionalitaet
 * ohne echte Kafka-Nachrichten.
 *
 * Endpunkte:
 * - POST /api/test/storage - Simuliert eine Einlagerungs-Anfrage
 * - POST /api/test/storage/quick - Schnell-Test mit generierten Daten
 */
@RestController
@RequestMapping("/api/test")
public class StorageTestController {

    private static final Logger log = LoggerFactory.getLogger(StorageTestController.class);

    private final IngotStorageService ingotStorageService;
    private final TransportOrderProcessor orderProcessor;
    private final StockyardRepository stockyardRepository;
    private final IngotService ingotService;
    private final SawStatusService sawStatusService;
    private final AtomicLong testCounter = new AtomicLong(System.currentTimeMillis());

    public StorageTestController(IngotStorageService ingotStorageService,
                                  TransportOrderProcessor orderProcessor,
                                  StockyardRepository stockyardRepository,
                                  IngotService ingotService,
                                  SawStatusService sawStatusService) {
        this.ingotStorageService = ingotStorageService;
        this.orderProcessor = orderProcessor;
        this.stockyardRepository = stockyardRepository;
        this.ingotService = ingotService;
        this.sawStatusService = sawStatusService;
    }

    /**
     * Startet die automatische Auftragsverarbeitung
     */
    @PostMapping("/autoprocessing/start")
    public ResponseEntity<Map<String, Object>> startAutoProcessing() {
        log.info("Starte Auto-Processing...");
        orderProcessor.startAutoProcessing();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("autoProcessingEnabled", orderProcessor.isAutoProcessingEnabled());
        response.put("message", "Auto-Processing gestartet");
        response.put("pendingOrders", orderProcessor.getPendingOrderCount());

        return ResponseEntity.ok(response);
    }

    /**
     * Stoppt die automatische Auftragsverarbeitung
     */
    @PostMapping("/autoprocessing/stop")
    public ResponseEntity<Map<String, Object>> stopAutoProcessing() {
        log.info("Stoppe Auto-Processing...");
        orderProcessor.stopAutoProcessing();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("autoProcessingEnabled", orderProcessor.isAutoProcessingEnabled());
        response.put("message", "Auto-Processing gestoppt");

        return ResponseEntity.ok(response);
    }

    /**
     * Gibt den Status des Auto-Processing zurueck
     */
    @GetMapping("/autoprocessing/status")
    public ResponseEntity<Map<String, Object>> getAutoProcessingStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("autoProcessingEnabled", orderProcessor.isAutoProcessingEnabled());
        response.put("processing", orderProcessor.isProcessing());
        response.put("pendingOrders", orderProcessor.getPendingOrderCount());
        response.put("currentOrder", orderProcessor.getCurrentOrder()
            .map(o -> Map.of("transportNo", o.getTransportNo(), "status", o.getStatus().name()))
            .orElse(null));

        return ResponseEntity.ok(response);
    }

    /**
     * Simuliert eine Einlagerungs-Anfrage mit vollstaendigen Daten
     *
     * @param request Die Einlagerungs-Anfrage im Kafka-Format
     * @return Transport-Auftrag oder Fehlermeldung
     */
    @PostMapping("/storage")
    public ResponseEntity<Map<String, Object>> simulateStorage(@RequestBody KafkaPickupOrderMessage request) {
        log.info("=============================================");
        log.info("REST API /api/test/storage aufgerufen");
        log.info("  Empfangene Barren-Nr: {}", request.getIngotNumber());
        log.info("  Empfangenes Produkt: {}", request.getProductNumber());
        log.info("  Empfangene Länge: {} mm", request.getLength());
        log.info("=============================================");

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());

        try {
            TransportOrderDTO order = ingotStorageService.processStorageRequest(
                request.getIngotNumber(),
                request.getProductNumber(),
                request.getWeight(),
                request.getLength(),
                request.getWidth(),
                request.getHeight(),
                request.isHeadSawn(),
                request.isFootSawn()
            );

            response.put("success", true);
            response.put("message", "Einlagerung erfolgreich angelegt");
            response.put("transportOrder", Map.of(
                "id", order.getId(),
                "transportNo", order.getTransportNo(),
                "fromYardId", order.getFromYardId(),
                "fromYardNo", order.getFromYardNo() != null ? order.getFromYardNo() : "N/A",
                "toYardId", order.getToYardId(),
                "toYardNo", order.getToYardNo() != null ? order.getToYardNo() : "N/A",
                "status", order.getStatus() != null ? order.getStatus().name() : "PENDING"
            ));

            log.info("Test-Einlagerung erfolgreich: Auftrag={}", order.getTransportNo());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Test-Einlagerung fehlgeschlagen: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Schnell-Test mit automatisch generierten Daten
     *
     * Generiert automatisch:
     * - Barren-Nummer: TEST-XXXX (fortlaufend)
     * - Produkt: 4047-001 (Standard-Aluminium-Barren)
     * - Standard-Masse und Gewicht
     *
     * @return Transport-Auftrag oder Fehlermeldung
     */
    @PostMapping("/storage/quick")
    public ResponseEntity<Map<String, Object>> quickStorageTest() {
        long id = testCounter.incrementAndGet();
        String ingotNumber = "TEST-" + String.format("%04d", id % 10000);
        String productNumber = "4047-001"; // Standard-Produkt

        log.info("=============================================");
        log.info("REST API /api/test/storage/quick aufgerufen");
        log.info("  GENERIERTE Barren-Nr: {}", ingotNumber);
        log.info("  (Dies ist der QUICK-Test - nicht der normale Endpoint!)");
        log.info("=============================================");

        KafkaPickupOrderMessage request = new KafkaPickupOrderMessage();
        request.setIngotNumber(ingotNumber);
        request.setProductNumber(productNumber);
        request.setWeight(2500);    // 2.5 Tonnen
        request.setLength(5000);    // 5m
        request.setWidth(800);      // 80cm
        request.setHeight(350);     // 35cm
        request.setHeadSawn(true);
        request.setFootSawn(false);
        request.setRotated(false);

        return simulateStorage(request);
    }

    /**
     * Generiert mehrere Test-Einlagerungen
     *
     * @param count Anzahl der zu erstellenden Einlagerungen (max 10)
     * @return Liste der erstellten Transport-Auftraege
     */
    @PostMapping("/storage/batch/{count}")
    public ResponseEntity<Map<String, Object>> batchStorageTest(@PathVariable int count) {
        count = Math.min(count, 10); // Maximal 10 auf einmal
        log.info("Batch-Test-Einlagerung: {} Auftraege", count);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("requestedCount", count);

        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        String[] products = {"4047-001", "4047-002", "5052-001", "6061-001"};

        for (int i = 0; i < count; i++) {
            try {
                long id = testCounter.incrementAndGet();
                String ingotNumber = "BATCH-" + String.format("%04d", id % 10000);
                String productNumber = products[i % products.length];

                TransportOrderDTO order = ingotStorageService.processStorageRequest(
                    ingotNumber,
                    productNumber,
                    2000 + (int)(Math.random() * 1000),
                    4500 + (int)(Math.random() * 1000),
                    700 + (int)(Math.random() * 200),
                    300 + (int)(Math.random() * 100),
                    true,
                    i % 2 == 0
                );

                results.add(Map.of(
                    "ingotNumber", ingotNumber,
                    "success", true,
                    "transportNo", order.getTransportNo()
                ));
                successCount++;

            } catch (Exception e) {
                results.add(Map.of(
                    "ingotNumber", "BATCH-" + (testCounter.get() % 10000),
                    "success", false,
                    "error", e.getMessage()
                ));
                failCount++;
            }
        }

        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("results", results);

        return ResponseEntity.ok(response);
    }

    /**
     * Zeigt die aktuelle Warteschlange auf der Saege
     */
    @GetMapping("/storage/queue")
    public ResponseEntity<Map<String, Object>> getQueue() {
        log.info("Abfrage der Säge-Warteschlange");

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());

        try {
            int queueSize = ingotStorageService.getQueueSize();
            var queueItems = ingotStorageService.getQueueItems();

            response.put("queueSize", queueSize);
            response.put("items", queueItems);
            response.put("message", queueSize == 0 ? "Warteschlange ist leer" : queueSize + " Barren in Warteschlange");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Fehler beim Abrufen der Warteschlange: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Loescht alle Barren auf der Saege (leert die Warteschlange)
     */
    @DeleteMapping("/storage/queue")
    public ResponseEntity<Map<String, Object>> clearQueue() {
        log.info("Loesche Säge-Warteschlange");

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());

        try {
            int deleted = ingotStorageService.clearSawPosition();
            response.put("success", true);
            response.put("deletedCount", deleted);
            response.put("message", deleted + " Barren geloescht");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Fehler beim Loeschen der Warteschlange: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Gibt alle Stockyards mit einer bestimmten Usage zurueck
     */
    /**
     * Gibt alle Stockyards mit einer bestimmten Usage zurueck.
     * Optional: ?available=true zeigt nur verfuegbare Plaetze (toStockAllowed=true und leer)
     */
    @GetMapping("/stockyards/usage/{usage}")
    public ResponseEntity<Map<String, Object>> getStockyardsByUsage(
            @PathVariable String usage,
            @RequestParam(required = false, defaultValue = "false") boolean available) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("requestedUsage", usage);
        response.put("filterAvailable", available);

        List<Map<String, Object>> yards = new java.util.ArrayList<>();
        int totalCount = 0;
        int availableCount = 0;

        for (Stockyard yard : stockyardRepository.findAll()) {
            String yardUsage = yard.getUsage() != null ? yard.getUsage().name() : "null";
            if (yardUsage.equalsIgnoreCase(usage) ||
                (usage.equalsIgnoreCase("null") && yard.getUsage() == null)) {

                totalCount++;
                int ingotCount = ingotService.countByStockyardId(yard.getId());
                boolean isEmpty = ingotCount == 0;
                boolean isAvailable = yard.isToStockAllowed() && isEmpty;

                if (isAvailable) availableCount++;

                // Filter wenn available=true
                if (available && !isAvailable) continue;

                Map<String, Object> yardInfo = new HashMap<>();
                yardInfo.put("id", yard.getId());
                yardInfo.put("yardNumber", yard.getYardNumber());
                yardInfo.put("usage", yardUsage);
                yardInfo.put("xCoordinate", yard.getXCoordinate());
                yardInfo.put("yCoordinate", yard.getYCoordinate());
                yardInfo.put("toStockAllowed", yard.isToStockAllowed());
                yardInfo.put("ingotCount", ingotCount);
                yardInfo.put("maxIngots", yard.getMaxIngots());
                yardInfo.put("available", isAvailable);
                yards.add(yardInfo);
            }
        }
        response.put("totalCount", totalCount);
        response.put("availableCount", availableCount);
        response.put("returnedCount", yards.size());
        response.put("stockyards", yards);
        return ResponseEntity.ok(response);
    }

    /**
     * Gibt Details zu einem Stockyard zurueck (fuer Diagnose)
     */
    @GetMapping("/stockyard/{id}")
    public ResponseEntity<Map<String, Object>> getStockyard(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());

        return stockyardRepository.findById(id)
            .map(yard -> {
                response.put("id", yard.getId());
                response.put("yardNumber", yard.getYardNumber());
                response.put("xCoordinate", yard.getXCoordinate());
                response.put("yCoordinate", yard.getYCoordinate());
                response.put("type", yard.getType() != null ? yard.getType().name() : null);
                response.put("usage", yard.getUsage() != null ? yard.getUsage().name() : null);
                response.put("xPosition", yard.getXPosition());
                response.put("yPosition", yard.getYPosition());
                response.put("zPosition", yard.getZPosition());
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> {
                response.put("error", "Stockyard not found: " + id);
                return ResponseEntity.notFound().build();
            });
    }


    /**
     * Gibt den aktuellen Säge-Status zurück (für Debugging)
     */
    @GetMapping("/saw/status")
    public ResponseEntity<Map<String, Object>> getSawStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());

        sawStatusService.getCurrentStatus().ifPresent(status -> {
            response.put("hasError", status.hasError());
            response.put("errorType", status.getErrorType());
            response.put("errorMessage", status.getErrorMessage());
            response.put("pickupMode", status.getPickupMode() != null ? status.getPickupMode().name() : null);
        });

        return ResponseEntity.ok(response);
    }

    /**
     * Gibt eine Beschreibung der verfuegbaren Test-Endpunkte zurueck
     */
    @GetMapping("/storage/help")
    public ResponseEntity<Map<String, Object>> help() {
        Map<String, Object> help = new HashMap<>();
        help.put("description", "Test-Endpunkte fuer Einlagerungs-Simulation");
        help.put("endpoints", java.util.List.of(
            Map.of(
                "method", "POST",
                "path", "/api/test/storage",
                "description", "Simuliert Einlagerung mit benutzerdefinierten Daten",
                "body", Map.of(
                    "ingotNumber", "Barren-Nummer (String)",
                    "productNumber", "Produkt-Nummer (String)",
                    "weight", "Gewicht in kg (int)",
                    "length", "Laenge in mm (int)",
                    "width", "Breite in mm (int)",
                    "height", "Hoehe in mm (int)",
                    "headSawn", "Kopf gesaegt (boolean)",
                    "footSawn", "Fuss gesaegt (boolean)"
                )
            ),
            Map.of(
                "method", "POST",
                "path", "/api/test/storage/quick",
                "description", "Schnell-Test mit generierten Standard-Daten"
            ),
            Map.of(
                "method", "POST",
                "path", "/api/test/storage/batch/{count}",
                "description", "Erstellt mehrere Test-Einlagerungen (max 10)"
            ),
            Map.of(
                "method", "GET",
                "path", "/api/test/storage/queue",
                "description", "Zeigt aktuelle Warteschlange auf der Saege"
            ),
            Map.of(
                "method", "DELETE",
                "path", "/api/test/storage/queue",
                "description", "Loescht alle Barren auf der Saege (leert Warteschlange)"
            )
        ));
        return ResponseEntity.ok(help);
    }
}
