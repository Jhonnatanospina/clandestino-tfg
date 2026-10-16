-- ============================================================================
-- Clandestino · esquema de la base de datos
-- Motor: MySQL 9.7 LTS (InnoDB, utf8mb4). Se ejecuta con:
--     mysql -u root -p < database/schema.sql
-- Es idempotente a medias: borra y recrea las tablas, así que sirve para
-- reiniciar la base de datos en desarrollo. No lo lances contra datos reales.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS clandestino
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE clandestino;

-- Las tablas se borran en orden inverso a sus dependencias.
DROP TABLE IF EXISTS citas;
DROP TABLE IF EXISTS invitaciones;
DROP TABLE IF EXISTS excepciones_dia;
DROP TABLE IF EXISTS horarios_semanales;
DROP TABLE IF EXISTS usuarios;


-- ----------------------------------------------------------------------------
-- usuarios: el barbero (rol admin) y sus clientes (rol cliente).
-- Un cliente nace 'pendiente' y el admin lo aprueba o lo bloquea.
-- ----------------------------------------------------------------------------
CREATE TABLE usuarios (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario       VARCHAR(20)     NOT NULL COMMENT 'Sin @ y en minúsculas; la API valida [a-z0-9_]{3,20}',
    telefono      VARCHAR(16)     NOT NULL COMMENT 'Formato internacional, p. ej. +34600111222',
    password_hash VARCHAR(255)    NOT NULL COMMENT 'pbkdf2-sha256$iteraciones$sal$hash; nunca la contraseña',
    rol           ENUM('admin','cliente')                  NOT NULL DEFAULT 'cliente',
    estado        ENUM('pendiente','aprobado','bloqueado') NOT NULL DEFAULT 'pendiente',
    precio_corte  DECIMAL(6,2)    NOT NULL DEFAULT 15.00 COMMENT 'Precio que se copia a la cita al marcar la asistencia',
    creado_en     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aprobado_en   DATETIME        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuarios_usuario  (usuario),
    UNIQUE KEY uq_usuarios_telefono (telefono),
    CONSTRAINT ck_usuarios_precio CHECK (precio_corte > 0)
) ENGINE=InnoDB;


-- ----------------------------------------------------------------------------
-- invitaciones: códigos de un solo uso que genera el admin para dar de alta
-- a un cliente. La caducidad se calcula con caduca_en (no hay estado 'caducada').
-- usada_por usa SET NULL: si se borra al cliente, la invitación se conserva.
-- ----------------------------------------------------------------------------
CREATE TABLE invitaciones (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo      VARCHAR(16)     NOT NULL COMMENT 'Aleatorio (SecureRandom), mínimo 12 caracteres',
    nota        VARCHAR(60)     NULL     COMMENT 'A quién va dirigida, para que el admin se acuerde',
    estado      ENUM('pendiente','usada') NOT NULL DEFAULT 'pendiente',
    caduca_en   DATETIME        NOT NULL,
    creada_por  BIGINT UNSIGNED NOT NULL,
    usada_por   BIGINT UNSIGNED NULL,
    creada_en   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_invitaciones_codigo (codigo),
    KEY ix_invitaciones_creada_por (creada_por),
    KEY ix_invitaciones_usada_por  (usada_por),
    CONSTRAINT fk_invitaciones_creada_por FOREIGN KEY (creada_por) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invitaciones_usada_por  FOREIGN KEY (usada_por)  REFERENCES usuarios (id) ON DELETE SET NULL
) ENGINE=InnoDB;


-- ----------------------------------------------------------------------------
-- horarios_semanales: plantilla de disponibilidad, un tramo por día de la semana.
-- dia_semana sigue la convención ISO de java.time.DayOfWeek: 1 = lunes ... 7 = domingo.
-- cupo es el máximo de citas ACTIVAS (pendiente + confirmada) de ese día.
-- ----------------------------------------------------------------------------
CREATE TABLE horarios_semanales (
    id          INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    dia_semana  TINYINT UNSIGNED NOT NULL,
    hora_inicio TIME             NOT NULL,
    hora_fin    TIME             NOT NULL,
    cupo        TINYINT UNSIGNED NOT NULL DEFAULT 2,
    activo      BOOLEAN          NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_horarios_dia (dia_semana),
    CONSTRAINT ck_horarios_dia   CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_horarios_tramo CHECK (hora_inicio < hora_fin)
) ENGINE=InnoDB;


