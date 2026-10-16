"""
Diagrama 02 · Entidad-relación (notación de patas de gallo) de database/schema.sql.

Cada columna, tipo y clave sale de schema.sql; si el esquema cambia, hay que
actualizar las listas TABLAS de abajo y volver a ejecutar generar.py.

Disposición pensada para que se lea a 15 cm de ancho en A4 (letra de las filas
de unos 8 pt): dos columnas estrechas y largas en lugar de dos columnas anchas.
  - Columna izquierda: invitaciones - usuarios - citas, encadenadas en vertical
    (así las etiquetas de las relaciones van en el hueco entre tablas y no hace
    falta un pasillo horizontal), y la nota de la columna generada bajo citas.
  - Columna derecha: las dos tablas sin clave ajena, las notas (CHECK y valores
    por defecto, valores de los ENUM) y la leyenda.
"""
from __future__ import annotations

import html as _html
import re

from drawio_lib import (BLANCO, BORDE, FUENTE, NEUTRO_1, NEUTRO_2, ROJO, TEXTO, Diagrama,
                        ancho_texto, estilo)

# --- Datos del esquema --------------------------------------------------------
# (clave, columna, tipo, nulo, generada)
# clave: "PK", "FK", "UK", "UK1" (parte de una clave única compuesta) o "".
TABLAS = {
    "usuarios": [
        ("PK", "id", "BIGINT UNSIGNED", False),
        ("UK", "usuario", "VARCHAR(20)", False),
        ("UK", "telefono", "VARCHAR(16)", False),
        ("", "password_hash", "VARCHAR(255)", False),
        ("", "rol", "ENUM", False),
        ("", "estado", "ENUM", False),
        ("", "precio_corte", "DECIMAL(6,2)", False),
        ("", "creado_en", "DATETIME", False),
        ("", "aprobado_en", "DATETIME", True),
    ],
    "invitaciones": [
        ("PK", "id", "BIGINT UNSIGNED", False),
        ("UK", "codigo", "VARCHAR(16)", False),
        ("", "nota", "VARCHAR(60)", True),
        ("", "estado", "ENUM", False),
        ("", "caduca_en", "DATETIME", False),
        ("FK", "creada_por", "BIGINT UNSIGNED", False),
        ("FK", "usada_por", "BIGINT UNSIGNED", True),
        ("", "creada_en", "DATETIME", False),
    ],
    "horarios_semanales": [
        ("PK", "id", "INT UNSIGNED", False),
        ("UK", "dia_semana", "TINYINT UNSIGNED", False),
        ("", "hora_inicio", "TIME", False),
        ("", "hora_fin", "TIME", False),
        ("", "cupo", "TINYINT UNSIGNED", False),
        ("", "activo", "BOOLEAN", False),
    ],
    "excepciones_dia": [
        ("PK", "id", "INT UNSIGNED", False),
        ("UK", "fecha", "DATE", False),
        ("", "tipo", "ENUM", False),
        ("", "hora_inicio", "TIME", True),
        ("", "hora_fin", "TIME", True),
        ("", "cupo", "TINYINT UNSIGNED", True),
        ("", "nota", "VARCHAR(80)", True),
    ],
    "citas": [
        ("PK", "id", "BIGINT UNSIGNED", False),
        ("FK", "cliente_id", "BIGINT UNSIGNED", False),
        ("UK1", "fecha", "DATE", False),
        ("", "hora_pedida", "TIME", False),
        ("", "hora_final", "TIME", True),
        ("", "estado", "ENUM", False),
        ("", "precio", "DECIMAL(6,2)", True),
        ("", "propina", "DECIMAL(6,2)", True),
        ("", "creada_en", "DATETIME", False),
        ("", "decidida_en", "DATETIME", True),
        ("UK1", "hora_ocupada", "TIME", True, "generada"),
    ],
}

