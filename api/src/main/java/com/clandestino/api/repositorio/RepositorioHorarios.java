package com.clandestino.api.repositorio;

import com.clandestino.api.modelo.ExcepcionDia;
import com.clandestino.api.modelo.HorarioSemanal;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Acceso a las tablas horarios_semanales y excepciones_dia. */
public interface RepositorioHorarios {

    /** Los horarios de la semana que existan, ordenados de lunes a domingo. */
    List<HorarioSemanal> semanal(Connection conexion) throws SQLException;

    /** Inserta el horario de ese día de la semana o, si ya había uno, lo sustituye. */
    void guardarSemanal(Connection conexion, HorarioSemanal horario) throws SQLException;

    Optional<ExcepcionDia> excepcion(Connection conexion, LocalDate fecha) throws SQLException;

    /** Excepciones entre dos fechas (las dos incluidas). hasta puede ser null: sin límite. */
    List<ExcepcionDia> excepciones(Connection conexion, LocalDate desde, LocalDate hasta) throws SQLException;

    /** Inserta la excepción o sustituye la que ya hubiera para esa fecha. Devuelve el id de la fila. */
    int guardarExcepcion(Connection conexion, ExcepcionDia excepcion) throws SQLException;

    /** Devuelve false si no existía. */
    boolean borrarExcepcion(Connection conexion, int id) throws SQLException;
}
