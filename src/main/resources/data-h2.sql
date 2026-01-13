-- ===================================================================
-- Testdaten für Entwicklung - Realistisches Lager-Layout
-- ===================================================================

-- Anwendungseinstellungen (SPS deaktiviert für Entwicklung -> Simulator)
INSERT INTO MD_APPSETTING (SETTING_KEY, SETTING_VALUE, CATEGORY, DESCRIPTION) VALUES
('SPS_ENABLED', '0', 'SPS', 'SPS-Verbindung aktiviert (1=ja, 0=nein)'),
('SPS_URL', 's7://localhost:102', 'SPS', 'SPS-Verbindungs-URL'),
('SPS_TIMEOUT', '10', 'SPS', 'Timeout in Sekunden'),
('SPS_RETRY_COUNT', '3', 'SPS', 'Anzahl Wiederholungsversuche'),
-- Lager-Grenzen exakt berechnet für Testdaten:
-- X: Spalte 1 bei 3000mm, Spalte 17 bei 51000mm
-- Y: Reihe 2 bei 6000mm, Reihe 10 bei 30000mm (Formel erfordert minY=3000)
('LAGER_MIN_X', '3000', 'WAREHOUSE', 'Internes Lager min. X [mm]'),
('LAGER_MAX_X', '51000', 'WAREHOUSE', 'Internes Lager max. X [mm]'),
('LAGER_MIN_Y', '3000', 'WAREHOUSE', 'Internes Lager min. Y [mm]'),
('LAGER_MAX_Y', '30000', 'WAREHOUSE', 'Internes Lager max. Y [mm]');

-- Produkte
INSERT INTO MD_PRODUCT (ID, SERIAL, PRODUCT_NO, DESCRIPTION, MAX_PER_LOCATION) VALUES
(1, 1, '4047-001', 'Aluminium Barren Standard', 8),
(2, 1, '4047-002', 'Aluminium Barren Lang', 6),
(3, 1, '4047-003', 'Aluminium Barren Kurz', 10),
(4, 1, '5052-001', 'Magnesium-Legierung', 8),
(5, 1, '6061-001', 'Strukturlegierung', 8);

-- Kran-Status (nur 1 Eintrag)
INSERT INTO TD_CRANESTATUS (ID, SERIAL, X_POSITION, Y_POSITION, Z_POSITION, 
                             CRANE_MODE, GRIPPER_STATE, JOB_STATE, DAEMON_STATE, 
                             WORK_PHASE, INCIDENT) VALUES
(1, 1, 25000, 15000, 5000, 'AUTOMATIC', 'OPEN', 'IDLE', 'IDLE_OK', 'IDLE', 'OK');

-- ===================================================================
-- Stockyards: 17 Spalten x 10 Reihen
-- ===================================================================