# --- Geometría -----------------------------------------------------------------
# A 15 cm de ancho el lienzo (~700 unidades) se reduce a ~0,6 pt por unidad:
# filas de 14 px = 8,4 pt, notas y etiquetas de 13 px = 7,8 pt.
FS = 14              # tamaño de letra de las filas
FS_CAB = 16          # cabecera de cada tabla
FS_NOTA = 13         # notas, etiquetas de las relaciones y leyenda
LH = 15.6            # interlineado de las notas (px)
ALTO_FILA = 21
ALTO_CAB = 28
W_CLAVE, W_NOMBRE, W_TIPO = 36, 111, 183
TW = W_CLAVE + W_NOMBRE + W_TIPO          # ancho de cada tabla (330)
HUECO = 26                                # pasillo entre las dos columnas
PAD_G = 8                                 # margen interior del recuadro de «sin clave ajena»
XA = 0                                    # x de la columna izquierda
XB = TW + HUECO                           # x de la columna derecha (recuadros y notas)
WB = TW + 2 * PAD_G                       # ancho de los elementos de la columna derecha
GAP_INV = 82                              # hueco entre invitaciones y usuarios (dos relaciones)
GAP_CLI = 72                              # hueco entre usuarios y citas (una relación)
SEP = 14                                  # separación entre notas de una misma columna


def alto_tabla(nombre: str) -> int:
    return ALTO_CAB + ALTO_FILA * len(TABLAS[nombre])


def comprobar_anchos() -> None:
    """Falla si algún texto no cabe en su columna (evita texto cortado)."""
    for nombre, filas in TABLAS.items():
        for fila in filas:
            clave, col, tipo, nulo = fila[:4]
            generada = len(fila) > 4
            assert ancho_texto(clave, FS, True) + 8 <= W_CLAVE, (nombre, col, clave)
            negrita = generada or clave == "PK"
            assert ancho_texto(col, FS, negrita) + 11 <= W_NOMBRE, (nombre, col)
            t = tipo + (" NULL" if nulo else "") + (" (generada)" if generada else "")
            assert ancho_texto(t, FS) + 11 <= W_TIPO, (nombre, col, t)


# --- Estimación del alto de las notas -------------------------------------------
def n_lineas(html: str, ancho_util: float, fs: int = FS_NOTA) -> int:
    """Líneas que ocupará un texto HTML (separado con <br>) en un ancho dado."""
    espacio = ancho_texto(" ", fs)
    limite = ancho_util / 1.02          # un 2 % de margen por las diferencias de render
    total = 0
    for parrafo in html.split("<br>"):
        # palabras con su estilo: la negrita (<b>) ocupa algo más
        palabras, negrita = [], False
        for trozo in re.split(r"(<[^>]+>)", parrafo):
            if trozo == "<b>":
                negrita = True
            elif trozo == "</b>":
                negrita = False
            elif trozo.startswith("<"):
                continue
            else:
                for pal in _html.unescape(trozo).split(" "):
                    if pal:
                        palabras.append((pal, negrita))
        lineas, ancho = 1, 0.0
        for pal, neg in palabras:
            w = ancho_texto(pal, fs, neg)
            if ancho and ancho + espacio + w > limite:
                lineas += 1
                ancho = w
            else:
                ancho += (espacio if ancho else 0) + w
        total += lineas
    return total


def alto_nota(html: str, w: float) -> float:
    """Alto de una nota con spacing=5 (ver nota())."""
    return round(n_lineas(html, w - 12) * LH + 14)


