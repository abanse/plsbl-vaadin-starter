-- ===================================================================
-- Diagnose: Welche Barren existieren und auf welchen Lagerplatz-Typen?
-- ===================================================================

PROMPT;
PROMPT === 1. Alle Lagerplatz-Typen im System ===;
SELECT YARD_TYPE, COUNT(*) AS ANZAHL
FROM MD_STOCKYARD
GROUP BY YARD_TYPE
ORDER BY YARD_TYPE;

PROMPT;
PROMPT === 2. Barren pro Lagerplatz-Typ ===;
SELECT s.YARD_TYPE, COUNT(i.ID) AS BARREN_ANZAHL
FROM TD_INGOT i
JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID
WHERE i.STOCKYARD_ID IS NOT NULL
GROUP BY s.YARD_TYPE
ORDER BY s.YARD_TYPE;

PROMPT;
PROMPT === 3. Barren die NICHT extern sind (Kran-erreichbar) ===;
SELECT COUNT(*) AS KRAN_ERREICHBAR
FROM TD_INGOT i
JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID
WHERE i.STOCKYARD_ID IS NOT NULL
  AND (i.SCRAP IS NULL OR i.SCRAP = 0)
  AND i.RELEASED_SINCE IS NULL
  AND s.YARD_TYPE != 'E';

PROMPT;
PROMPT === 4. Erste 10 Barren mit Lagerplatz-Info ===;
SELECT i.INGOT_NO, s.YARD_NO, s.YARD_TYPE, i.WEIGHT, i.LENGTH,
       CASE WHEN i.SCRAP = 1 THEN 'Ja' ELSE 'Nein' END AS SCHROTT
FROM TD_INGOT i
LEFT JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID
WHERE i.STOCKYARD_ID IS NOT NULL
  AND ROWNUM <= 10
ORDER BY s.YARD_NO;

PROMPT;
PROMPT === 5. Interne Lagerplaetze (YARD_TYPE='I') mit Barren ===;
SELECT s.YARD_NO, s.YARD_TYPE, COUNT(i.ID) AS BARREN
FROM MD_STOCKYARD s
LEFT JOIN TD_INGOT i ON i.STOCKYARD_ID = s.ID
WHERE s.YARD_TYPE = 'I'
GROUP BY s.YARD_NO, s.YARD_TYPE
HAVING COUNT(i.ID) > 0
ORDER BY s.YARD_NO;

PROMPT;
PROMPT === 6. Alle unterschiedlichen YARD_TYPE Werte ===;
SELECT DISTINCT YARD_TYPE,
       CASE YARD_TYPE
           WHEN 'I' THEN 'Intern (Kran)'
           WHEN 'E' THEN 'Extern (Stapler)'
           WHEN 'S' THEN 'Saege'
           WHEN 'A' THEN 'Ausgang/SwapOut'
           WHEN 'N' THEN 'Eingang/SwapIn'
           WHEN 'L' THEN 'Verladung'
           ELSE 'Unbekannt'
       END AS BESCHREIBUNG
FROM MD_STOCKYARD;
