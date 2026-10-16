"""
Diagrama 08 · Gantt del proyecto por etapas (Inicio, Diseño, Desarrollo, Cierre).

Las barras macizas son trabajo ya hecho, con las fechas del registro técnico
(docs/diario-tecnico.md). Las de trazo discontinuo son lo previsto: su final depende
de la fecha de entrega, así que se cambian aquí cuando se conozca y se regenera.
"""
from __future__ import annotations

from datetime import date, timedelta

from drawio_lib import BLANCO, BORDE, FUENTE, NEUTRO_1, NEUTRO_2, ROJO, TEXTO, Diagrama, estilo

INICIO = date(2026, 9, 28)      # lunes de la primera semana
FIN = date(2026, 11, 1)         # domingo de la última semana que se dibuja
DIA = 24                        # ancho de un día
ETIQUETA = 330                  # ancho de la columna de tareas
FILA = 30
CAB = 52                        # alto de la cabecera (semanas + días)

# (etapa, tarea, desde, hasta, hecho)
TAREAS = [
    ("Inicio", "Análisis de la app original y alcance", date(2026, 9, 30), date(2026, 9, 30), True),
    ("Inicio", "Entorno: MySQL portable y draw.io", date(2026, 10, 1), date(2026, 10, 1), True),
    ("Diseño", "Esquema SQL y sus 21 pruebas", date(2026, 10, 1), date(2026, 10, 1), True),
    ("Diseño", "Diagramas, mockups y casos de uso", date(2026, 10, 1), date(2026, 10, 8), True),
    ("Diseño", "Convenciones de código en español", date(2026, 10, 9), date(2026, 10, 9), True),
    ("Desarrollo", "API REST y sus 88 pruebas", date(2026, 10, 9), date(2026, 10, 14), True),
    ("Desarrollo", "App Android y prueba en el emulador", date(2026, 10, 14), date(2026, 10, 15), True),
    ("Desarrollo", "Diagramas al día con el código", date(2026, 10, 15), date(2026, 10, 15), True),
    ("Cierre", "Memoria y anexos", date(2026, 10, 16), date(2026, 10, 25), False),
    ("Cierre", "Presentación, ensayos y vídeo", date(2026, 10, 24), date(2026, 10, 31), False),
]


def x_dia(d: date) -> float:
    return ETIQUETA + (d - INICIO).days * DIA


def construir() -> Diagrama:
    dias = (FIN - INICIO).days + 1
    ancho = ETIQUETA + dias * DIA
    alto = CAB + len(TAREAS) * FILA + 44
    d = Diagrama("08-gantt", ancho + 20, alto + 20)

    texto = estilo("text", "html=1", "whiteSpace=wrap", f"fontFamily={FUENTE}", f"fontColor={TEXTO}",
                   "fontSize=13", "align=left", "verticalAlign=middle", "spacingLeft=8")

    # Cabecera: una banda por semana y la inicial de cada día debajo
    for s in range(dias // 7):
        lunes = INICIO + timedelta(days=7 * s)
        domingo = lunes + timedelta(days=6)
        d.celda(f"{lunes.day:02d}/{lunes.month:02d} – {domingo.day:02d}/{domingo.month:02d}",
                estilo("rounded=0", "html=1", f"fillColor={ROJO}", f"strokeColor={BLANCO}",
                       f"fontColor={BLANCO}", "fontStyle=1", "fontSize=13", f"fontFamily={FUENTE}"),
                x_dia(lunes), 0, 7 * DIA, 26)
    for i in range(dias):
        dia = INICIO + timedelta(days=i)
        fin_de_semana = dia.weekday() >= 5
        d.celda("LMXJVSD"[dia.weekday()],
                estilo("rounded=0", "html=1", f"fillColor={NEUTRO_2 if fin_de_semana else NEUTRO_1}",
                       f"strokeColor={BLANCO}", f"fontColor={TEXTO}", "fontSize=11", f"fontFamily={FUENTE}"),
                x_dia(dia), 26, DIA, 26)
    d.celda("<b>Etapa · tarea</b>", texto, 0, 26, ETIQUETA, 26)

    # Filas
    etapa_anterior = None
    for n, (etapa, tarea, desde, hasta, hecho) in enumerate(TAREAS):
        y = CAB + n * FILA
        if n % 2 == 0:
            d.celda("", estilo("rounded=0", f"fillColor={NEUTRO_1}", "strokeColor=none"), 0, y, ancho, FILA)
        # El nombre de la etapa solo en su primera fila, para que se lea como un grupo
        if etapa != etapa_anterior:
            d.celda(f"<b>{etapa}</b>", texto, 0, y, 96, FILA)
        d.celda(tarea, texto, 90, y, ETIQUETA - 90, FILA)
        etapa_anterior = etapa
        x0 = x_dia(desde)
        w = ((hasta - desde).days + 1) * DIA
        if hecho:
            barra = estilo("rounded=1", "arcSize=30", f"fillColor={ROJO}", f"strokeColor={ROJO}")
        else:
            barra = estilo("rounded=1", "arcSize=30", f"fillColor={BLANCO}", f"strokeColor={ROJO}",
                           "strokeWidth=2", "dashed=1", "dashPattern=6 4")
        d.celda("", barra, x0 + 2, y + 7, w - 4, FILA - 14)

    # Línea de "hoy" (día en que se dibuja) y leyenda
    hoy = date(2026, 10, 16)
    y_fin = CAB + len(TAREAS) * FILA
    d.arista(None, None, estilo("endArrow=none", "html=1", f"strokeColor={BORDE}", "strokeWidth=1.5",
                                "dashed=1"),
             origen_punto=(x_dia(hoy), CAB), destino_punto=(x_dia(hoy), y_fin + 6))
    y_ley = y_fin + 14
    d.celda("", estilo("rounded=1", "arcSize=30", f"fillColor={ROJO}", f"strokeColor={ROJO}"),
            8, y_ley + 6, 34, 14)
    d.celda("hecho", texto, 44, y_ley, 80, 26)
    d.celda("", estilo("rounded=1", "arcSize=30", f"fillColor={BLANCO}", f"strokeColor={ROJO}",
                       "strokeWidth=2", "dashed=1", "dashPattern=6 4"), 130, y_ley + 6, 34, 14)
    d.celda("previsto", texto, 166, y_ley, 90, 26)
    d.celda("- - -  16/10/2026, fecha de esta versión", texto, 262, y_ley, 330, 26)
    return d