-- ----------------------------------------------------------------------------
-- excepciones_dia: una fecha concreta que manda sobre la plantilla semanal.
--   'cerrado' = vacaciones o día libre (sin horas ni cupo).
--   'abierto' = día extra, o un día normal con otro horario (horas obligatorias).
-- ----------------------------------------------------------------------------
CREATE TABLE excepciones_dia (
    id          INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    fecha       DATE             NOT NULL,
    tipo        ENUM('cerrado','abierto') NOT NULL,
    hora_inicio TIME             NULL,
    hora_fin    TIME             NULL,
    cupo        TINYINT UNSIGNED NULL     COMMENT 'Si es NULL en un día abierto, la API usa 2',
    nota        VARCHAR(80)      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_excepciones_fecha (fecha),
    CONSTRAINT ck_excepciones_coherencia CHECK (
        (tipo = 'cerrado' AND hora_inicio IS NULL AND hora_fin IS NULL AND cupo IS NULL)
        OR
        (tipo = 'abierto' AND hora_inicio IS NOT NULL AND hora_fin IS NOT NULL AND hora_inicio < hora_fin)
    )
) ENGINE=InnoDB;


-- ----------------------------------------------------------------------------
-- citas: la tabla central. Una cita dura una hora.
--
-- Estados que OCUPAN hueco y cupo: solo 'pendiente' y 'confirmada'.
-- Cancelar, rechazar o caducar la liberan al instante.
--
-- PROBLEMA: queremos que no haya dos citas activas a la misma hora del mismo día,
-- pero que sí pueda haber muchas canceladas a esa hora. Postgres (que usa el
-- Clandestino original) lo resuelve con un índice único PARCIAL; MySQL no los tiene.
-- SOLUCIÓN: una columna generada, hora_ocupada, que vale la hora efectiva mientras
-- la cita está activa y NULL en cuanto deja de estarlo. Como MySQL permite varios NULL
-- en un índice único, el UNIQUE (fecha, hora_ocupada) solo vigila las citas activas.
--
-- La hora efectiva es hora_final (la que fija el admin al confirmar) o, si todavía
-- no la hay, la hora que pidió el cliente.
-- ----------------------------------------------------------------------------
CREATE TABLE citas (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cliente_id   BIGINT UNSIGNED NOT NULL,
    fecha        DATE            NOT NULL,
    hora_pedida  TIME            NOT NULL,
    hora_final   TIME            NULL,
    estado       ENUM('pendiente','confirmada','rechazada','cancelada','caducada','hecha','no_asistio')
                                 NOT NULL DEFAULT 'pendiente',
    precio       DECIMAL(6,2)    NULL COMMENT 'Se rellena al marcar "hecha" con el precio_corte del cliente en ese momento',
    propina      DECIMAL(6,2)    NULL COMMENT 'Opcional; solo tiene sentido si la cita está "hecha"',
    creada_en    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decidida_en  DATETIME        NULL COMMENT 'Cuándo la confirmó, rechazó o caducó',
    hora_ocupada TIME GENERATED ALWAYS AS (
        IF(estado IN ('pendiente','confirmada'), COALESCE(hora_final, hora_pedida), NULL)
    ) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_citas_hueco_activo (fecha, hora_ocupada),
    KEY ix_citas_fecha_estado (fecha, estado),
    KEY ix_citas_cliente (cliente_id),
    CONSTRAINT fk_citas_cliente FOREIGN KEY (cliente_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_citas_precio  CHECK (precio  IS NULL OR precio  >= 0),
    CONSTRAINT ck_citas_propina CHECK (propina IS NULL OR propina >= 0)
) ENGINE=InnoDB;
