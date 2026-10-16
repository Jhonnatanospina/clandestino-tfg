package com.clandestino.app.utilidades;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cómo se escriben las fechas, las horas y los teléfonos en las pantallas.
 *
 * Los nombres de días y meses van a mano en lugar de pedírselos a DateTimeFormatter con
 * Locale("es"): según la versión de Android, la abreviatura de martes sale "mar", "mar." o
 * "MAR.", y queremos que salga siempre igual que en los mockups.
 */
public final class Formatos {

    /** La barbería está en Madrid; la API también trabaja con esta zona. */
    public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private static final String[] DIAS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final String[] DIAS_CORTOS = {"LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM"};
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final String[] MESES_CORTOS = {"ENE", "FEB", "MAR", "ABR", "MAY", "JUN",
            "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"};

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Formatos() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA);
    }

    /** "16:00" */
    public static String hora(LocalTime hora) {
        return hora.format(HORA);
    }

    /** "MAR 2 FEB", como en las tarjetas de citas. */
    public static String diaCorto(LocalDate fecha) {
        return diaDeLaSemanaCorto(fecha) + " " + fecha.getDayOfMonth() + " "
                + MESES_CORTOS[fecha.getMonthValue() - 1];
    }

    /** "MAR", para la tira de días de la agenda. */
    public static String diaDeLaSemanaCorto(LocalDate fecha) {
        return DIAS_CORTOS[fecha.getDayOfWeek().getValue() - 1];
    }

    /** "Lunes 1 de febrero" */
    public static String diaLargo(LocalDate fecha) {
        return DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha.getDayOfMonth()
                + " de " + MESES[fecha.getMonthValue() - 1];
    }

    /** "2 de febrero", para meterlo en una frase: "La cita del 2 de febrero a las 16:00". */
    public static String diaYMes(LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1];
    }

    /**
     * "Del 2 al 6 de noviembre", o "Del 30 de octubre al 3 de noviembre" si cambia de mes.
     * Repetir el mes cuando es el mismo hacía que el texto no cupiera en una línea.
     */
    public static String rango(LocalDate desde, LocalDate hasta) {
        String inicio = desde.getMonth() == hasta.getMonth()
                ? String.valueOf(desde.getDayOfMonth())
                : diaYMes(desde);
        return "Del " + inicio + " al " + diaYMes(hasta);
    }

    /** "Lunes" a partir del día ISO de la API (1 = lunes ... 7 = domingo). */
    public static String nombreDia(int diaSemana) {
        return DIAS[diaSemana - 1];
    }

    /** "OCTUBRE 2026", o "OCTUBRE – NOVIEMBRE 2026" si el intervalo pasa de un mes a otro. */
    public static String meses(LocalDate desde, LocalDate hasta) {
        String primero = MESES[desde.getMonthValue() - 1].toUpperCase(Locale.ROOT);
        if (desde.getMonth() == hasta.getMonth()) {
            return primero + " " + desde.getYear();
        }
        String segundo = MESES[hasta.getMonthValue() - 1].toUpperCase(Locale.ROOT);
        if (desde.getYear() == hasta.getYear()) {
            return primero + " – " + segundo + " " + hasta.getYear();
        }
        return primero + " " + desde.getYear() + " – " + segundo + " " + hasta.getYear();
    }

    /** "5 – 11 OCT 2026" o "28 SEP – 4 OCT 2026" para la semana de la agenda. */
    public static String semana(LocalDate lunes) {
        LocalDate domingo = lunes.plusDays(6);
        String fin = domingo.getDayOfMonth() + " " + MESES_CORTOS[domingo.getMonthValue() - 1]
                + " " + domingo.getYear();
        if (lunes.getMonth() == domingo.getMonth()) {
            return lunes.getDayOfMonth() + " – " + fin;
        }
        return lunes.getDayOfMonth() + " " + MESES_CORTOS[lunes.getMonthValue() - 1] + " – " + fin;
    }

    /**
     * "+34 611 222 333". La API guarda el teléfono sin espacios; aquí solo se separa para
     * leerlo mejor. Si no es un número español de 9 cifras se deja tal cual.
     */
    public static String telefono(String telefono) {
        if (telefono == null || !telefono.matches("\\+34\\d{9}")) {
            return telefono;
        }
        return "+34 " + telefono.substring(3, 6) + " " + telefono.substring(6, 9) + " "
                + telefono.substring(9);
    }
}
