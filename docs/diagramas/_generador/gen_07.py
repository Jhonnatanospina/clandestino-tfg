# -*- coding: utf-8 -*-
"""07 · Diagrama de estados de la cita.

Fuente de las transiciones: docs/diseno/reglas-negocio.md §9 (T01 a T09).
  T02 pendiente -> confirmada   Administrador  [cita no pasada, hora final libre]
  T03 pendiente -> rechazada    Administrador  (sin guarda)
  T04 pendiente -> cancelada    Cliente propietario  [cita no pasada]
  T05/T06 confirmada -> cancelada  Cliente propietario o Administrador  [cita no pasada]
  T07/T08 confirmada -> hecha / no_asistio  Administrador  [cita ya pasada]
  T09 (P2) pendiente -> caducada  Sistema (tarea programada)

Legibilidad: lienzo de unos 800 unidades con letra de 16 px -> a 15 cm de ancho sale a ~8,5 pt.
"""
from uml_dio import *

NOMBRE = '07-estados-cita'
FL = 16                       # etiquetas de transicion
LH = FL * 1.2                 # alto de una linea de etiqueta
GRIS_P2 = '#5A5A5E'


def estado(doc, nombre, sub, x, y, w=130, hh=66, activo=False, p2=False):
    fill = ROJO if activo else N1
    col = BLANCO if activo else TINTA
    nom = '<b style="font-size:18px">%s</b>' % h(nombre)
    if p2:
        nom += ' <font style="font-size:15px">(P2)</font>'
    val = '%s<br><font style="font-size:15px">%s</font>' % (nom, h(sub))
    extra = dict(dashed=1, dashPattern='6 4') if p2 else {}
    st = estilo(rounded=1, arcSize=28, whiteSpace='wrap', html=1, fillColor=fill, strokeColor=BORDE,
                strokeWidth=2 if activo else 1.5, fontColor=col, fontSize=18, fontFamily=FUENTE,
                verticalAlign='middle', **extra)
    return doc.vertice(val, x, y, w, hh, st)


TR = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.8;endArrow=block;endFill=1;endSize=9;'
      'fontFamily=Arial;fontSize=%d;fontColor=%s;' % (BORDE, FL, TINTA))
TR_P2 = TR + 'dashed=1;dashPattern=7 5;'


def rotulo(doc, cx, cy, lineas, ancho=None):
    """Etiqueta de transicion centrada en (cx, cy), con fondo blanco para cortar la linea que pasa por detras."""
    texto = '<br>'.join(lineas)
    plano = [re_plano(l) for l in lineas]
    ancho = ancho or max(tw(p, FL, '<b>' in l) for p, l in zip(plano, lineas)) + 10
    alto = len(lineas) * LH + 6
    st = estilo(text=None, html=1, whiteSpace='nowrap', align='center', verticalAlign='middle', fontSize=FL,
                fontFamily=FUENTE, fontColor=TINTA, fillColor='#FFFFFF', strokeColor='none')
    return doc.vertice(texto, cx - ancho / 2, cy - alto / 2, ancho, alto, st)


def re_plano(t):
    import re
    import html as _h
    return _h.unescape(re.sub(r'<[^>]+>', '', t))


def trans(doc, o, d, sal, ent, via, estilo_arista=TR):
    return doc.arista(o, d, estilo_arista, puntos=via, salida=sal, entrada=ent)