-- Reihe 10 (oben)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(101, 1, '01/10', 1, 10, 'I', 'A', 3000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(102, 1, '02/10', 2, 10, 'I', 'A', 6000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(103, 1, '03/10', 3, 10, 'I', 'A', 9000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(104, 1, '04/10', 4, 10, 'I', 'A', 12000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(105, 1, '05/10', 5, 10, 'I', 'A', 15000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(106, 1, '06/10', 6, 10, 'I', 'S', 18000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(107, 1, '07/10', 7, 10, 'I', 'S', 21000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(108, 1, '08/10', 8, 10, 'I', 'S', 24000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(109, 1, '09/10', 9, 10, 'I', 'S', 27000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(110, 1, '10/10', 10, 10, 'I', 'S', 30000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(111, 1, '11/10', 11, 10, 'I', 'S', 33000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(112, 1, '12/10', 12, 10, 'I', 'S', 36000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(113, 1, '13/10', 13, 10, 'I', 'S', 39000, 30000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(114, 1, '14/10', 14, 10, 'I', 'A', 42000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(115, 1, '15/10', 15, 10, 'I', 'A', 45000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(116, 1, '16/10', 16, 10, 'E', 'A', 48000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(117, 1, '17/10', 17, 10, 'E', 'A', 51000, 30000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 9
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(201, 1, '01/09', 1, 9, 'I', 'A', 3000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(202, 1, '02/09', 2, 9, 'I', 'A', 6000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(203, 1, '03/09', 3, 9, 'I', 'A', 9000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(204, 1, '04/09', 4, 9, 'I', 'A', 12000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(205, 1, '05/09', 5, 9, 'I', 'A', 15000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(206, 1, '06/09', 6, 9, 'I', 'S', 18000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(207, 1, '07/09', 7, 9, 'I', 'S', 21000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(208, 1, '08/09', 8, 9, 'I', 'S', 24000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(209, 1, '09/09', 9, 9, 'I', 'S', 27000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(210, 1, '10/09', 10, 9, 'I', 'S', 30000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(211, 1, '11/09', 11, 9, 'I', 'S', 33000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(212, 1, '12/09', 12, 9, 'I', 'S', 36000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(213, 1, '13/09', 13, 9, 'I', 'S', 39000, 27000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(214, 1, '14/09', 14, 9, 'I', 'A', 42000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(215, 1, '15/09', 15, 9, 'I', 'A', 45000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(216, 1, '16/09', 16, 9, 'E', 'A', 48000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(217, 1, '17/09', 17, 9, 'E', 'A', 51000, 27000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 8
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(301, 1, '01/08', 1, 8, 'I', 'A', 3000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(302, 1, '02/08', 2, 8, 'I', 'A', 6000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(303, 1, '03/08', 3, 8, 'I', 'A', 9000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(304, 1, '04/08', 4, 8, 'I', 'A', 12000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(305, 1, '05/08', 5, 8, 'I', 'A', 15000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(306, 1, '06/08', 6, 8, 'I', 'S', 18000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(307, 1, '07/08', 7, 8, 'I', 'S', 21000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(308, 1, '08/08', 8, 8, 'I', 'S', 24000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(309, 1, '09/08', 9, 8, 'I', 'S', 27000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(310, 1, '10/08', 10, 8, 'I', 'S', 30000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(311, 1, '11/08', 11, 8, 'I', 'S', 33000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(312, 1, '12/08', 12, 8, 'I', 'S', 36000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(313, 1, '13/08', 13, 8, 'I', 'S', 39000, 24000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(314, 1, '14/08', 14, 8, 'I', 'A', 42000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(315, 1, '15/08', 15, 8, 'I', 'A', 45000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(316, 1, '16/08', 16, 8, 'E', 'A', 48000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(317, 1, '17/08', 17, 8, 'E', 'A', 51000, 24000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 7 (mit gesperrten Plätzen)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(401, 1, '01/07', 1, 7, 'I', 'A', 3000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(402, 1, '02/07', 2, 7, 'I', 'A', 6000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(403, 1, '03/07', 3, 7, 'I', 'A', 9000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(404, 1, '04/07', 4, 7, 'I', 'A', 12000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(405, 1, '05/07', 5, 7, 'I', 'A', 15000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(406, 1, '06/07', 6, 7, 'I', 'S', 18000, 21000, 0, 1800, 1500, 0, 10, TRUE, FALSE),
(407, 1, '07/07', 7, 7, 'I', 'S', 21000, 21000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(408, 1, '08/07', 8, 7, 'I', 'S', 24000, 21000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(409, 1, '09/07', 9, 7, 'I', 'S', 27000, 21000, 0, 1800, 1500, 0, 10, FALSE, TRUE),
(410, 1, '10/07', 10, 7, 'I', 'S', 30000, 21000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(411, 1, '11/07', 11, 7, 'I', 'S', 33000, 21000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(412, 1, '12/07', 12, 7, 'I', 'S', 36000, 21000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(413, 1, '13/07', 13, 7, 'I', 'S', 39000, 21000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(414, 1, '14/07', 14, 7, 'I', 'A', 42000, 21000, 0, 2000, 1500, 0, 8, FALSE, FALSE),
(415, 1, '15/07', 15, 7, 'I', 'A', 45000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(416, 1, '16/07', 16, 7, 'E', 'A', 48000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(417, 1, '17/07', 17, 7, 'E', 'A', 51000, 21000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 6
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(501, 1, '01/06', 1, 6, 'I', 'A', 3000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(502, 1, '02/06', 2, 6, 'I', 'A', 6000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(503, 1, '03/06', 3, 6, 'I', 'A', 9000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(504, 1, '04/06', 4, 6, 'I', 'A', 12000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(505, 1, '05/06', 5, 6, 'I', 'A', 15000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(506, 1, '06/06', 6, 6, 'I', 'S', 18000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(507, 1, '07/06', 7, 6, 'I', 'S', 21000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(508, 1, '08/06', 8, 6, 'I', 'S', 24000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(509, 1, '09/06', 9, 6, 'I', 'S', 27000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(510, 1, '10/06', 10, 6, 'I', 'S', 30000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(511, 1, '11/06', 11, 6, 'I', 'S', 33000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(512, 1, '12/06', 12, 6, 'I', 'S', 36000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(513, 1, '13/06', 13, 6, 'I', 'S', 39000, 18000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(514, 1, '14/06', 14, 6, 'I', 'A', 42000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(515, 1, '15/06', 15, 6, 'I', 'A', 45000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(516, 1, '16/06', 16, 6, 'E', 'A', 48000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(517, 1, '17/06', 17, 6, 'E', 'A', 51000, 18000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 5 (mit Verladezone)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(601, 1, '01/05', 1, 5, 'I', 'A', 3000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(602, 1, '02/05', 2, 5, 'I', 'A', 6000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(603, 1, '03/05', 3, 5, 'I', 'A', 9000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(604, 1, '04/05', 4, 5, 'I', 'A', 12000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(605, 1, '05/05', 5, 5, 'I', 'A', 15000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(606, 1, '06/05', 6, 5, 'I', 'S', 18000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(607, 1, '07/05', 7, 5, 'I', 'S', 21000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(608, 1, '08/05', 8, 5, 'I', 'S', 24000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(609, 1, '09/05', 9, 5, 'I', 'S', 27000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(610, 1, '10/05', 10, 5, 'I', 'S', 30000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(611, 1, '11/05', 11, 5, 'I', 'S', 33000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(612, 1, '12/05', 12, 5, 'I', 'S', 36000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(613, 1, '13/05', 13, 5, 'I', 'S', 39000, 15000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(614, 1, '14/05', 14, 5, 'I', 'A', 42000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(615, 1, '15/05', 15, 5, 'I', 'A', 45000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(616, 1, '16/05', 16, 5, 'L', 'A', 48000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(617, 1, '17/05', 17, 5, 'L', 'A', 51000, 15000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 4 (vollständig mit allen Spalten)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(701, 1, '01/04', 1, 4, 'I', 'A', 3000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(702, 1, '02/04', 2, 4, 'I', 'A', 6000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(703, 1, '03/04', 3, 4, 'I', 'A', 9000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(704, 1, '04/04', 4, 4, 'I', 'A', 12000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(705, 1, '05/04', 5, 4, 'I', 'A', 15000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(706, 1, '06/04', 6, 4, 'I', 'S', 18000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(707, 1, '07/04', 7, 4, 'I', 'S', 21000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(708, 1, '08/04', 8, 4, 'I', 'S', 24000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(709, 1, '09/04', 9, 4, 'I', 'S', 27000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(710, 1, '10/04', 10, 4, 'I', 'S', 30000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(711, 1, '11/04', 11, 4, 'I', 'S', 33000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(712, 1, '12/04', 12, 4, 'I', 'S', 36000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(713, 1, '13/04', 13, 4, 'I', 'S', 39000, 12000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(714, 1, '14/04', 14, 4, 'I', 'A', 42000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(715, 1, '15/04', 15, 4, 'I', 'A', 45000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(716, 1, '16/04', 16, 4, 'L', 'A', 48000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(717, 1, '17/04', 17, 4, 'L', 'A', 51000, 12000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 3 (vollständig mit allen Spalten)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(801, 1, '01/03', 1, 3, 'I', 'A', 3000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(802, 1, '02/03', 2, 3, 'I', 'A', 6000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(803, 1, '03/03', 3, 3, 'I', 'A', 9000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(804, 1, '04/03', 4, 3, 'I', 'A', 12000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(805, 1, '05/03', 5, 3, 'I', 'A', 15000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(806, 1, '06/03', 6, 3, 'I', 'S', 18000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(807, 1, '07/03', 7, 3, 'I', 'S', 21000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(808, 1, '08/03', 8, 3, 'I', 'S', 24000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(809, 1, '09/03', 9, 3, 'I', 'S', 27000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(810, 1, '10/03', 10, 3, 'I', 'S', 30000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(811, 1, '11/03', 11, 3, 'I', 'S', 33000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(812, 1, '12/03', 12, 3, 'I', 'S', 36000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(813, 1, '13/03', 13, 3, 'I', 'S', 39000, 9000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(814, 1, '14/03', 14, 3, 'I', 'A', 42000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(815, 1, '15/03', 15, 3, 'I', 'A', 45000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(816, 1, '16/03', 16, 3, 'L', 'A', 48000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(817, 1, '17/03', 17, 3, 'L', 'A', 51000, 9000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 2 (vollständig mit allen Spalten)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(901, 1, '01/02', 1, 2, 'I', 'A', 3000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(902, 1, '02/02', 2, 2, 'I', 'A', 6000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(903, 1, '03/02', 3, 2, 'I', 'A', 9000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(904, 1, '04/02', 4, 2, 'I', 'A', 12000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(905, 1, '05/02', 5, 2, 'I', 'A', 15000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(906, 1, '06/02', 6, 2, 'I', 'S', 18000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(907, 1, '07/02', 7, 2, 'I', 'S', 21000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(908, 1, '08/02', 8, 2, 'I', 'S', 24000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(909, 1, '09/02', 9, 2, 'I', 'S', 27000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(910, 1, '10/02', 10, 2, 'I', 'S', 30000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(911, 1, '11/02', 11, 2, 'I', 'S', 33000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(912, 1, '12/02', 12, 2, 'I', 'S', 36000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(913, 1, '13/02', 13, 2, 'I', 'S', 39000, 6000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(914, 1, '14/02', 14, 2, 'I', 'A', 42000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(915, 1, '15/02', 15, 2, 'I', 'A', 45000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(916, 1, '16/02', 16, 2, 'L', 'A', 48000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE),
(917, 1, '17/02', 17, 2, 'L', 'A', 51000, 6000, 0, 2000, 1500, 0, 8, TRUE, TRUE);

-- Reihe 1 (6 kurze Plätze links von 11/01, in der unteren Reihe)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(1101, 1, '12/01', 12, 1, 'I', 'S', 36000, 3000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(1102, 1, '13/01', 13, 1, 'I', 'S', 39000, 3000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(1103, 1, '14/01', 14, 1, 'I', 'S', 42000, 3000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(1104, 1, '15/01', 15, 1, 'I', 'S', 45000, 3000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(1105, 1, '16/01', 16, 1, 'I', 'S', 48000, 3000, 0, 1800, 1500, 0, 10, TRUE, TRUE),
(1106, 1, '17/01', 17, 1, 'I', 'S', 51000, 3000, 0, 1800, 1500, 0, 10, TRUE, TRUE);

-- Säge-Position - oben rechts, an der Zaun-Öffnung
-- BOTTOM_CENTER_X: -2500 (Mitte zwischen -5000 und 0)
-- BOTTOM_CENTER_Y: 36000 (an der Zaun-Öffnung, oberhalb LAGER_MAX_Y=30000)
INSERT INTO MD_STOCKYARD (ID, SERIAL, YARD_NO, X_COORDINATE, Y_COORDINATE, YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED) VALUES
(1001, 1, '00/11', 0, 11, 'S', 'A', -2500, 36000, 0, 3000, 2000, 0, 1, FALSE, TRUE);

-- ===================================================================
-- Status-Daten für Stockyards (Beispiel-Belegung)
-- ===================================================================
INSERT INTO TD_STOCKYARDSTATUS (ID, SERIAL, STOCKYARD_ID, PRODUCT_ID, INGOTS_COUNT) VALUES
(1, 1, 101, 1, 8),
(2, 1, 102, 1, 8),
(3, 1, 103, 2, 6),
(4, 1, 104, 1, 5),
(5, 1, 105, 3, 3),
(6, 1, 106, 1, 7),
(7, 1, 201, 2, 4),
(8, 1, 202, 1, 2),
(9, 1, 203, 4, 6),
(10, 1, 301, 1, 1),
(11, 1, 302, 5, 3),
(12, 1, 303, 1, 5),
(13, 1, 401, 2, 2),
(14, 1, 402, 1, 4),
(15, 1, 501, 3, 7),
(16, 1, 502, 1, 3),
(17, 1, 601, 1, 6),
(18, 1, 602, 4, 2),
(19, 1, 616, 1, 4),
(20, 1, 617, 2, 3);

-- PlsStatus (Säge-Status) - nur 1 Eintrag
INSERT INTO TD_PLSSTATUS (ID, SERIAL, PICKUP_MODE, PICKUP_IN_PROGRESS, ROTATE) VALUES
(1, 1, 'NO_PICKUP', FALSE, FALSE);

-- ===================================================================
-- Transport-Aufträge (alle als COMPLETED damit neue Einlagerungen von der Säge sofort verarbeitet werden)
-- ===================================================================
INSERT INTO TD_TRANSPORTORDER (ID, SERIAL, TABLESERIAL, TRANSPORT_NO, NORMTEXT, FROM_YARD_ID, TO_YARD_ID, STATUS, PRIORITY) VALUES
(1, 1, 1, 'TA-2024-0001', 'Umlagerung 01/10 nach 01/09', 101, 201, 'C', 5),
(2, 1, 2, 'TA-2024-0002', 'Umlagerung 02/10 nach 02/09', 102, 202, 'C', 3),
(3, 1, 3, 'TA-2024-0003', 'Umlagerung 03/10 nach 03/09', 103, 203, 'C', 1),
(4, 1, 4, 'TA-2024-0004', 'Verladung 01/05 nach 16/05', 601, 616, 'C', 0),
(5, 1, 5, 'TA-2024-0005', 'Verladung 02/05 nach 17/05', 602, 617, 'F', 0);
