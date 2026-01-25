-- ===================================================================
-- SQL-Skript zur Aktualisierung der Lagerplatz-Verwendung (YARD_USAGE)
-- UND zur Korrektur der BOTTOM_CENTER Koordinaten
--
-- Führe dieses Skript auf der Oracle-Datenbank aus, um die
-- SHORT/LONG-Zuordnung korrekt zu setzen:
--   - Spalten 01-05: LONG ('L') für lange Barren (>6000mm)
--   - Spalten 06-13: SHORT ('S') für kurze Barren (<=6000mm)
--   - Spalten 14-17: AUTOMATIC ('A') als Fallback
-- ===================================================================

-- ===================================================================
-- SCHRITT 1: Aktuelle Koordinaten prüfen
-- ===================================================================
SELECT YARD_NO, YARD_USAGE, X_COORDINATE, Y_COORDINATE,
       BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z,
       LENGTH, WIDTH
FROM MD_STOCKYARD
WHERE YARD_TYPE = 'I'
ORDER BY Y_COORDINATE DESC, X_COORDINATE;

-- ===================================================================
-- SCHRITT 2: YARD_USAGE aktualisieren
-- ===================================================================

-- LONG Plätze (Spalten 01-05) - Für Barren länger als 6000mm
UPDATE MD_STOCKYARD
SET YARD_USAGE = 'L'
WHERE YARD_TYPE = 'I'
  AND X_COORDINATE BETWEEN 1 AND 5;

-- SHORT Plätze (Spalten 06-13) - Für Barren kürzer oder gleich 6000mm
UPDATE MD_STOCKYARD
SET YARD_USAGE = 'S'
WHERE YARD_TYPE = 'I'
  AND X_COORDINATE BETWEEN 6 AND 13;

-- AUTOMATIC Plätze (Spalten 14-17) - Fallback
UPDATE MD_STOCKYARD
SET YARD_USAGE = 'A'
WHERE YARD_TYPE = 'I'
  AND X_COORDINATE >= 14;

-- ===================================================================
-- SCHRITT 3: BOTTOM_CENTER Koordinaten korrigieren
--
-- Die BOTTOM_CENTER Koordinaten müssen die MITTE des Lagerplatzes sein!
-- Formel: BOTTOM_CENTER_X = X_COORDINATE * 3000 (wenn 3000mm Raster)
-- Formel: BOTTOM_CENTER_Y = Y_COORDINATE * 3000 (wenn 3000mm Raster)
--
-- WICHTIG: Passen Sie diese Formeln an Ihr tatsächliches Raster an!
-- ===================================================================

-- Beispiel: Wenn Ihr Raster 3000mm beträgt:
-- UPDATE MD_STOCKYARD
-- SET BOTTOM_CENTER_X = X_COORDINATE * 3000,
--     BOTTOM_CENTER_Y = Y_COORDINATE * 3000
-- WHERE YARD_TYPE = 'I';

-- ===================================================================
-- SCHRITT 4: Ergebnis prüfen
-- ===================================================================
SELECT YARD_NO, YARD_USAGE,
       CASE YARD_USAGE
         WHEN 'L' THEN 'Lang (>6000mm)'
         WHEN 'S' THEN 'Kurz (<=6000mm)'
         WHEN 'A' THEN 'Automatisch'
         WHEN 'R' THEN 'Reserviert/Schrott'
         ELSE 'Unbekannt'
       END AS VERWENDUNG,
       X_COORDINATE, Y_COORDINATE,
       BOTTOM_CENTER_X, BOTTOM_CENTER_Y
FROM MD_STOCKYARD
WHERE YARD_TYPE = 'I'
ORDER BY Y_COORDINATE DESC, X_COORDINATE;

COMMIT;
