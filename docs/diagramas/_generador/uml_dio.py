# -*- coding: utf-8 -*-
"""
Mini biblioteca para generar ficheros .drawio (XML mxfile sin comprimir) y
exportarlos a PNG con draw.io de escritorio.

Se usa desde los scripts gen_03.py ... gen_07.py. No depende de nada salvo
Pillow (solo para medir el ancho del texto con Arial y ajustar las cajas).
"""
import html
import math
import subprocess
from PIL import ImageFont

# ---- Paleta de la marca (solo estos colores) -------------------------------
ROJO = '#DD0426'      # cabeceras y elementos destacados
TINTA = '#121212'     # texto
N1 = '#F2F2F4'        # neutro claro
N2 = '#E9E9EC'        # neutro algo mas oscuro
BORDE = '#5A5A5E'     # bordes y lineas
BLANCO = '#FFFFFF'

FUENTE = 'Arial'
DRAWIO = r'C:\Users\jhony\AppData\Local\Programs\draw.io\draw.io.exe'

_fuentes = {}


def tw(texto, tam=13, negrita=False):
    """Ancho en px de un texto en Arial (la linea mas ancha si hay saltos)."""
    clave = (tam, negrita)
    f = _fuentes.get(clave)
    if f is None:
        f = ImageFont.truetype('C:/Windows/Fonts/' + ('arialbd.ttf' if negrita else 'arial.ttf'), tam)
        _fuentes[clave] = f
    return max(f.getlength(l) for l in texto.split('\n')) if texto else 0


def h(t):
    """Escapa texto plano para usarlo dentro de una etiqueta html."""
    return html.escape(t, quote=False)


def estilo(**kw):
    """Construye un estilo de draw.io. Una clave con valor None se emite sin '='."""
    partes = []
    for k, v in kw.items():
        partes.append(k if v is None else '%s=%s' % (k, v))
    return ';'.join(partes) + ';'


def redondea(v, paso=10):
    return int(math.ceil(v / paso) * paso)


class Caja:
    """Rectangulo con coordenadas absolutas, para calcular anclajes."""

    def __init__(self, id, x, y, w, h):
        self.id, self.x, self.y, self.w, self.h = id, x, y, w, h

    def pt(self, fx, fy):
        return (self.x + self.w * fx, self.y + self.h * fy)

    @property
    def cx(self):
        return self.x + self.w / 2

    @property
    def cy(self):
        return self.y + self.h / 2

    @property
    def b(self):
        return self.y + self.h

    @property
    def r(self):
        return self.x + self.w


def _n(v):
    if isinstance(v, float):
        v = round(v, 3)
        if v == int(v):
            v = int(v)
    return str(v)


def _a(s):
    return '"' + html.escape(s, quote=True) + '"'


