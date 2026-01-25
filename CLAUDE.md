# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PLSBL (PLS Barrenlager) is an industrial warehouse management system for aluminum ingots at Hydro Aluminium. It controls an automated crane to move ingots between storage locations (stockyards), a saw, and loading areas. The system is a Spring Boot + Vaadin reimplementation of a legacy Tentackle/JavaFX application.

**Key domain concepts:**
- **Stockyard** (Lagerplatz): Storage location with X/Y grid coordinates and physical crane coordinates (mm)
- **Ingot** (Barren): Aluminum ingot with dimensions, weight, and current location
- **TransportOrder**: Crane movement command from source to destination stockyard
- **Calloff** (Abruf): Order from SAP to move ingots for processing or shipment

## Build & Run Commands

```bash
# Run with H2 in-memory database (development)
mvn spring-boot:run

# Run with Oracle database (production)
mvn spring-boot:run -Poracle

# Production build with Vaadin frontend compilation
mvn clean package -Pproduction

# Run tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Run single test method
mvn test -Dtest=ClassName#methodName
```

**URLs:**
- Application: http://localhost:8085/ui/
- H2 Console: http://localhost:8085/h2-console (JDBC URL: `jdbc:h2:mem:plsbl`, user: `sa`, no password)

## Architecture

### Layer Structure

```
ui/                    # Vaadin views and components
  view/               # Main views (LagerView, SawView, BeladungView, etc.)
  component/          # Reusable UI components (LagerGrid, CraneStatusBar)
  dialog/             # Modal dialogs
service/              # Business logic
plc/                  # PLC/SPS communication (Apache PLC4X for Siemens S7)
simulator/            # Crane simulator for development without real PLC
kafka/                # SAP integration via Kafka messages
entity/               # Spring Data JDBC entities
  masterdata/         # MD_* tables (Stockyard, Product, AppSetting)
  transdata/          # TD_* tables (Ingot, TransportOrder, CraneStatus)
  enums/              # StockyardType, StockyardUsage, OrderStatus
repository/           # Spring Data JDBC repositories
dto/                  # Data transfer objects
security/             # Workstation-based crane access control
```

### Key Service Interactions

1. **TransportOrderProcessor** - Orchestrates crane movements:
   - Monitors pending orders (`@Scheduled`)
   - Sends commands via PlcService
   - Tracks job state through PLC status updates
   - Updates ingot locations on completion

2. **PlcService** - Abstracts PLC/Simulator:
   - Connects to Siemens S7 PLC via PLC4X (`s7://ip:102`)
   - Falls back to CraneSimulatorService when PLC unavailable
   - Polls status every 500ms, notifies listeners
   - DB addresses: DB100 (status read), DB101 (command write)

3. **CraneSimulatorService** - Simulates crane for development:
   - State machine with WorkPhases (IDLE → MOVE_TO_PICKUP → GRABBING → ... → IDLE)
   - Configurable via `plsbl.simulator.*` properties

4. **DataBroadcaster** - Cross-view real-time updates via Vaadin Push

### Database

Uses Spring Data JDBC (not JPA/Hibernate) for closer SQL control.

**Profiles:**
- `h2` (default): In-memory with `schema-h2.sql` + `data-h2.sql`
- `oracle`: Production Oracle database with `schema-oracle.sql`

**Schema Files:**
- `src/main/resources/schema-h2.sql` - H2 DDL (development)
- `src/main/resources/schema-oracle.sql` - Oracle DDL (production)
- `src/main/resources/data-h2.sql` - Test data for H2

**Tables (10 total):**

Stammdaten (MD_*):
- `MD_APPSETTING`: Key-value settings (SPS_ENABLED, SPS_URL, KAFKA_*, colors)
- `MD_STOCKYARD`: Storage locations with grid coords (X/Y_COORDINATE) and crane coords in mm (BOTTOM_CENTER_X/Y/Z)
- `MD_PRODUCT`: Products/articles with product number
- `MD_INGOTTYPE`: Ingot types (KURZ, MITTEL, LANG) with length ranges and permissions

Bewegungsdaten (TD_*):
- `TD_STOCKYARDSTATUS`: Current status per stockyard (ingot count, product)
- `TD_INGOT`: Ingot inventory with dimensions, weight, and current stockyard
- `TD_CRANESTATUS`: Crane position and status (single row)
- `TD_PLSSTATUS`: Saw/pickup status (single row)
- `TD_TRANSPORTORDER`: Movement orders with status lifecycle (P→I→U→C or F)
- `TD_CALLOFF`: Customer orders from SAP with requested/delivered amounts

### Crane Access Control

Only one workstation can control the crane at a time (token-based):
- Configured in `application.properties`: `plsbl.workstations.crane-controllers`
- CraneAccessService manages the control token
- PlsblSessionContext tracks per-session access state
- See `docs/ACCESS-CONTROL.md` for details

### Kafka Integration

Receives messages from SAP and saw:
- `KafkaCalloffMessage`: New calloff orders
- `KafkaPickupOrderMessage`: Saw pickup requests
- `KafkaSawFeedbackMessage`: Saw completion feedback

Sends events:
- `KafkaIngotEventMessage`: Ingot movements
- `KafkaShipmentMessage`: Loading completions

## Configuration

Key properties in `application.properties`:

```properties
# PLC settings stored in MD_APPSETTING table:
# SPS_ENABLED, SPS_URL (s7://10.72.242.190:102?remote-slot=1), SPS_TIMEOUT, SPS_RETRY_COUNT

# Simulator
plsbl.simulator.enabled=true
plsbl.simulator.auto-start=true
plsbl.simulator.interval-ms=500

# Crane position deltas per tick (mm)
plsbl.simulator.delta-x=1000
plsbl.simulator.delta-y=500
plsbl.simulator.delta-z=200
```

## Tech Stack

- Java 17
- Spring Boot 3.2.1
- Vaadin 24.3.3
- Spring Data JDBC
- Apache PLC4X 0.12.0 (Siemens S7 driver)
- Apache Kafka
- Oracle/H2 databases