# --- Elementos -----------------------------------------------------------------
def tabla_er(d: Diagrama, nombre: str, x: float, y: float, parent: str = "1") -> dict:
    """Dibuja una tabla con el formato nativo de draw.io (shape=table), editable fila a fila."""
    filas = TABLAS[nombre]
    h = alto_tabla(nombre)
    id_tabla = d.celda(
        nombre,
        estilo("shape=table", "container=1", "collapsible=0", "childLayout=tableLayout",
               "fixedRows=1", "rowLines=1", "columnLines=0", "html=1", "align=center",
               "resizeLast=1", f"startSize={ALTO_CAB}", "fontStyle=1", f"fontSize={FS_CAB}",
               f"fontFamily={FUENTE}", f"fillColor={ROJO}", f"swimlaneFillColor={BLANCO}",
               f"fontColor={BLANCO}", f"strokeColor={BORDE}", "strokeWidth=1.5"),
        x, y, TW, h, parent=parent)
    ids_fila = {}
    for i, fila in enumerate(filas):
        clave, col, tipo, nulo = fila[:4]
        generada = len(fila) > 4
        fy = ALTO_CAB + i * ALTO_FILA
        relleno = NEUTRO_2 if generada else ("none" if clave != "PK" else NEUTRO_1)
        id_fila = d.celda(
            "",
            estilo("shape=tableRow", "horizontal=0", "startSize=0", "swimlaneHead=0",
                   "swimlaneBody=0", f"fillColor={relleno}", "collapsible=0", "dropTarget=0",
                   "points=[[0,0.5],[1,0.5]]", "portConstraint=eastwest", "top=0", "left=0",
                   "right=0", "bottom=1", f"strokeColor={NEUTRO_2 if not generada else BORDE}",
                   "strokeWidth=1"),
            0, fy, TW, ALTO_FILA, parent=id_tabla)
        ids_fila[col] = id_fila

        base = estilo("shape=partialRectangle", "connectable=0", "fillColor=none", "top=0",
                      "left=0", "bottom=0", "right=0", "overflow=hidden", "whiteSpace=nowrap",
                      "html=1", "align=left", "verticalAlign=middle", "spacingLeft=4",
                      f"fontSize={FS}", f"fontFamily={FUENTE}", f"fontColor={TEXTO}")
        # Columna de la clave (PK / FK / UK)
        d.celda(clave, base + "fontStyle=1;align=center;spacingLeft=0;",
                0, 0, W_CLAVE, ALTO_FILA, parent=id_fila)
        # Nombre de la columna: la PK en negrita y subrayada; la generada, en negrita cursiva
        if generada:
            est_nombre = base + "fontStyle=3;"
        elif clave == "PK":
            est_nombre = base + "fontStyle=5;"
        else:
            est_nombre = base
        d.celda(col, est_nombre, W_CLAVE, 0, W_NOMBRE, ALTO_FILA, parent=id_fila)
        # Tipo (con NULL en cursiva si admite nulos)
        valor_tipo = tipo
        if nulo:
            valor_tipo += " <i>NULL</i>"
        if generada:
            valor_tipo += " <i>(generada)</i>"
        d.celda(valor_tipo, base, W_CLAVE + W_NOMBRE, 0, W_TIPO, ALTO_FILA, parent=id_fila)
    return {"tabla": id_tabla, "filas": ids_fila, "x": x, "y": y, "w": TW, "h": h}


def nota(d: Diagrama, html: str, x, y, w, h=None, parent="1", discontinua=True) -> tuple[str, float]:
    """Recuadro de nota. Si no se da el alto, se estima a partir del texto."""
    h = alto_nota(html, w) if h is None else h
    id_nota = d.celda(
        html,
        estilo("rounded=0", "whiteSpace=wrap", "html=1", "align=left", "verticalAlign=middle",
               "spacing=5", f"fontSize={FS_NOTA}", f"fontFamily={FUENTE}",
               f"fontColor={TEXTO}", f"fillColor={NEUTRO_1}", f"strokeColor={BORDE}",
               "dashed=1" if discontinua else "", "dashPattern=4 3"),
        x, y, w, h, parent=parent)
    return id_nota, h


# --- Textos de las notas ---------------------------------------------------------
TXT_SIN_FK_TITULO = "<b>Sin clave ajena: se relacionan por lógica de negocio</b>"
TXT_SIN_FK = (
    "La API las combina por fecha y por día de la semana (1&nbsp;=&nbsp;lunes … "
    "7&nbsp;=&nbsp;domingo): la excepción manda sobre el horario y su cupo limita las "
    "citas activas del día.")

TXT_GENERADA = (
    "<b>hora_ocupada</b> es una columna generada (VIRTUAL): vale "
    "COALESCE(hora_final, hora_pedida) si la cita está <i>pendiente</i> o "
    "<i>confirmada</i> y NULL en el resto.<br>"
    "<b>UK1 = uq_citas_hueco_activo</b>: UNIQUE (fecha, hora_ocupada) impide dos citas "
    "activas a la misma hora; una cita cancelada libera el hueco.")

