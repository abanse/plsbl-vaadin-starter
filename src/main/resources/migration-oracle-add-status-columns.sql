-- ===================================================================
-- Migration: Status-Felder für automatische Auftragsverarbeitung
-- Ziel: TD_TRANSPORTORDER
-- ===================================================================

-- Neue Spalten hinzufügen
ALTER TABLE TD_TRANSPORTORDER ADD (
    STATUS          CHAR(1) DEFAULT 'P',           -- P=Pending, I=InProgress, U=PickedUp, C=Completed, F=Failed, X=Cancelled, H=Paused
    PRIORITY        NUMBER(10) DEFAULT 0,
    STARTED_AT      TIMESTAMP,
    COMPLETED_AT    TIMESTAMP,
    ERROR_MESSAGE   VARCHAR2(500),
    RETRY_COUNT     NUMBER(10) DEFAULT 0
);

-- Index für Status-Abfragen
CREATE INDEX IDX_TRANSPORTORDER_STATUS ON TD_TRANSPORTORDER(STATUS);

-- Bestehende Aufträge auf COMPLETED setzen (optional)
UPDATE TD_TRANSPORTORDER SET STATUS = 'C' WHERE STATUS IS NULL;

COMMIT;

-- ===================================================================
-- Hinweis: Dieses Script muss manuell auf der Oracle-Datenbank
-- ausgeführt werden, z.B. mit SQL Developer oder sqlplus:
--
-- sqlplus plsbl/hydroplsbl@//192.168.178.113:1521/ORCLCDB @migration-oracle-add-status-columns.sql
-- ===================================================================