def construir():
    doc = Doc(NOMBRE)
    YA = 120                  # fila de estados activos
    HA = 70
    ymid = 242                # tramo horizontal de las transiciones en codo
    YB = 440                  # fila de estados finales

    # ---- recuadros de agrupacion ---------------------------------------------------
    marco_a = estilo(rounded=1, arcSize=6, whiteSpace='wrap', html=1, dashed=1, dashPattern='8 4', fillColor='none',
                     strokeColor=ROJO, strokeWidth=2, fontColor=ROJO, fontSize=16, fontStyle=1, fontFamily=FUENTE,
                     verticalAlign='top', align='left', spacingLeft=12, spacingTop=4)
    doc.vertice('Ocupan hueco y cupo (estados activos)', 134, 18, 536, 200, marco_a)
    marco_b = estilo(rounded=1, arcSize=4, whiteSpace='wrap', html=1, dashed=1, dashPattern='8 4', fillColor='none',
                     strokeColor=BORDE, strokeWidth=2, fontColor=TINTA, fontSize=16, fontStyle=1, fontFamily=FUENTE,
                     verticalAlign='bottom', align='left', spacingLeft=12, spacingBottom=4)
    doc.vertice('Liberan el hueco y el cupo (estados finales)', 4, YB - 20, 790, 130, marco_b)

    # ---- estados ---------------------------------------------------------------------
    ini = doc.vertice('', 32, YA + HA / 2 - 11, 22, 22, estilo(ellipse=None, html=1, fillColor=TINTA, strokeColor=TINTA))
    pen = estado(doc, 'pendiente', 'ocupa hueco y cupo', 146, YA, w=150, hh=HA, activo=True)
    con = estado(doc, 'confirmada', 'ocupa hueco y cupo', 500, YA, w=150, hh=HA, activo=True)
    rec = estado(doc, 'rechazada', 'libera el hueco', 14, YB)
    cad = estado(doc, 'caducada', 'libera el hueco', 156, YB, p2=True)
    can = estado(doc, 'cancelada', 'libera el hueco', 306, YB, w=190)
    hec = estado(doc, 'hecha', 'libera el hueco', 510, YB)
    noa = estado(doc, 'no_asistio', 'libera el hueco', 654, YB)

    # ---- transiciones desde el inicio y entre estados activos ---------------------------------
    doc.arista(ini, pen, TR, salida=(1, 0.5), entrada=(0, 0.5))
    rotulo(doc, 96, YA + HA / 2 - 4 - 1.5 * LH - 3, ['<b>Cliente</b>', 'solicita', 'la cita'])

    doc.arista(pen, con, TR, salida=(1, 0.5), entrada=(0, 0.5))
    rotulo(doc, (pen.r + con.x) / 2, YA + HA / 2 - 4 - 2.5 * LH - 3,
           ['<b>Administrador</b>', 'confirma (fija', 'la hora final)', '[cita no pasada,', 'hora final libre]'])

    # ---- desde pendiente --------------------------------------------------------------------------
    x_rec, x_cad = rec.cx, cad.cx
    x_can_i = can.x + 0.17 * can.w
    x_can_d = can.x + 0.83 * can.w
    xp = lambda f: pen.x + f * pen.w
    xc = lambda f: con.x + f * con.w

    trans(doc, pen, rec, (0.2, 1), (0.5, 0), [(xp(0.2), ymid), (x_rec, ymid)])
    rotulo(doc, x_rec, 330, ['<b>Administrador</b>', 'rechaza'])

    trans(doc, pen, cad, (0.5, 1), (0.5, 0), [], estilo_arista=TR_P2)
    # el recorrido vertical de la rama discontinua es recto: pen.cx debe coincidir con cad.cx
    rotulo(doc, x_cad, 312, ['<b>Sistema</b>', 'caduca', '(P2)'])

    trans(doc, pen, can, (0.8, 1), (0.17, 0), [(xp(0.8), ymid), (x_can_i, ymid)])
    rotulo(doc, x_can_i, 330, ['<b>Cliente</b>', 'cancela', '[cita no pasada]'])

    # ---- desde confirmada -------------------------------------------------------------------------
    trans(doc, con, can, (0.2, 1), (0.83, 0), [(xc(0.2), ymid), (x_can_d, ymid)])
    rotulo(doc, x_can_d, 372, ['<b>Cliente o</b>', '<b>administrador</b>', 'cancela', '[cita no pasada]'])

    doc.arista(con, hec, TR, salida=(0.5, 1), entrada=(0.5, 0))
    rotulo(doc, hec.cx, 312, ['<b>Administrador</b>', 'marca «Asistió»', '[cita ya pasada]'])

    trans(doc, con, noa, (0.8, 1), (0.5, 0), [(xc(0.8), ymid), (noa.cx, ymid)])
    rotulo(doc, noa.cx, 340, ['<b>Administrador</b>', 'marca «No vino»', '[cita ya pasada]'])

    nota(doc, 'Solo <b>pendiente</b> y <b>confirmada</b> cuentan para el cupo diario y para el índice único '
              '(fecha, hora_ocupada) de la tabla citas; al salir de ellas, el hueco queda libre. '
              'El estado <b>caducada</b> y su transición, en trazo discontinuo, son P2: el P1 no los implementa.',
         4, YB + 125, 790, 78, fs=15)
    return doc


if __name__ == '__main__':
    construir().guardar(NOMBRE + '.drawio')