SANGRIA = "&nbsp;&nbsp;&nbsp;"
TXT_CHECK = (
    "<b>Restricciones CHECK</b><br>"
    "usuarios: precio_corte &gt; 0<br>"
    "horarios_semanales: dia_semana de 1 a 7;<br>"
    f"{SANGRIA}hora_inicio &lt; hora_fin<br>"
    "excepciones_dia: «cerrado» sin horas ni cupo;<br>"
    f"{SANGRIA}«abierto» con horas y hora_inicio &lt; hora_fin<br>"
    "citas: precio y propina &ge; 0 (o NULL)<br>"
    "<b>Valores por defecto</b><br>"
    "usuarios: rol = 'cliente', estado = 'pendiente',<br>"
    f"{SANGRIA}precio_corte = 15,00<br>"
    "horarios_semanales: cupo = 2, activo = TRUE<br>"
    "invitaciones y citas: estado = 'pendiente'")

TXT_ENUM = (
    "<b>Valores de los ENUM</b><br>"
    "usuarios.rol: admin, cliente<br>"
    "usuarios.estado: pendiente, aprobado, bloqueado<br>"
    "invitaciones.estado: pendiente, usada<br>"
    "excepciones_dia.tipo: cerrado, abierto<br>"
    "citas.estado: pendiente, confirmada, rechazada, cancelada, caducada, hecha, "
    "no_asistio")

TXT_LEYENDA = (
    "<b>PK</b> primaria · <b>FK</b> ajena · <b>UK</b> única<br>"
    "<b>UK1</b> = <b>uq_citas_hueco_activo</b>: clave única compuesta (fecha, hora_ocupada)<br>"
    "<i>NULL</i> admite nulos; sin ella, NOT NULL<br>"
    "<b><i>Negrita cursiva</i></b> sobre gris: columna generada")


