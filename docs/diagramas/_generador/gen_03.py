# -*- coding: utf-8 -*-
"""03 · Diagrama de casos de uso (solo los 10 casos P1 del plan).

Actores segun docs/diseno/casos-de-uso.md §1:
  Visitante      -> UC01
  Cliente        -> UC03, UC04, UC05
  Administrador  -> UC06 a UC10
  Usuario registrado (abstracto): generalizacion de Cliente y Administrador, asociado una sola vez a UC02.
UC04 «include» UC03.

Legibilidad: lienzo de unos 800 unidades y letra de 15 px -> a 15 cm de ancho sale a ~8 pt.
"""
from uml_dio import *

NOMBRE = '03-casos-de-uso'
FS = 15            # letra base de casos, actores, relaciones y notas

ASO = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.8;endArrow=none;fontFamily=Arial;fontSize=%d;fontColor=%s;'
       % (BORDE, FS, TINTA))
INC = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.8;dashed=1;endArrow=open;endSize=10;endFill=0;'
       'fontFamily=Arial;fontSize=%d;fontColor=%s;labelBackgroundColor=#FFFFFF;' % (BORDE, FS, TINTA))
GEN = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.8;endArrow=block;endFill=0;endSize=14;'
       'fontFamily=Arial;fontSize=%d;fontColor=%s;' % (BORDE, FS, TINTA))


def caso(doc, uc, l1, l2, x, y, w=262, hh=88):
    texto = '<b>%s</b><br>%s' % (uc, h(l1))
    if l2:
        texto += '<br>%s' % h(l2)
    st = estilo(ellipse=None, whiteSpace='wrap', html=1, fillColor=BLANCO, strokeColor=BORDE, strokeWidth=1.5,
                fontColor=TINTA, fontSize=FS, fontFamily=FUENTE, verticalAlign='middle', align='center',
                spacingLeft=0, spacingRight=0)
    return doc.vertice(texto, x, y, w, hh, st)


def actor(doc, nombre, sub, x, y, abstracto=False, desplaza=0):
    """Actor UML. El nombre va debajo de la figura; el abstracto lo lleva encima y en cursiva."""
    extra = dict(verticalLabelPosition='top', verticalAlign='bottom') if abstracto else \
        dict(verticalLabelPosition='bottom', verticalAlign='top')
    st = estilo(shape='umlActor', html=1, outlineConnect=0, fillColor=BLANCO, strokeColor=TINTA, strokeWidth=2,
                fontColor=TINTA, fontSize=FS, fontFamily=FUENTE, spacingLeft=desplaza, **extra)
    nom = '<b><i>%s</i></b>' % h(nombre) if abstracto else '<b>%s</b>' % h(nombre)
    etiqueta = nom + ('<br><font style="font-size:%dpx">%s</font>' % (FS - 1, h(sub)) if sub else '')
    return doc.vertice(etiqueta, x, y, 40, 72, st)


