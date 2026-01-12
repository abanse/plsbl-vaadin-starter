# PLSBL Vaadin Starter

Spring Boot + Vaadin Neuimplementierung des PLS Barrenlager Systems.

## Schnellstart

### 1. Projekt starten (mit H2 Test-Datenbank)

```bash
cd plsbl-vaadin-starter
mvn spring-boot:run
```

Der Browser öffnet automatisch: **http://localhost:8080**

### 2. Mit Oracle-Datenbank starten

```bash
mvn spring-boot:run -Poracle
```

Vorher `application-oracle.properties` anpassen:
```properties
spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/xe
spring.datasource.username=plsbl
spring.datasource.password=hydroplsbl
```

## Features

### ✅ Implementiert

- **Lager-Grid** - Interaktive Ansicht aller Lagerplätze
- **Farbkodierung** - Leer, teilweise belegt, voll
- **Sperr-Markierungen** - Einlagern/Auslagern gesperrt
- **Tooltips** - Produkt und Belegung auf Hover
- **Info-Dialog** - Details bei Klick
- **Responsive** - Grid passt sich an

### 🔜 Noch zu implementieren

- SaegeView (Säge)
- BeladungView (Beladung/Verladung)
- KranView (Kran-Status)
- AlarmeView (Alarme/Meldungen)
- Real-time Updates (WebSocket)
- Kran-Animation

## Projekt-Struktur

```
src/main/java/com/hydro/plsbl/
├── PlsblApplication.java      # Spring Boot Main
├── entity/
│   ├── masterdata/
│   │   └── Stockyard.java     # Lagerplatz
│   ├── transdata/
│   │   └── StockyardStatus.java
│   └── enums/
│       ├── StockyardType.java
│       └── StockyardUsage.java
├── repository/
│   ├── StockyardRepository.java
│   └── StockyardStatusRepository.java
├── service/
│   └── StockyardService.java
├── dto/
│   ├── StockyardDTO.java
│   └── StockyardStatusDTO.java
└── ui/
    ├── MainLayout.java        # Navigation
    ├── view/
    │   └── LagerView.java     # Lager-Ansicht
    ├── component/
    │   └── LagerGrid.java     # Grid-Komponente
    └── dialog/
        └── StockyardInfoDialog.java
```

## Datenbank

### H2 (Entwicklung)

- Automatisch mit Testdaten gefüllt
- H2 Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:plsbl`
- User: `sa`, Passwort: (leer)

### Oracle (Produktion)

Benötigte Tabellen:
- `MD_STOCKYARD` - Lagerplatz-Stammdaten
- `TD_STOCKYARD_STATUS` - Status/Belegung
- `MD_PRODUCT` - Produkte
- `TD_INGOT` - Barren
- `TD_CRANE_STATUS` - Kran-Status

## Technologie-Stack

| Komponente | Version |
|------------|---------|
| Java | 17+ |
| Spring Boot | 3.2.1 |
| Vaadin | 24.3.3 |
| Spring Data JDBC | 3.2.1 |
| Oracle JDBC | 21.x |
| H2 (Test) | 2.x |

## Entwicklung

### Hot-Reload aktivieren

Spring DevTools ist bereits konfiguriert. Änderungen werden automatisch neu geladen.

### Vaadin Production Build

```bash
mvn clean package -Pproduction
java -jar target/plsbl-vaadin-4.0.0-SNAPSHOT.jar
```

## Migration von Tentackle

Dieses Projekt ist eine Neuimplementierung des Tentackle-basierten PLSBL Systems.

| Alt (Tentackle) | Neu (Spring/Vaadin) |
|-----------------|---------------------|
| PDO Entities | Spring Data JDBC |
| JavaFX UI | Vaadin Web UI |
| Tentackle Session | Spring @Transactional |
| PdoTracker Events | Spring Events |

## Nächste Schritte

1. **SaegeView** - Säge-Ansicht implementieren
2. **CraneService** - Kran-Status polling
3. **WebSocket** - Real-time Updates
4. **Security** - Login implementieren

## Lizenz

Copyright (C) 2024 Hydro Aluminium Rolled Products GmbH