def construir() -> Diagrama:
    comprobar_anchos()
    d = Diagrama("02-er", 760, 1000)

    # =========================================================================
    # Columna izquierda: invitaciones - usuarios - citas
    # =========================================================================
    y_invit = 0
    y_usuarios = y_invit + alto_tabla("invitaciones") + GAP_INV
    y_citas = y_usuarios + alto_tabla("usuarios") + GAP_CLI
    y_gen = y_citas + alto_tabla("citas") + 22

    # Recuadro de la columna derecha (se dibuja primero: queda al fondo)
    alto_cab_g = alto_nota(TXT_SIN_FK_TITULO + "<br>" + TXT_SIN_FK, WB)
    y_horarios = alto_cab_g + 6
    y_excep = y_horarios + alto_tabla("horarios_semanales") + 14
    h_grupo = y_excep + alto_tabla("excepciones_dia") + PAD_G + 2
    grupo = d.celda(
        "",
        estilo("rounded=1", "arcSize=2", "whiteSpace=wrap", "html=1", "container=1",
               "collapsible=0", "dashed=1", "dashPattern=6 4", "fillColor=none",
               f"strokeColor={BORDE}", "strokeWidth=1.5"),
        XB, 0, WB, h_grupo)
    d.celda(
        TXT_SIN_FK_TITULO + "<br>" + TXT_SIN_FK,
        estilo("text", "html=1", "whiteSpace=wrap", "align=left", "verticalAlign=top",
               "spacing=5", "spacingTop=0", f"fontSize={FS_NOTA}", f"fontFamily={FUENTE}",
               f"fontColor={TEXTO}"),
        2, 2, WB - 4, alto_cab_g, parent=grupo)

    invit = tabla_er(d, "invitaciones", XA, y_invit)
    usuarios = tabla_er(d, "usuarios", XA, y_usuarios)
    citas = tabla_er(d, "citas", XA, y_citas)

    # --- Las dos tablas sin clave ajena, dentro del recuadro
    tabla_er(d, "horarios_semanales", PAD_G, y_horarios, parent=grupo)
    tabla_er(d, "excepciones_dia", PAD_G, y_excep, parent=grupo)

    # --- Relaciones (patas de gallo), todas verticales
    base_rel = ["html=1", f"fontFamily={FUENTE}", f"fontSize={FS_NOTA}", f"fontColor={TEXTO}",
                f"strokeColor={TEXTO}", "strokeWidth=1.5", "endFill=0", "startFill=0",
                "startSize=14", "endSize=14", "rounded=0", "edgeStyle=orthogonalEdgeStyle",
                "align=left", "spacingLeft=10", f"labelBackgroundColor={BLANCO}"]
    x1, x2 = 62, 190

    # usuarios 1 — N invitaciones (creada_por) y usuarios 0..1 — N invitaciones (usada_por)
    d.arista(usuarios["tabla"], invit["tabla"],
             estilo(*base_rel, "startArrow=ERmandOne", "endArrow=ERzeroToMany",
                    f"exitX={x1 / TW:.4f}", "exitY=0", f"entryX={x1 / TW:.4f}", "entryY=1"),
             valor="creada_por<br>ON DELETE<br>RESTRICT")
    d.arista(usuarios["tabla"], invit["tabla"],
             estilo(*base_rel, "startArrow=ERzeroToOne", "endArrow=ERzeroToMany",
                    f"exitX={x2 / TW:.4f}", "exitY=0", f"entryX={x2 / TW:.4f}", "entryY=1"),
             valor="usada_por (opcional)<br>ON DELETE<br>SET NULL")

    # usuarios 1 — N citas (cliente_id)
    d.arista(usuarios["tabla"], citas["tabla"],
             estilo(*base_rel, "startArrow=ERmandOne", "endArrow=ERzeroToMany",
                    f"exitX={x1 / TW:.4f}", "exitY=1", f"entryX={x1 / TW:.4f}", "entryY=0"),
             valor="cliente_id<br>ON DELETE<br>CASCADE")

    # --- Nota de la columna generada, bajo citas
    n1, h_n1 = nota(d, TXT_GENERADA, XA, y_gen, TW)
    d.arista(n1, citas["tabla"],
             estilo("endArrow=none", "dashed=1", "dashPattern=4 3", f"strokeColor={BORDE}",
                    "strokeWidth=1.5", "exitX=0.5", "exitY=0", "entryX=0.5", "entryY=1",
                    "edgeStyle=none"))
    alto_col_a = y_gen + h_n1

    # =========================================================================
    # Columna derecha: tablas sin FK, notas y leyenda
    # =========================================================================
    y = h_grupo + SEP
    _, h = nota(d, TXT_CHECK, XB, y, WB)
    y += h + SEP
    _, h = nota(d, TXT_ENUM, XB, y, WB)
    y += h + SEP

    # --- Leyenda
    h_ley_texto = n_lineas(TXT_LEYENDA, WB - 14) * LH + 10
    h_ley = round(28 + 3 * 22 + 4 + h_ley_texto)
    y_ley = y
    d.celda("", estilo("rounded=0", "html=1", f"fillColor={BLANCO}", f"strokeColor={BORDE}",
                       "strokeWidth=1"), XB, y_ley, WB, h_ley)
    d.celda("<b>Leyenda</b>", estilo("text", "html=1", "align=left", "verticalAlign=middle",
                                     "spacingLeft=6", f"fontSize={FS_NOTA}",
                                     f"fontFamily={FUENTE}", f"fontColor={TEXTO}"),
            XB, y_ley + 4, 120, 22)
    simbolos = [("ERmandOne", "uno y solo uno"), ("ERzeroToOne", "cero o uno"),
                ("ERzeroToMany", "cero o muchos")]
    for i, (marca, texto) in enumerate(simbolos):
        yy = y_ley + 36 + i * 22
        d.arista(None, None,
                 estilo("html=1", "edgeStyle=none", f"endArrow={marca}", "endFill=0", "endSize=14",
                        f"strokeColor={TEXTO}", "strokeWidth=1.5"),
                 origen_punto=(XB + 16, yy), destino_punto=(XB + 76, yy))
        d.celda(texto, estilo("text", "html=1", "align=left", "verticalAlign=middle",
                              f"fontSize={FS_NOTA}", f"fontFamily={FUENTE}",
                              f"fontColor={TEXTO}"),
                XB + 92, yy - 11, 200, 22)
    y_txt = y_ley + 28 + 3 * 22
    d.celda(
        TXT_LEYENDA,
        estilo("text", "html=1", "whiteSpace=wrap", "align=left", "verticalAlign=top",
               "spacingLeft=6", f"fontSize={FS_NOTA}", f"fontFamily={FUENTE}",
               f"fontColor={TEXTO}"),
        XB, y_txt, WB, h_ley_texto)
    alto_col_b = y_ley + h_ley

    # La página de draw.io se ajusta al contenido (solo afecta a la vista del editor)
    d.ancho = int(XB + WB + 20)
    d.alto = int(max(alto_col_a, alto_col_b) + 20)
    return d
