# PLSBL Zugriffskontrolle - Kran-Steuerung

## Übersicht

Das System stellt sicher, dass nur **ein** Arbeitsplatz gleichzeitig den Kran steuern kann.
Alle anderen sehen das Lager nur im Ansichtsmodus.

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   KRAN-PC-01          LAGER-PC-02          BÜRO-PC-03          │
│   ┌─────────┐        ┌─────────┐          ┌─────────┐          │
│   │ 🔓 CTRL │        │ 👁 VIEW │          │ 👁 VIEW │          │
│   └────┬────┘        └────┬────┘          └────┬────┘          │
│        │                  │                    │                │
│        ▼                  ▼                    ▼                │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │              CraneAccessService                          │  │
│   │  ┌─────────────────────────────────────────────────┐    │  │
│   │  │  Token: KRAN-PC-01                              │    │  │
│   │  │  User:  schmidt                                 │    │  │
│   │  │  Since: 09:15:30                                │    │  │
│   │  └─────────────────────────────────────────────────┘    │  │
│   └─────────────────────────────────────────────────────────┘  │
│                            │                                   │
│                            ▼                                   │
│                     ┌───────────┐                              │
│                     │    SPS    │                              │
│                     └───────────┘                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Konfiguration

### application.properties

```properties
# Arbeitsplätze die STEUERN dürfen (Whitelist)
plsbl.workstations.crane-controllers=KRAN-PC-01,KRAN-PC-02

# Arbeitsplätze die NUR ANSICHT haben
plsbl.workstations.view-only=BUERO-PC-01,BUERO-PC-02

# Standard für nicht konfigurierte PCs: VIEW (sicher!)
plsbl.workstations.default-mode=VIEW

# Token-System: Nur EINER gleichzeitig
plsbl.workstations.token-based-control=true

# Inaktivitäts-Timeout (5 Minuten)
plsbl.workstations.token-timeout-seconds=300
```

### Arbeitsplatz-Erkennung

Der Arbeitsplatz wird automatisch erkannt über:

1. **Hostname** (bevorzugt)
2. **IP-Adresse** (Fallback)
3. **X-Forwarded-For Header** (bei Proxy)

---

## Zugriffsmodi

| Modus | Symbol | Kann steuern? | Kann anfordern? |
|-------|--------|--------------|-----------------|
| **CONTROL** (aktiv) | 🔓 | ✅ Ja | - |
| **CONTROL** (möglich) | ⏸ | ❌ Nein | ✅ Ja |
| **VIEW** | 👁 | ❌ Nein | ❌ Nein |

---

## Ablauf

### 1. Steuerung anfordern

```java
// Im UI
CraneAccessService.ControlRequestResult result = sessionContext.requestCraneControl();

if (result.granted) {
    // Erfolgreich! Kann jetzt steuern
} else {
    // Fehlgeschlagen
    String message = result.message;
    String currentHolder = result.currentHolder;  // Wer hat die Steuerung?
}
```

### 2. Steuerung freigeben

```java
sessionContext.releaseCraneControl();
```

### 3. Vor jeder Kran-Aktion prüfen

```java
@Service
public class CraneCommandService {
    
    private final CraneAccessService accessService;
    
    public void sendCommand(CraneCommand command, String workstationId) {
        // WICHTIG: Erst prüfen!
        if (!accessService.hasControl(workstationId)) {
            throw new AccessDeniedException("Keine Kran-Steuerung aktiv");
        }
        
        // Befehl senden...
        plcService.send(command);
    }
}
```

---

## UI-Anzeige

Die `AccessControlBar` zeigt:

```
┌─────────────────────────────────────────────────────────────────┐
│ 🖥 KRAN-PC-01 | 🔓 STEUERUNG AKTIV | [Steuerung freigeben]     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 🖥 LAGER-PC-02 | ⏸ Steuerung möglich | Aktiv: KRAN-PC-01 |     │
│                                        [Steuerung anfordern]    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 🖥 BUERO-PC-03 | 👁 NUR ANSICHT | Steuerung: KRAN-PC-01        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Sicherheit

### Timeout

Wenn ein Arbeitsplatz **5 Minuten inaktiv** ist, wird die Steuerung automatisch freigegeben.

Der Heartbeat wird alle 30 Sekunden gesendet, solange die UI aktiv ist.

### Notfall-Freigabe (Admin)

```java
// Für Administratoren
accessService.forceReleaseControl("admin_user");
```

### Absicherung im Backend

**Jede** Aktion die den Kran betrifft MUSS geprüft werden:

```java
@RestController
@RequestMapping("/api/crane")
public class CraneController {
    
    @PostMapping("/command")
    public ResponseEntity<?> sendCommand(@RequestBody CraneCommand cmd,
                                         HttpServletRequest request) {
        String workstation = getWorkstationId(request);
        
        // Prüfung!
        if (!accessService.hasControl(workstation)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Keine Steuerungsberechtigung");
        }
        
        // Befehl ausführen...
    }
}
```

---

## Buttons deaktivieren im View-Modus

```java
public class LagerView extends VerticalLayout {
    
    @Autowired
    private PlsblSessionContext sessionContext;
    
    private void createButtons() {
        Button swapInButton = new Button("Einlagern");
        Button relocateButton = new Button("Umlagern");
        
        // Buttons deaktivieren wenn keine Steuerung
        boolean canAct = sessionContext.hasCraneControl();
        swapInButton.setEnabled(canAct);
        relocateButton.setEnabled(canAct);
        
        // Tooltip für deaktivierte Buttons
        if (!canAct) {
            swapInButton.setTooltipText("Kran-Steuerung erforderlich");
        }
    }
}
```

---

## Erweiterungsmöglichkeiten

### 1. Datenbank-gestützte Konfiguration

Statt `application.properties` können die Berechtigungen in einer Datenbank-Tabelle gespeichert werden:

```sql
CREATE TABLE WORKSTATION_CONFIG (
    ID NUMBER PRIMARY KEY,
    HOSTNAME VARCHAR(100) UNIQUE,
    ACCESS_MODE VARCHAR(20),
    DESCRIPTION VARCHAR(255),
    ENABLED NUMBER(1)
);

INSERT INTO WORKSTATION_CONFIG VALUES (1, 'KRAN-PC-01', 'CONTROL', 'Kran-Leitstand', 1);
INSERT INTO WORKSTATION_CONFIG VALUES (2, 'LAGER-PC-02', 'VIEW', 'Lager Büro', 1);
```

### 2. Benutzer-basierte Berechtigung

Kombination aus Arbeitsplatz UND Benutzer:

```java
// Nur wenn BEIDES stimmt
boolean canControl = 
    config.isAllowedToControlCrane(workstationId) &&
    userService.hasRole(userId, "CRANE_OPERATOR");
```

### 3. Zeitbasierte Einschränkung

```java
// Nur während Arbeitszeiten
LocalTime now = LocalTime.now();
boolean isWorkingHours = now.isAfter(LocalTime.of(6, 0)) 
                      && now.isBefore(LocalTime.of(22, 0));
```

---

## Checkliste für Produktion

- [ ] `default-mode=VIEW` setzen (nicht CONTROL!)
- [ ] Alle berechtigten Arbeitsplätze in `crane-controllers` eintragen
- [ ] `token-based-control=true` aktivieren
- [ ] Timeout angemessen setzen (z.B. 300 Sekunden)
- [ ] Alle Kran-Aktionen im Backend absichern
- [ ] Logging für Steuerungsübernahmen aktivieren
- [ ] Notfall-Freigabe testen