def construir():
    doc = Doc(NOMBRE)
    ANCHO, ALTO = 262, 88
    PASO = 104                       # separacion vertical entre casos

    BX = 100                         # borde izquierdo del sistema
    X1 = BX + 14                     # columna izquierda (cliente)
    X2 = X1 + ANCHO + 28             # columna derecha (administrador)
    BW = 14 + ANCHO + 28 + ANCHO + 14
    BY = 150                         # borde superior del sistema
    Y02 = BY + 46                    # UC02
    R0 = Y02 + ALTO + 28             # primera fila de las dos columnas
    BH = (R0 + 4 * PASO + ALTO + 24) - BY

    # --- sistema: caja gris con pestana roja (sin cabecera de ancho completo para no cruzarla con UC02) ---
    doc.vertice('', BX, BY, BW, BH, estilo(rounded=0, html=1, fillColor=N1, strokeColor=BORDE, strokeWidth=2))
    doc.vertice('Clandestino', BX, BY, 160, 32,
                estilo(rounded=0, html=1, fillColor=ROJO, strokeColor=BORDE, strokeWidth=2, fontColor=BLANCO,
                       fontSize=18, fontStyle=1, fontFamily=FUENTE, align='left', spacingLeft=12))

    # --- casos de uso ----------------------------------------------------------------------------
    cx_sys = BX + BW / 2
    uc02 = caso(doc, 'UC02', 'Iniciar sesión', '', cx_sys - ANCHO / 2, Y02)
    # UC04 incluye UC03: se deja un hueco mayor entre ambos para que se vea la flecha «include»
    Y3 = R0 + 43
    uc03 = caso(doc, 'UC03', 'Consultar disponibilidad', '', X1, Y3)
    uc04 = caso(doc, 'UC04', 'Solicitar cita', '', X1, Y3 + ALTO + 36)
    uc05 = caso(doc, 'UC05', 'Gestionar mis citas', '(ver y cancelar)', X1, Y3 + ALTO + 36 + PASO)
    uc01 = caso(doc, 'UC01', 'Registrarse con código', '', X1, Y3 + ALTO + 36 + 2 * PASO)
    uc06 = caso(doc, 'UC06', 'Generar invitación', '', X2, R0)
    uc07 = caso(doc, 'UC07', 'Aprobar o bloquear', 'cliente', X2, R0 + PASO)
    uc08 = caso(doc, 'UC08', 'Gestionar solicitudes', '(confirmar o rechazar)', X2, R0 + 2 * PASO)
    uc09 = caso(doc, 'UC09', 'Gestionar la agenda', '(asistencia y cancelación)', X2, R0 + 3 * PASO)
    uc10 = caso(doc, 'UC10', 'Configurar horarios', 'y vacaciones', X2, R0 + 4 * PASO)

    # --- actores --------------------------------------------------------------------------------------
    UR_Y = 52                                                     # figura del actor abstracto
    ur = actor(doc, 'Usuario registrado', '(abstracto)', cx_sys - 20, UR_Y, abstracto=True)
    cli = actor(doc, 'Cliente', '', 25, uc04.cy - 36)
    vis = actor(doc, 'Visitante', '', 25, uc01.cy - 36)
    adm_cx = BX + BW + 76
    adm = actor(doc, 'Administrador', '(barbero)', adm_cx - 20, uc08.cy - 36, desplaza=44)

    # asociaciones del visitante y del cliente (hasta la punta izquierda de cada elipse)
    doc.arista(vis, uc01, ASO, salida=(1, 1 / 3), entrada=(0, 0.5))
    for uc in (uc03, uc04, uc05):
        doc.arista(cli, uc, ASO, salida=(1, 1 / 3), entrada=(0, 0.5))
    # asociaciones del administrador
    for uc in (uc06, uc07, uc08, uc09, uc10):
        doc.arista(adm, uc, ASO, salida=(0, 1 / 3), entrada=(1, 0.5))
    # UC02: una sola asociacion, con el actor abstracto
    doc.arista(ur, uc02, ASO, salida=(0.5, 1), entrada=(0.5, 0))

    # generalizacion: Cliente y Administrador son Usuario registrado (triangulo hueco en el actor abstracto)
    y_brazos = UR_Y + 72 / 3
    doc.arista(cli, ur, GEN, salida=(0.5, 0), entrada=(0, 1 / 3), puntos=[(cli.cx, y_brazos)])
    doc.arista(adm, ur, GEN, salida=(0.5, 0), entrada=(1, 1 / 3), puntos=[(adm.cx, y_brazos)])

    # UC04 incluye UC03 (para pedir cita hay que ver los huecos libres)
    e = doc.arista(uc04, uc03, INC, salida=(0.5, 0), entrada=(0.5, 1))
    doc.etiqueta(e, '«include»', x=0, dx=52, dy=0, fs=FS, fondo=False)

    # --- notas ------------------------------------------------------------------------------------------
    yn = BY + BH + 24
    nota(doc, '<b>Requisito común:</b> los casos UC03 a UC10 exigen haber iniciado sesión (UC02), '
              'que comparten Cliente y Administrador como <b>Usuario registrado</b>.',
         10, yn, 395, 84, fs=FS)
    nota(doc, 'El código de UC01 lo genera el administrador en UC06, y el cliente no puede usar UC03 a UC05 '
              'hasta que lo aprueba en UC07.',
         421, yn, 395, 84, fs=FS)
    return doc


if __name__ == '__main__':
    construir().guardar(NOMBRE + '.drawio')
