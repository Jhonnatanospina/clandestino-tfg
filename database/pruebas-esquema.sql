-- ============================================================================
-- Clandestino · pruebas del esquema (fase 1, primer día)
--
-- Comprueba que la base de datos protege por sí sola las reglas más delicadas,
-- antes de construir nada encima:
--   · no puede haber dos citas activas a la misma hora del mismo día,
--   · una cita cancelada, rechazada o caducada libera el hueco,
--   · las restricciones CHECK y las claves foráneas se comportan como se espera.
--
-- Se ejecuta contra una base de datos de pruebas, nunca contra la real:
--   scripts/probar-esquema.cmd
-- Cada prueba imprime su título y, si el resultado esperado es un error,
-- el servidor responde con "ERROR 1062 / 3819 / 1451 ..." (eso es lo correcto).
-- ============================================================================

USE clandestino_test;

SET @f = '2026-10-20';

INSERT INTO usuarios (usuario, telefono, password_hash, rol, estado) VALUES
    ('admin', '+34600000001', 'x', 'admin',   'aprobado'),
    ('marta', '+34600000002', 'x', 'cliente', 'aprobado'),
    ('luis',  '+34600000003', 'x', 'cliente', 'aprobado');
-- ids: admin = 1, marta = 2, luis = 3

SELECT 'P01 primera cita pendiente a las 11:00 -> DEBE ENTRAR' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida) VALUES (2, @f, '11:00');

SELECT 'P02 segunda cita activa a las 11:00 del mismo dia -> DEBE FALLAR (1062)' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida) VALUES (3, @f, '11:00');

SELECT 'P03 se cancela la primera y la segunda ya puede entrar -> DEBE ENTRAR' AS prueba;
UPDATE citas SET estado = 'cancelada' WHERE cliente_id = 2 AND fecha = @f AND hora_pedida = '11:00';
INSERT INTO citas (cliente_id, fecha, hora_pedida) VALUES (3, @f, '11:00');

SELECT 'P04 varias citas inactivas a la misma hora conviven (cancelada + rechazada + caducada) -> DEBEN ENTRAR' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida, estado) VALUES
    (2, @f, '12:00', 'cancelada'),
    (3, @f, '12:00', 'rechazada'),
    (2, @f, '12:00', 'caducada');

SELECT 'P05 confirmar con una hora final que ya esta ocupada -> DEBE FALLAR (1062)' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida) VALUES (2, @f, '13:00');
UPDATE citas SET estado = 'confirmada', hora_final = '11:00'
 WHERE cliente_id = 2 AND fecha = @f AND hora_pedida = '13:00';

SELECT 'P06 confirmar con una hora final libre -> DEBE ENTRAR' AS prueba;
UPDATE citas SET estado = 'confirmada', hora_final = '14:00'
 WHERE cliente_id = 2 AND fecha = @f AND hora_pedida = '13:00';

SELECT 'P07 la hora efectiva es hora_final: pedir ahora las 13:00 ya NO choca con la cita confirmada a las 14:00 -> DEBE ENTRAR' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida) VALUES (3, @f, '13:00');

SELECT 'P08 pero pedir las 14:00 si choca con la confirmada -> DEBE FALLAR (1062)' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida) VALUES (3, @f, '14:00');

SELECT 'Estado de las citas tras las pruebas (hora_ocupada = NULL significa que no ocupa hueco)' AS resumen;
SELECT id, cliente_id, hora_pedida, hora_final, estado, hora_ocupada FROM citas ORDER BY id;

SELECT 'P09 una cita con precio negativo -> DEBE FALLAR (3819, CHECK)' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida, precio) VALUES (2, '2026-10-21', '11:00', -5);

SELECT 'P10 excepcion "cerrado" con horas -> DEBE FALLAR (3819, CHECK de coherencia)' AS prueba;
INSERT INTO excepciones_dia (fecha, tipo, hora_inicio, hora_fin) VALUES ('2026-10-22', 'cerrado', '10:00', '12:00');

SELECT 'P11 excepcion "abierto" sin horas -> DEBE FALLAR (3819)' AS prueba;
INSERT INTO excepciones_dia (fecha, tipo) VALUES ('2026-10-23', 'abierto');

SELECT 'P12 excepciones correctas: un cerrado y un abierto -> DEBEN ENTRAR' AS prueba;
INSERT INTO excepciones_dia (fecha, tipo, nota) VALUES ('2026-10-24', 'cerrado', 'Vacaciones');
INSERT INTO excepciones_dia (fecha, tipo, hora_inicio, hora_fin, cupo) VALUES ('2026-10-25', 'abierto', '10:00', '14:00', 3);

SELECT 'P13 horario con inicio posterior al fin -> DEBE FALLAR (3819)' AS prueba;
INSERT INTO horarios_semanales (dia_semana, hora_inicio, hora_fin) VALUES (1, '18:00', '11:00');

SELECT 'P14 dia de la semana fuera de 1-7 -> DEBE FALLAR (3819)' AS prueba;
INSERT INTO horarios_semanales (dia_semana, hora_inicio, hora_fin) VALUES (8, '11:00', '18:00');

SELECT 'P15 dos horarios para el mismo dia -> DEBE FALLAR (1062)' AS prueba;
INSERT INTO horarios_semanales (dia_semana, hora_inicio, hora_fin) VALUES (2, '11:00', '18:00');
INSERT INTO horarios_semanales (dia_semana, hora_inicio, hora_fin) VALUES (2, '12:00', '19:00');

SELECT 'P16 usuario repetido -> DEBE FALLAR (1062)' AS prueba;
INSERT INTO usuarios (usuario, telefono, password_hash) VALUES ('marta', '+34600000099', 'x');

SELECT 'P17 estado de cita inexistente -> DEBE FALLAR (1265, ENUM)' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida, estado) VALUES (2, '2026-10-26', '11:00', 'volando');

SELECT 'P18 cita de un cliente que no existe -> DEBE FALLAR (1452, clave foranea)' AS prueba;
INSERT INTO citas (cliente_id, fecha, hora_pedida) VALUES (999, '2026-10-26', '11:00');

SELECT 'P19 invitacion usada por Luis' AS prueba;
INSERT INTO invitaciones (codigo, nota, estado, caduca_en, creada_por, usada_por)
VALUES ('K7M2QX9TBR4W', 'para Luis', 'usada', '2026-12-31 23:59:00', 1, 3);

SELECT 'P20 borrar a un admin que ha creado invitaciones -> DEBE FALLAR (1451, RESTRICT)' AS prueba;
DELETE FROM usuarios WHERE id = 1;

SELECT 'P21 borrar a Luis: se borran sus citas (CASCADE) y la invitacion se conserva con usada_por = NULL (SET NULL) -> DEBE ENTRAR' AS prueba;
DELETE FROM usuarios WHERE id = 3;
SELECT (SELECT COUNT(*) FROM citas WHERE cliente_id = 3) AS citas_de_luis_que_quedan,
       (SELECT usada_por FROM invitaciones WHERE codigo = 'K7M2QX9TBR4W') AS usada_por_ahora;
