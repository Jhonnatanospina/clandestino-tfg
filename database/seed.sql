-- ============================================================================
-- Clandestino · datos iniciales
-- Se ejecuta después de schema.sql:   mysql -u root -p < database/seed.sql
--
-- Solo lleva el horario semanal de ejemplo. El usuario administrador no se crea
-- aquí porque su contraseña tiene que guardarse con hash y sal, y eso lo hace
-- el código de la API (herramienta "crear admin", fase 1).
-- ============================================================================

USE clandestino;

-- Lunes a jueves de 16:00 a 22:00 con un máximo de 2 citas al día, igual que la
-- agenda de ejemplo de los mockups. Los días sin fila están cerrados.
INSERT INTO horarios_semanales (dia_semana, hora_inicio, hora_fin, cupo, activo) VALUES
    (1, '16:00', '22:00', 2, TRUE),
    (2, '16:00', '22:00', 2, TRUE),
    (3, '16:00', '22:00', 2, TRUE),
    (4, '16:00', '22:00', 2, TRUE);