class Doc:
    def __init__(self, nombre):
        self.nombre = nombre
        self.xml = []
        self._n = 1
        self.origen = {}   # id -> (x, y) absolutos de los contenedores

    def nid(self, p='c'):
        self._n += 1
        return '%s%d' % (p, self._n)

    # ------------------------------------------------------------ vertices
    def vertice(self, valor, x, y, w, hh, st, padre=None, id=None, rel=False):
        """x, y absolutos (o relativos al padre si rel=True)."""
        id = id or self.nid()
        ox, oy = self.origen.get(padre, (0, 0)) if padre else (0, 0)
        if rel:
            rx, ry = x, y
            ax, ay = ox + x, oy + y
        else:
            rx, ry = x - ox, y - oy
            ax, ay = x, y
        self.origen[id] = (ax, ay)
        self.xml.append(
            '<mxCell id="%s" value=%s style=%s vertex="1" parent="%s">'
            '<mxGeometry x="%s" y="%s" width="%s" height="%s" as="geometry"/></mxCell>'
            % (id, _a(valor), _a(st), padre or '1', _n(rx), _n(ry), _n(w), _n(hh)))
        return Caja(id, ax, ay, w, hh)

    # --------------------------------------------------------------- aristas
    def arista(self, origen, destino, st, valor='', puntos=None, sp=None, tp=None,
               salida=None, entrada=None, id=None):
        """origen/destino: Caja o id, o None si se dan sp/tp como puntos sueltos.
        puntos: lista de puntos intermedios absolutos (la linea es una poligonal)."""
        id = id or self.nid('e')
        s = st
        if salida is not None:
            s += 'exitX=%s;exitY=%s;exitDx=0;exitDy=0;' % (_n(salida[0]), _n(salida[1]))
        if entrada is not None:
            s += 'entryX=%s;entryY=%s;entryDx=0;entryDy=0;' % (_n(entrada[0]), _n(entrada[1]))
        a = ''
        if origen is not None:
            a += ' source="%s"' % (origen.id if isinstance(origen, Caja) else origen)
        if destino is not None:
            a += ' target="%s"' % (destino.id if isinstance(destino, Caja) else destino)
        g = '<mxGeometry relative="1" as="geometry">'
        if sp is not None:
            g += '<mxPoint x="%s" y="%s" as="sourcePoint"/>' % (_n(sp[0]), _n(sp[1]))
        if tp is not None:
            g += '<mxPoint x="%s" y="%s" as="targetPoint"/>' % (_n(tp[0]), _n(tp[1]))
        if puntos:
            g += '<Array as="points">' + ''.join(
                '<mxPoint x="%s" y="%s"/>' % (_n(px), _n(py)) for px, py in puntos) + '</Array>'
        g += '</mxGeometry>'
        self.xml.append('<mxCell id="%s" value=%s style=%s edge="1" parent="1"%s>%s</mxCell>'
                        % (id, _a(valor), _a(s), a, g))
        return id

    def etiqueta(self, arista_id, texto, x=0.0, dx=0, dy=0, fs=12, fondo=True, negrita=False):
        """Etiqueta hija de una arista. x en [-1, 1] a lo largo de la arista (-1 origen, 1 destino)."""
        id = self.nid('t')
        st = ('edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];'
              'fontFamily=Arial;fontSize=%d;fontColor=%s;' % (fs, TINTA))
        if fondo:
            st += 'labelBackgroundColor=#FFFFFF;'
        if negrita:
            st += 'fontStyle=1;'
        self.xml.append(
            '<mxCell id="%s" value=%s style=%s vertex="1" connectable="0" parent="%s">'
            '<mxGeometry x="%s" relative="1" as="geometry"><mxPoint x="%s" y="%s" as="offset"/></mxGeometry></mxCell>'
            % (id, _a(texto), _a(st), arista_id, _n(x), _n(dx), _n(dy)))
        return id

    # --------------------------------------------------------------- salida
    def a_xml(self, ancho=1200, alto=900):
        cuerpo = '\n        '.join(self.xml)
        return (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<mxfile host="app.diagrams.net" agent="generador Python (Clandestino TFG)" version="24.7.0" type="device">\n'
            '  <diagram id="%s" name="%s">\n'
            '    <mxGraphModel dx="%d" dy="%d" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" '
            'fold="1" page="0" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0" background="#FFFFFF">\n'
            '      <root>\n'
            '        <mxCell id="0"/>\n'
            '        <mxCell id="1" parent="0"/>\n'
            '        %s\n'
            '      </root>\n'
            '    </mxGraphModel>\n'
            '  </diagram>\n'
            '</mxfile>\n' % (self.nombre, html.escape(self.nombre), ancho, alto, cuerpo))

    def guardar(self, ruta, ancho=1200, alto=900):
        with open(ruta, 'w', encoding='utf-8', newline='\n') as f:
            f.write(self.a_xml(ancho, alto))


# ======================================================================
#   Utilidades de geometria para etiquetas de aristas poligonales
# ======================================================================
def x_en_poligonal(pts, k, frac):
    """Parametro x de una etiqueta de arista (-1..1) para el punto situado a la
    fraccion `frac` del segmento k (0 = primer segmento) de la poligonal pts."""
    largos = [math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]) for i in range(len(pts) - 1)]
    total = sum(largos) or 1
    d = sum(largos[:k]) + largos[k] * frac
    return round(2 * d / total - 1, 4)


def punto_en_poligonal(pts, k, frac):
    (x0, y0), (x1, y1) = pts[k], pts[k + 1]
    return (x0 + (x1 - x0) * frac, y0 + (y1 - y0) * frac)


# ======================================================================
#   Clases UML
# ======================================================================
def _calc_clase(nombre, attrs, metodos, tipo, estereotipo, fs, hfs, fila, abstracta, minw, subtitulo, w=None):
    est = estereotipo
    if tipo == 'interfaz' and not est:
        est = '«interface»'
    if tipo == 'enum' and not est:
        est = '«enumeration»'
    cab = []   # (html, texto, tamano, negrita)
    if est:
        cab.append(('<font style="font-size:12px">%s</font>' % h(est), est, 12, False))
    nombre_html = '<b><i>%s</i></b>' % h(nombre) if abstracta else '<b>%s</b>' % h(nombre)
    cab.append((nombre_html, nombre, hfs, True))
    if subtitulo:
        cab.append(('<font style="font-size:12px">%s</font>' % h(subtitulo), subtitulo, 12, False))
    hh = 28 if len(cab) == 1 else 10 + sum(17 if t[2] == 12 else 20 for t in cab)
    filas = list(attrs) + list(metodos)
    ancho = max([tw(t[1], t[2], t[3]) + 24 for t in cab] + [tw(r, fs) + 20 for r in filas] + [minw])
    w = w or redondea(ancho)
    sep = 8 if (attrs and metodos) else 0
    alto = hh + fila * len(filas) + sep + (4 if filas else 0)
    return cab, hh, w, alto, sep


def tam_clase(nombre, attrs=(), metodos=(), tipo='clase', estereotipo=None, fs=13, hfs=14, fila=20,
              abstracta=False, minw=0, subtitulo=None, w=None, **_):
    """(ancho, alto) que tendra la clase, para poder maquetar antes de dibujar."""
    _, _, w, alto, _ = _calc_clase(nombre, attrs, metodos, tipo, estereotipo, fs, hfs, fila, abstracta, minw,
                                   subtitulo, w)
    return w, alto


