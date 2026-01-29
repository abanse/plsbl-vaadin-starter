-- ===================================================================
-- SQL Script: Kran-Koordinaten für Lagerplätze berechnen
-- Für Oracle Datenbank
-- ===================================================================

-- Die Koordinaten werden basierend auf dem Grid berechnet:
-- BOTTOM_CENTER_X = (X_COORDINATE + 1) * 3000  (Spalte 0 = 3000mm, Spalte 1 = 6000mm, etc.)
-- BOTTOM_CENTER_Y = (Y_COORDINATE + 1) * 3000  (Reihe 0 = 3000mm, Reihe 1 = 6000mm, etc.)
-- BOTTOM_CENTER_Z = 0 (Standard-Höhe)

-- Prüfen aktueller Status:
SELECT YARD_NO, X_COORDINATE, Y_COORDINATE,
       BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z
FROM MD_STOCKYARD
WHERE ROWNUM <= 10
ORDER BY YARD_NO;

-- Update: Kran-Koordinaten setzen basierend auf Grid-Koordinaten
UPDATE MD_STOCKYARD
SET BOTTOM_CENTER_X = (X_COORDINATE + 1) * 3000,
    BOTTOM_CENTER_Y = (Y_COORDINATE + 1) * 3000,
    BOTTOM_CENTER_Z = 0
WHERE BOTTOM_CENTER_X IS NULL OR BOTTOM_CENTER_X = 0;

-- Ergebnis prüfen:
SELECT YARD_NO, X_COORDINATE, Y_COORDINATE,
       BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z
FROM MD_STOCKYARD
WHERE ROWNUM <= 20
ORDER BY YARD_NO;

COMMIT;

-- ===================================================================
-- Alternative: Manuelle Zuordnung falls andere Koordinaten nötig
-- ===================================================================
-- Die tatsächlichen Koordinaten hängen von der physischen Hallen-Geometrie ab.
-- Falls die automatische Berechnung nicht passt, können Sie die Werte
-- manuell anpassen:
--
-- UPDATE MD_STOCKYARD SET BOTTOM_CENTER_X = 5000, BOTTOM_CENTER_Y = 12000
-- WHERE YARD_NO = '00/05';
--
-- Typische Werte (in mm):
-- - X: 3000 bis 51000 (Spalten 00 bis 17)
-- - Y: 3000 bis 30000 (Reihen 01 bis 10)
-- - Z: 0 bis 5000 (Höhe, meist 0)
