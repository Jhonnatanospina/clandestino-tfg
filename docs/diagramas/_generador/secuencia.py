# -*- coding: utf-8 -*-
"""Ayudas para dibujar diagramas de secuencia UML con draw.io (lineas de vida, barras de activacion,
mensajes conectados a las barras, marcos alt/critical y notas)."""
from uml_dio import *

BARRA_W = 12
MSG = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.6;endArrow=block;endFill=1;endSize=8;fontFamily=Arial;'
       'fontSize=13;fontColor=%s;labelBackgroundColor=#FFFFFF;' % (BORDE, TINTA))
RET = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.6;dashed=1;endArrow=open;endFill=0;endSize=8;fontFamily=Arial;'
       'fontSize=13;fontColor=%s;labelBackgroundColor=#FFFFFF;' % (BORDE, TINTA))
ASY = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.6;endArrow=open;endFill=0;endSize=8;fontFamily=Arial;'
       'fontSize=13;fontColor=%s;labelBackgroundColor=#FFFFFF;' % (BORDE, TINTA))
LINEA_H = 16     # alto de una linea de texto de 13 px


class Secuencia:
    def __init__(self, doc, y_cab=20, alto_cab=48):
        self.doc = doc
        self.y_cab, self.alto_cab = y_cab, alto_cab
        self.partes = {}
        self.barras = {}      # clave -> [(y1, y2, id)]
        self.mensajes = []
        self.marcos = []
        self.textos = []
        self.avisos = []
        self.guardas = []

    # ------------------------------------------------------------------ declaracion
    def parte(self, clave, titulo, x, w, actor=False):
        self.partes[clave] = dict(titulo=titulo, x=x, w=w, actor=actor)
        self.barras[clave] = []

    def barra(self, clave, y1, y2):
        self.barras[clave].append([y1, y2, None])

    def msg(self, o, d, y, texto, tipo='call', lineas=None, dx=0, dy=None):
        self.mensajes.append(dict(k='msg', o=o, d=d, y=y, texto=texto, tipo=tipo, lineas=lineas, dx=dx, dy=dy))

    def auto(self, p, y1, texto, y2=None, ancho_lazo=34, tipo='call'):
        lin = texto.count('<br>') + 1
        y2 = y2 or (y1 + max(26, lin * LINEA_H + 6))
        self.mensajes.append(dict(k='auto', p=p, y1=y1, y2=y2, texto=texto, L=ancho_lazo, tipo=tipo, lineas=lin))
        return y2

    def marco(self, x1, y1, x2, y2, op, guarda='', ancho_tab=70, gx=None):
        self.marcos.append(dict(x1=x1, y1=y1, x2=x2, y2=y2, op=op, guarda=guarda, tab=ancho_tab, seps=[], gx=gx))
        return self.marcos[-1]

    def texto(self, x, y, w, hh, html_texto, fs=13, align='left', negrita=False, color=TINTA, rot=0):
        self.textos.append(dict(x=x, y=y, w=w, h=hh, t=html_texto, fs=fs, align=align, color=color, negrita=negrita,
                                rot=rot))

    def nota(self, x, y, w, hh, html_texto, fs=13):
        self.textos.append(dict(nota=True, x=x, y=y, w=w, h=hh, t=html_texto, fs=fs))

    # ------------------------------------------------------------------ dibujo
    def _barras_auto(self, hueco=70):
        ys = {k: [] for k in self.partes}
        for m in self.mensajes:
            if m['k'] == 'msg':
                ys[m['o']].append(m['y'])
                ys[m['d']].append(m['y'])
            else:
                ys[m['p']] += [m['y1'], m['y2']]
        for k, lista in ys.items():
            if self.barras[k] or not lista:
                continue
            lista.sort()
            ini = fin = lista[0]
            for v in lista[1:]:
                if v - fin <= hueco:
                    fin = v
                else:
                    self.barras[k].append([ini - 10, fin + 10, None])
                    ini = fin = v
            self.barras[k].append([ini - 10, fin + 10, None])

    def _barra_en(self, clave, y):
        for b in self.barras[clave]:
            if b[0] - 1 <= y <= b[1] + 1:
                return b
        raise ValueError('sin barra de %s en y=%s' % (clave, y))

    def dibuja(self, y_fin):
        doc = self.doc
        # 1) marcos (detras de todo)
        for m in self.marcos:
            st = estilo(shape='umlFrame', whiteSpace='wrap', html=1, pointerEvents=0, recursiveResize=0, container=0,
                        collapsible=0, width=m['tab'], height=26, fillColor='none', strokeColor=BORDE, strokeWidth=1.5,
                        fontColor=TINTA, fontSize=14, fontStyle=1, fontFamily=FUENTE)
            doc.vertice(m['op'], m['x1'], m['y1'], m['x2'] - m['x1'], m['y2'] - m['y1'], st)
            gx = m['gx'] if m['gx'] is not None else m['x1'] + m['tab'] + 10
            if m['guarda']:
                self.guardas.append((gx, m['y1'] + 2, m['guarda']))
            for ys, g in m['seps']:
                doc.arista(None, None, 'html=1;dashed=1;dashPattern=8 4;endArrow=none;strokeColor=%s;strokeWidth=1.5;' % BORDE,
                           sp=(m['x1'], ys), tp=(m['x2'], ys))
                if g:
                    gx2 = m['gx'] if m['gx'] is not None else m['x1'] + 10
                    self.guardas.append((gx2, ys + 2, g))
        # 2) lineas de vida
        for k, p in self.partes.items():
            x = p['x']
            if p['actor']:
                st = estilo(shape='umlActor', verticalLabelPosition='bottom', verticalAlign='top', html=1, outlineConnect=0,
                            fillColor=BLANCO, strokeColor=TINTA, strokeWidth=2, fontColor=TINTA, fontSize=14,
                            fontStyle=1, fontFamily=FUENTE)
                doc.vertice(p['titulo'], x - 15, self.y_cab - 4, 30, 54, st)
                doc.arista(None, None, 'html=1;dashed=1;dashPattern=8 6;endArrow=none;strokeColor=%s;strokeWidth=1.5;' % BORDE,
                           sp=(x, self.y_cab + 76), tp=(x, y_fin))
            else:
                st = estilo(shape='umlLifeline', perimeter='lifelinePerimeter', whiteSpace='wrap', html=1, container=1,
                            dropTarget=0, collapsible=0, recursiveResize=0, outlineConnect=0, portConstraint='eastwest',
                            size=self.alto_cab, fillColor=ROJO, strokeColor=BORDE, strokeWidth=1.5, fontColor=BLANCO,
                            fontSize=14, fontStyle=1, fontFamily=FUENTE)
                doc.vertice(p['titulo'], x - p['w'] / 2, self.y_cab, p['w'], y_fin - self.y_cab, st)
        # 3) barras de activacion (las de los participantes sin barras explicitas se deducen de los mensajes)
        self._barras_auto()
        for k, lista in self.barras.items():
            x = self.partes[k]['x']
            for b in lista:
                relleno = b[3] if len(b) > 3 else N2
                c = doc.vertice('', x - BARRA_W / 2, b[0], BARRA_W, b[1] - b[0],
                                estilo(html=1, points='[]', perimeter='orthogonalPerimeter', outlineConnect=0,
                                       fillColor=relleno, strokeColor=BORDE, strokeWidth=1.5))
                b[2] = c
        # 3b) textos de guarda de los marcos (encima de las lineas de vida, con fondo blanco)
        for gx, gy, g in self.guardas:
            self._txt(gx, gy, tw(re_texto(g), 13, True) + 10, 22, g, 13, negrita=True, fondo=True)
        # 4) mensajes
        for m in self.mensajes:
            if m['k'] == 'msg':
                self._dibuja_msg(m)
            else:
                self._dibuja_auto(m)
        # 5) textos y notas
        for t in self.textos:
            if t.get('nota'):
                nota(doc, t['t'], t['x'], t['y'], t['w'], t['h'], fs=t['fs'])
            else:
                self._txt(t['x'], t['y'], t['w'], t['h'], t['t'], t['fs'], t['align'], t['negrita'], t['color'],
                          t.get('rot', 0))

    def _txt(self, x, y, w, hh, t, fs=13, align='left', negrita=False, color=TINTA, rot=0, fondo=False):
        st = estilo(text=None, html=1, whiteSpace='wrap', align=align, verticalAlign='middle', fontSize=fs,
                    fontFamily=FUENTE, fontColor=color, fontStyle=1 if negrita else 0, spacingLeft=2, spacingRight=2)
        if rot:
            st += 'rotation=%d;' % rot
        if fondo:
            st += 'fillColor=#FFFFFF;'
        self.doc.vertice(t, x, y, w, hh, st)

    def _dibuja_msg(self, m):
        doc = self.doc
        bo = self._barra_en(m['o'], m['y'])
        bd = self._barra_en(m['d'], m['y'])
        xo, xd = self.partes[m['o']]['x'], self.partes[m['d']]['x']
        der = xd > xo
        fo = (m['y'] - bo[0]) / (bo[1] - bo[0])
        fd = (m['y'] - bd[0]) / (bd[1] - bd[0])
        st = {'call': MSG, 'ret': RET, 'async': ASY}[m['tipo']]
        e = doc.arista(bo[2], bd[2], st, salida=(1 if der else 0, fo), entrada=(0 if der else 1, fd))
        lin = m['lineas'] or (m['texto'].count('<br>') + 1)
        dy = m['dy'] if m['dy'] is not None else -(lin * LINEA_H / 2 + 5)
        doc.etiqueta(e, m['texto'], x=0, dx=m['dx'], dy=dy, fs=13)
        # aviso de desbordamiento horizontal
        ancho = max(tw(re_texto(l), 13) for l in m['texto'].split('<br>'))
        hueco = abs(xd - xo) - BARRA_W
        if ancho > hueco + 4:
            self.avisos.append('mensaje "%s" mide %d y el hueco es %d' % (re_texto(m['texto']), ancho, hueco))

    def _dibuja_auto(self, m):
        doc = self.doc
        b1 = self._barra_en(m['p'], m['y1'])
        b2 = self._barra_en(m['p'], m['y2'])
        x = self.partes[m['p']]['x'] + BARRA_W / 2
        f1 = (m['y1'] - b1[0]) / (b1[1] - b1[0])
        f2 = (m['y2'] - b2[0]) / (b2[1] - b2[0])
        st = {'call': MSG, 'ret': RET, 'async': ASY}[m['tipo']]
        pts = [(x + m['L'], m['y1']), (x + m['L'], m['y2'])]
        e = doc.arista(b1[2], b2[2], st, puntos=pts, salida=(1, f1), entrada=(1, f2))
        ancho = max(tw(re_texto(l), 13) for l in m['texto'].split('<br>'))
        doc.etiqueta(e, m['texto'], x=0, dx=ancho / 2 + 10, dy=0, fs=13, fondo=True)


def re_texto(t):
    """Quita etiquetas html y deshace entidades para medir el texto."""
    import re
    import html as _h
    return _h.unescape(re.sub(r'<[^>]+>', '', t))