def clase(doc, nombre, attrs=(), metodos=(), x=0, y=0, w=None, tipo='clase', estereotipo=None,
          padre=None, fs=13, hfs=14, fila=20, abstracta=False, minw=0, cabecera=None, subtitulo=None):
    """Dibuja una clase/interfaz/enum UML como swimlane con una fila por miembro. Devuelve Caja."""
    color = cabecera or {'clase': ROJO, 'interfaz': TINTA, 'enum': BORDE}[tipo]
    cab, hh, w, alto, sep = _calc_clase(nombre, attrs, metodos, tipo, estereotipo, fs, hfs, fila, abstracta, minw,
                                        subtitulo, w)
    st = estilo(swimlane=None, fontStyle=0, align='center', verticalAlign='middle', childLayout='stackLayout',
                horizontal=1, startSize=hh, horizontalStack=0, resizeParent=1, resizeParentMax=0, resizeLast=0,
                collapsible=0, marginBottom=0, html=1, whiteSpace='wrap', fillColor=color, strokeColor=BORDE,
                strokeWidth=1.5, swimlaneFillColor=N1, fontColor=BLANCO, fontSize=hfs, fontFamily=FUENTE)
    c = doc.vertice('<br>'.join(t[0] for t in cab), x, y, w, alto, st, padre=padre)
    yy = hh
    txt = estilo(text=None, strokeColor='none', fillColor='none', align='left', verticalAlign='middle',
                 spacingLeft=7, spacingRight=4, overflow='hidden', rotatable=0, html=1, whiteSpace='nowrap',
                 fontSize=fs, fontFamily=FUENTE, fontColor=TINTA)
    for r in attrs:
        doc.vertice(h(r), 0, yy, w, fila, txt, padre=c.id, rel=True)
        yy += fila
    if sep:
        doc.vertice('', 0, yy, w, sep,
                    estilo(line=None, strokeWidth=1, fillColor='none', align='left', verticalAlign='middle',
                           spacingTop=-1, rotatable=0, labelPosition='right', points='[]',
                           portConstraint='eastwest', strokeColor=BORDE),
                    padre=c.id, rel=True)
        yy += sep
    for r in metodos:
        doc.vertice(h(r), 0, yy, w, fila, txt, padre=c.id, rel=True)
        yy += fila
    return c


def paquete(doc, nombre, x, y, w, hh, padre=None, fs=15):
    """Contenedor de paquete: cabecera gris claro con el nombre, cuerpo blanco."""
    st = estilo(swimlane=None, fontStyle=1, startSize=30, horizontal=1, collapsible=0, html=1, container=1,
                dropTarget=0, recursiveResize=0, fillColor=N2, swimlaneFillColor=BLANCO, strokeColor=BORDE,
                strokeWidth=1.5, fontColor=TINTA, fontSize=fs, fontFamily=FUENTE, align='left', spacingLeft=10)
    return doc.vertice(h(nombre), x, y, w, hh, st, padre=padre)


def nota(doc, texto_html, x, y, w, hh, fs=13, padre=None):
    """Nota UML (esquina doblada)."""
    st = estilo(shape='note', whiteSpace='wrap', html=1, size=14, fillColor=BLANCO, strokeColor=BORDE,
                strokeWidth=1.5, fontColor=TINTA, fontSize=fs, fontFamily=FUENTE, align='left',
                verticalAlign='middle', spacingLeft=8, spacingRight=8, spacingTop=2, spacingBottom=2)
    return doc.vertice(texto_html, x, y, w, hh, st, padre=padre)


# Estilos de arista UML
_BASE = ('html=1;rounded=0;strokeColor=%s;strokeWidth=1.5;fontFamily=Arial;fontSize=12;fontColor=%s;'
         'labelBackgroundColor=#FFFFFF;' % (BORDE, TINTA))
ASOC = _BASE + 'endArrow=open;endSize=10;endFill=0;'
DEP = _BASE + 'dashed=1;endArrow=open;endSize=10;endFill=0;'
REAL = _BASE + 'dashed=1;endArrow=block;endSize=12;endFill=0;'
HERE = _BASE + 'endArrow=block;endSize=12;endFill=0;'
COMP = _BASE + 'startArrow=diamondThin;startFill=1;startSize=16;endArrow=none;'
AGRE = _BASE + 'startArrow=diamondThin;startFill=0;startSize=16;endArrow=none;'
LINEA = _BASE + 'endArrow=none;'


def exporta(drawio, png):
    """Exporta con draw.io de escritorio (PNG a doble escala, borde de 10 px)."""
    r = subprocess.run([DRAWIO, '-x', '-f', 'png', '-s', '2', '-b', '10', '-o', png, drawio],
                       capture_output=True, text=True, timeout=180)
    if r.returncode != 0:
        raise RuntimeError('draw.io fallo: %s %s' % (r.stdout, r.stderr))
    return png
