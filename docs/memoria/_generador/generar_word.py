# Monta la memoria en la plantilla oficial de Word de la línea 2 a partir de los
# capítulos en Markdown de docs/memoria.
#
# La plantilla se usa tal cual: portada, cabecera con el logo, pie con el número
# de página, márgenes y estilos. Lo único que se añade son los estilos de título
# 2 y 3, que la plantilla pide (Arial 14 y 12 en negrita) pero no trae definidos.
# Las tablas y las figuras siguen las normas APA 7: etiqueta en negrita encima,
# título en cursiva debajo y una nota al pie.
#
# Solo usa la biblioteca estándar de Python. Después hay que abrir el resultado
# en Word para que calcule el índice; de eso se encarga montar.ps1.
#
# Uso: python generar_word.py <plantilla.docx> <salida.docx>

import re
import struct
import sys
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

MEMORIA = Path(__file__).resolve().parent.parent

TITULO = 'Clandestino: aplicación Android de citas para barberos que trabajan desde casa'
LINEA = 'desarrollo de una aplicación'
ALUMNO = 'Jhonnatan Ospina Henao'
TUTOR = 'Alfonso García'
REPOSITORIO = 'https://github.com/Jhonnatanospina/clandestino-tfg'

CAPITULOS = [
    '01-resumen.md', '02-abstract.md', '03-introduccion.md',
    '04-descripcion-y-objetivos.md', '05-contenidos.md', '06-metodologia.md',
    '07-desarrollo.md', '08-conclusiones.md', '09-bibliografia.md',
]
ANEXOS = [
    'anexo-1-instalacion.md', 'anexo-2-usuario.md', 'anexo-3-diseno.md',
    'anexo-4-pruebas.md', 'anexo-5-diario.md',
]

# El resumen y el abstract van numerados como en el índice de la plantilla.
NUMERO_SIN_NUMERO = {'Resumen': '1.', 'Abstract': '2.'}

# Medidas en vigésimos de punto (twips). El ancho útil sale de la página A4
# menos los márgenes de 2,5 cm de la plantilla.
ANCHO_UTIL = 11906 - 2 * 1418
SANGRIA = 720  # 1,27 cm, la sangría que pide APA
EMU_POR_TWIP = 635
EMU_POR_CM = 360000

NUMERO_VINETAS = 2  # lista de viñetas que ya trae la plantilla

PATRON_PIE = re.compile(r'^\*(Figura|Tabla) ((?:[IVX]+\.)?\d+)\. (.*)\*$')
PATRON_FUENTE_GRUPO = re.compile(r'^\*Fuente de las figuras (.+?): elaboración propia\.\*$')
# Una imagen puede llevar como título su alto máximo en centímetros, por
# ejemplo ![Figura](ruta.png "alto 12"), para que no ocupe una hoja entera.
PATRON_IMAGEN = re.compile(r'^!\[(.*?)\]\((\S+?)(?: "alto (\d+(?:[.,]\d+)?)")?\)$')
PATRON_LISTA = re.compile(r'^(\s*)([-*]|\d+\.) (.*)$')


# ---------------------------------------------------------------- texto y runs

def run(texto, negrita=False, cursiva=False, codigo=False, resaltado=False,
        tamano=None, color=None):
    propiedades = ''
    if codigo:
        propiedades += '<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New" w:cs="Courier New"/>'
    if negrita:
        propiedades += '<w:b/><w:bCs/>'
    if cursiva:
        propiedades += '<w:i/><w:iCs/>'
    if color:
        propiedades += f'<w:color w:val="{color}"/>'
    if codigo and not tamano:
        tamano = 20
    if tamano:
        propiedades += f'<w:sz w:val="{tamano}"/><w:szCs w:val="{tamano}"/>'
    if resaltado:
        propiedades += '<w:highlight w:val="yellow"/>'
    rpr = f'<w:rPr>{propiedades}</w:rPr>' if propiedades else ''
    return f'<w:r>{rpr}<w:t xml:space="preserve">{escape(texto)}</w:t></w:r>'


def salto_linea():
    return '<w:r><w:br/></w:r>'


# Trocea una línea de Markdown en negritas, cursivas, código, saltos <br> y
# huecos [completar], que van resaltados en amarillo para que no se escapen.
MARCAS = r'\*\*.+?\*\*|`[^`]+`|\*[^*\s][^*]*?\*|<br>'
PATRON_EN_LINEA = re.compile(rf'(\[completar[^\]]*\]|{MARCAS})')
# Dentro de un hueco ya resaltado solo quedan las marcas normales.
PATRON_RESALTADO = re.compile(rf'({MARCAS})')


def en_linea(texto, negrita=False, cursiva=False, tamano=None, resaltado=False):
    trozos = []
    patron = PATRON_RESALTADO if resaltado else PATRON_EN_LINEA
    for trozo in patron.split(texto):
        if not trozo:
            continue
        if trozo == '<br>':
            trozos.append(salto_linea())
        elif trozo.startswith('[completar') and not resaltado:
            trozos.append(en_linea(trozo, negrita, cursiva, tamano, resaltado=True))
        elif trozo.startswith('**') and trozo.endswith('**') and len(trozo) > 4:
            trozos.append(en_linea(trozo[2:-2], True, cursiva, tamano, resaltado))
        elif trozo.startswith('`') and trozo.endswith('`'):
            trozos.append(run(trozo[1:-1], negrita, cursiva, codigo=True,
                              resaltado=resaltado, tamano=tamano and tamano - 2))
        elif trozo.startswith('*') and trozo.endswith('*') and len(trozo) > 2:
            trozos.append(en_linea(trozo[1:-1], negrita, True, tamano, resaltado))
        else:
            trozos.append(run(trozo, negrita, cursiva, resaltado=resaltado, tamano=tamano))
    return ''.join(trozos)


def parrafo(contenido, estilo=None, seguir=False, salto_antes=False, vineta=None,
            antes=None, despues=None, interlineado=None, izquierda=None,
            primera=None, colgante=None, alineacion=None, sombreado=None,
            tabulador=None):
    p = ''
    if estilo:
        p += f'<w:pStyle w:val="{estilo}"/>'
    if seguir:
        p += '<w:keepNext/>'
    if salto_antes:
        p += '<w:pageBreakBefore/>'
    if vineta is not None:
        p += f'<w:numPr><w:ilvl w:val="{vineta}"/><w:numId w:val="{NUMERO_VINETAS}"/></w:numPr>'
    if sombreado:
        p += f'<w:shd w:val="clear" w:color="auto" w:fill="{sombreado}"/>'
    if tabulador:
        p += f'<w:tabs><w:tab w:val="left" w:pos="{tabulador}"/></w:tabs>'
    espaciado = ''
    if antes is not None:
        espaciado += f' w:before="{antes}"'
    if despues is not None:
        espaciado += f' w:after="{despues}"'
    if interlineado is not None:
        espaciado += f' w:line="{interlineado}" w:lineRule="auto"'
    if espaciado:
        p += f'<w:spacing{espaciado}/>'
    sangria = ''
    if izquierda is not None:
        sangria += f' w:left="{izquierda}"'
    if primera is not None:
        sangria += f' w:firstLine="{primera}"'
    if colgante is not None:
        sangria += f' w:hanging="{colgante}"'
    if sangria:
        p += f'<w:ind{sangria}/>'
    if alineacion:
        p += f'<w:jc w:val="{alineacion}"/>'
    ppr = f'<w:pPr>{p}</w:pPr>' if p else ''
    return f'<w:p>{ppr}{contenido}</w:p>'


def texto_plano(texto):
    """Quita las marcas de Markdown para medir cuánto ocupa una celda."""
    return re.sub(r'[*`]|<br>', '', texto)


# ------------------------------------------------------------------- imágenes

class Imagenes:
    """Guarda las imágenes que se van usando y les da su relación en el docx."""

    def __init__(self):
        self.lista = []  # (nombre dentro del docx, ruta en disco, id de relación)
        self.siguiente_id = 100  # por encima de los ids de dibujo de la plantilla

    def dibujo(self, ruta_md, ancho_max_cm, alto_max_cm, descripcion=''):
        ruta = (MEMORIA / ruta_md).resolve()
        ancho_px, alto_px = tamano_png(ruta)
        # Las capturas y los diagramas están exportados a buena resolución, así
        # que se dibujan al ancho máximo salvo que se pasen de alto.
        ancho = ancho_max_cm * EMU_POR_CM
        alto = ancho * alto_px / ancho_px
        if alto > alto_max_cm * EMU_POR_CM:
            alto = alto_max_cm * EMU_POR_CM
            ancho = alto * ancho_px / alto_px
        ancho, alto = int(ancho), int(alto)

        indice = len(self.lista) + 1
        rid = f'rIdMemoria{indice}'
        self.lista.append((f'media/memoria{indice}.png', ruta, rid))
        id_dibujo = self.siguiente_id
        self.siguiente_id += 1
        descr = escape(descripcion or ruta.stem, {'"': '&quot;'})
        return (
            '<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">'
            f'<wp:extent cx="{ancho}" cy="{alto}"/><wp:effectExtent l="0" t="0" r="0" b="0"/>'
            f'<wp:docPr id="{id_dibujo}" name="Imagen {id_dibujo}" descr="{descr}"/>'
            '<wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/></wp:cNvGraphicFramePr>'
            '<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
            '<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            f'<pic:nvPicPr><pic:cNvPr id="{id_dibujo}" name="{escape(ruta.name)}"/><pic:cNvPicPr/></pic:nvPicPr>'
            f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
            f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{ancho}" cy="{alto}"/></a:xfrm>'
            '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>'
            '</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>'
        )


def tamano_png(ruta):
    with open(ruta, 'rb') as f:
        cabecera = f.read(24)
    if cabecera[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'{ruta} no es un PNG')
    return struct.unpack('>II', cabecera[16:24])


# --------------------------------------------------------- pies de figura APA

def partir_pie(resto):
    """Separa «Título. Más detalle. Fuente: ...» en título y nota."""
    fuente = ''
    m = re.match(r'(.*?)\.?\s*Fuente:\s*(.*)$', resto)
    if m:
        resto, fuente = m.group(1), m.group(2)
    titulo, _, detalle = resto.partition('. ')
    titulo = titulo.rstrip('.')
    nota = []
    if detalle:
        nota.append(detalle.rstrip('.') + '.')
    if fuente:
        nota.append(fuente[0].upper() + fuente[1:])
    return titulo, ' '.join(nota)


def etiqueta_apa(tipo, numero, titulo):
    """Etiqueta en negrita y título en cursiva, cada uno en su línea (APA 7)."""
    return (
        parrafo(run(f'{tipo} {numero}', negrita=True), seguir=True, antes=240,
                alineacion='left')
        + parrafo(en_linea(titulo, cursiva=True), seguir=True, despues=120,
                  alineacion='left')
    )


def nota_apa(texto):
    if not texto:
        return parrafo('', despues=120)
    return parrafo(run('Nota. ', cursiva=True, tamano=20) + en_linea(texto, tamano=20),
                   antes=80, despues=240, interlineado=240, alineacion='left')


# --------------------------------------------------------------------- tablas

BORDE = '<w:{lado} w:val="single" w:sz="{grosor}" w:space="0" w:color="000000"/>'


def anchos_columnas(filas, total=ANCHO_UTIL):
    columnas = len(filas[0])
    pesos, minimos = [], []
    for j in range(columnas):
        celdas = [texto_plano(f[j]) if j < len(f) else '' for f in filas]
        media = sum(min(len(c), 200) for c in celdas) / len(celdas)
        palabra = max((len(p) for c in celdas for p in c.split()), default=4)
        pesos.append(max(media, 6))
        minimos.append(max(min(palabra, 22) * 115 + 200, 900))
    if sum(minimos) > total:
        return [int(m * total / sum(minimos)) for m in minimos]
    libre = total - sum(minimos)
    anchos = [int(m + libre * p / sum(pesos)) for m, p in zip(minimos, pesos)]
    anchos[-1] += total - sum(anchos)
    return anchos


def celda(contenido, ancho, abajo=None, arriba=None, centrada=False):
    bordes = ''
    if arriba:
        bordes += BORDE.format(lado='top', grosor=arriba)
    if abajo:
        bordes += BORDE.format(lado='bottom', grosor=abajo)
    tcpr = f'<w:tcW w:w="{ancho}" w:type="dxa"/>'
    if bordes:
        tcpr += f'<w:tcBorders>{bordes}</w:tcBorders>'
    if centrada:
        tcpr += '<w:vAlign w:val="bottom"/>'
    return f'<w:tc><w:tcPr>{tcpr}</w:tcPr>{contenido}</w:tc>'


def tabla_xml(anchos, filas_xml, bordes_apa=True):
    bordes = ''
    if bordes_apa:
        # APA solo usa líneas horizontales: arriba, bajo la cabecera y al final.
        bordes = ('<w:tblBorders>' + BORDE.format(lado='top', grosor=8)
                  + BORDE.format(lado='bottom', grosor=8) + '</w:tblBorders>')
    rejilla = ''.join(f'<w:gridCol w:w="{a}"/>' for a in anchos)
    return (
        f'<w:tbl><w:tblPr><w:tblW w:w="{sum(anchos)}" w:type="dxa"/>{bordes}'
        '<w:tblLayout w:type="fixed"/>'
        '<w:tblCellMar><w:top w:w="40" w:type="dxa"/><w:left w:w="85" w:type="dxa"/>'
        '<w:bottom w:w="40" w:type="dxa"/><w:right w:w="85" w:type="dxa"/></w:tblCellMar>'
        '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" '
        'w:lastColumn="0" w:noHBand="0" w:noVBand="1"/></w:tblPr>'
        f'<w:tblGrid>{rejilla}</w:tblGrid>{"".join(filas_xml)}</w:tbl>'
    )


def tabla_datos(filas):
    """Tabla de texto con la primera fila como cabecera, a 10 puntos."""
    anchos = anchos_columnas(filas)
    # Dentro de las celdas no se usa «conservar con el siguiente»: junto con las
    # filas que no se parten deja a Word maquetando en bucle sin terminar nunca.
    filas_xml = []
    for i, fila in enumerate(filas):
        cabecera = i == 0
        celdas = []
        for j, ancho in enumerate(anchos):
            texto = fila[j] if j < len(fila) else ''
            contenido = parrafo(en_linea(texto, negrita=cabecera, tamano=20),
                                interlineado=240, alineacion='left')
            celdas.append(celda(contenido, ancho, abajo=4 if cabecera else None))
        propiedades = '<w:trPr><w:tblHeader/></w:trPr>' if cabecera else '<w:trPr><w:cantSplit/></w:trPr>'
        filas_xml.append(f'<w:tr>{propiedades}{"".join(celdas)}</w:tr>')
    return tabla_xml(anchos, filas_xml)


def es_fila_vacia(fila):
    return all(not c.strip() for c in fila)


def tabla_figuras(filas, imagenes):
    """Rejilla sin bordes con capturas o mockups lado a lado.

    Si la fila siguiente a las imágenes lleva los pies (Figura II.1...), cada pie
    se sube encima de su imagen, que es donde lo pone APA."""
    filas = [f for f in filas if not es_fila_vacia(f)]
    columnas = max(len(f) for f in filas)
    ancho = ANCHO_UTIL // columnas
    alto_max = 11 if columnas >= 3 else 13
    filas_xml = []
    i = 0
    while i < len(filas):
        fila = filas[i]
        pies = None
        if i + 1 < len(filas) and all(PATRON_PIE.match(c.strip()) for c in filas[i + 1] if c.strip()):
            pies = filas[i + 1]
        celdas = []
        for j in range(columnas):
            texto = fila[j].strip() if j < len(fila) else ''
            contenido = ''
            if pies and j < len(pies) and pies[j].strip():
                m = PATRON_PIE.match(pies[j].strip())
                contenido += (parrafo(run(f'{m.group(1)} {m.group(2)}', negrita=True, tamano=20),
                                      interlineado=240, alineacion='left')
                              + parrafo(en_linea(m.group(3).rstrip('.'), cursiva=True, tamano=20),
                                        despues=80, interlineado=240, alineacion='left'))
            imagen = PATRON_IMAGEN.match(texto)
            if imagen:
                dibujo = imagenes.dibujo(imagen.group(2), (ancho - 300) / 567, alto_max,
                                         descripcion=imagen.group(1))
                contenido += parrafo(dibujo, alineacion='center', interlineado=240)
            elif texto:
                contenido += parrafo(en_linea(texto, negrita=True, tamano=20),
                                     alineacion='center', interlineado=240)
            celdas.append(celda(contenido or parrafo(''), ancho, centrada=bool(imagen)))
        filas_xml.append(f'<w:tr><w:trPr><w:cantSplit/></w:trPr>{"".join(celdas)}</w:tr>')
        i += 2 if pies else 1
    return tabla_xml([ancho] * columnas, filas_xml, bordes_apa=False)


# ----------------------------------------------------------- lectura de bloques

def leer_bloques(texto):
    """Convierte el Markdown en una lista de bloques sencillos.

    Solo reconoce lo que usan los capítulos: títulos, párrafos, listas (con
    contenido sangrado dentro), bloques de código, tablas, imágenes y pies."""
    lineas = texto.splitlines()
    bloques = []
    i = 0
    while i < len(lineas):
        linea = lineas[i]
        sangria = len(linea) - len(linea.lstrip(' '))
        limpia = linea.strip()
        if not limpia:
            i += 1
            continue
        nivel = 0 if sangria < 2 else (1 if sangria < 6 else 2)
        if limpia.startswith('```'):
            codigo = []
            i += 1
            while i < len(lineas) and not lineas[i].strip().startswith('```'):
                codigo.append(lineas[i][sangria:] if lineas[i][:sangria].strip() == '' else lineas[i].strip())
                i += 1
            bloques.append(('codigo', nivel, codigo))
            i += 1
            continue
        m = re.match(r'^(#{1,4}) (.*)$', limpia)
        if m and sangria == 0:
            bloques.append(('titulo', len(m.group(1)), m.group(2).strip()))
            i += 1
            continue
        if limpia.startswith('|') and i + 1 < len(lineas) and re.match(r'^\s*\|[-| :]+\|\s*$', lineas[i + 1]):
            filas = [celdas_de(limpia)]
            i += 2
            while i < len(lineas) and lineas[i].strip().startswith('|'):
                filas.append(celdas_de(lineas[i].strip()))
                i += 1
            bloques.append(('tabla', nivel, filas))
            continue
        if PATRON_IMAGEN.match(limpia):
            bloques.append(('imagen', nivel, limpia))
            i += 1
            continue
        if PATRON_PIE.match(limpia) or PATRON_FUENTE_GRUPO.match(limpia):
            bloques.append(('pie', nivel, limpia))
            i += 1
            continue
        m = PATRON_LISTA.match(linea)
        if m:
            marca = m.group(2)
            bloques.append(('numero' if marca[0].isdigit() else 'vineta', nivel,
                            (marca, m.group(3).strip())))
            i += 1
            continue
        # Párrafo: se juntan las líneas seguidas hasta un hueco o un bloque nuevo.
        partes = [limpia]
        i += 1
        while i < len(lineas) and lineas[i].strip() and not empieza_bloque(lineas[i]):
            partes.append(lineas[i].strip())
            i += 1
        bloques.append(('parrafo', nivel, ' '.join(partes)))
    return bloques


def celdas_de(linea):
    return [c.strip() for c in linea.strip().strip('|').split('|')]


def empieza_bloque(linea):
    limpia = linea.strip()
    return (limpia.startswith(('```', '|', '#', '![')) or PATRON_LISTA.match(linea) is not None
            or PATRON_PIE.match(limpia) is not None)


# ------------------------------------------------------------- de bloques a XML

class Capitulo:
    def __init__(self, imagenes, modo='normal', en_anexos=False, sigue_en_la_hoja=False):
        self.imagenes = imagenes
        self.modo = modo  # normal, resumen o bibliografia
        self.en_anexos = en_anexos
        # El anexo I va justo debajo de la presentación de los anexos.
        self.sigue_en_la_hoja = sigue_en_la_hoja
        self.xml = []

    def convertir(self, bloques):
        i = 0
        while i < len(bloques):
            tipo, nivel, dato = bloques[i]
            siguiente = bloques[i + 1] if i + 1 < len(bloques) else None
            pie = siguiente if siguiente and siguiente[0] == 'pie' else None
            if tipo == 'titulo':
                self.titulo(nivel, dato)
            elif tipo == 'parrafo':
                self.texto(nivel, dato, bloques[i - 1][0] if i else None)
            elif tipo in ('vineta', 'numero'):
                ultimo = not siguiente or siguiente[0] != tipo or siguiente[1] < nivel
                self.lista(tipo, nivel, dato, ultimo)
            elif tipo == 'codigo':
                self.codigo(nivel, dato)
            elif tipo == 'imagen':
                self.figura(dato, pie)
                i += 1 if pie else 0
            elif tipo == 'tabla':
                self.tabla(dato, pie)
                i += 1 if pie else 0
            elif tipo == 'pie':
                self.pie_suelto(dato)
            i += 1

    def titulo(self, nivel, texto):
        if self.en_anexos:
            nivel += 1
        if nivel == 1:
            m = re.match(r'^(\d+\.)\s', texto)
            if not m and texto in NUMERO_SIN_NUMERO:
                texto = f'{NUMERO_SIN_NUMERO[texto]} {texto}'
        estilo = {1: 'Ttulo1', 2: 'Ttulo2', 3: 'Ttulo3'}.get(nivel, 'Ttulo3')
        # La guía pide hoja nueva para el resumen, el abstract y la introducción, y
        # la plantilla la pone también antes de la bibliografía y de los anexos.
        # Del capítulo 4 al 8 el texto sigue seguido, porque la memoria tiene que
        # caber en 15-20 páginas y cada salto dejaba media hoja en blanco.
        capitulo_seguido = re.match(r'^[4-8]\.\s', texto) is not None
        nueva_hoja = (nivel == 1 and not capitulo_seguido) or (
            self.en_anexos and nivel == 2 and not self.sigue_en_la_hoja)
        self.xml.append(parrafo(en_linea(texto), estilo=estilo, salto_antes=nueva_hoja))

    def texto(self, nivel, texto, anterior):
        if texto.startswith(('**Palabras clave:**', '**Keywords:**')):
            etiqueta, _, resto = texto[2:].partition(':**')
            self.xml.append(parrafo(run(etiqueta + ': ', cursiva=True) + en_linea(resto.strip()),
                                    primera=SANGRIA, antes=240))
            return
        if self.modo == 'bibliografia' and not texto.startswith('[completar'):
            # Referencias APA: sangría francesa y doble espacio, alineadas a la izquierda.
            self.xml.append(parrafo(en_linea(texto), izquierda=SANGRIA, colgante=SANGRIA,
                                    interlineado=480, alineacion='left'))
            return
        if texto.startswith('[completar'):
            self.xml.append(parrafo(en_linea(texto), antes=120, despues=120))
            return
        if nivel:
            self.xml.append(parrafo(en_linea(texto), izquierda=SANGRIA * nivel, despues=120))
            return
        primera = None if self.modo == 'resumen' else SANGRIA
        antes = 120 if anterior in ('vineta', 'numero', 'codigo') else None
        self.xml.append(parrafo(en_linea(texto), primera=primera, antes=antes))

    def lista(self, tipo, nivel, dato, ultimo):
        marca, texto = dato
        despues = 120 if ultimo else 0
        if tipo == 'vineta':
            self.xml.append(parrafo(en_linea(texto), estilo='Prrafodelista', vineta=nivel,
                                    despues=despues))
        else:
            izquierda = SANGRIA * (nivel + 1)
            self.xml.append(parrafo(run(marca + '\t') + en_linea(texto), izquierda=izquierda,
                                    colgante=360, tabulador=izquierda, despues=despues))

    def codigo(self, nivel, lineas):
        izquierda = SANGRIA * nivel if nivel else 0
        for k, linea in enumerate(lineas):
            ultima = k == len(lineas) - 1
            self.xml.append(parrafo(run(linea or ' ', codigo=True, tamano=18),
                                    seguir=not ultima, interlineado=240, izquierda=izquierda,
                                    sombreado='F2F2F2', alineacion='left',
                                    antes=60 if k == 0 else None, despues=120 if ultima else None))

    def figura(self, linea_imagen, pie):
        imagen = PATRON_IMAGEN.match(linea_imagen)
        if pie:
            m = PATRON_PIE.match(pie[2])
            titulo, nota = partir_pie(m.group(3))
            self.xml.append(etiqueta_apa(m.group(1), m.group(2), titulo))
        alto = float(imagen.group(3).replace(',', '.')) if imagen.group(3) else 19
        dibujo = self.imagenes.dibujo(imagen.group(2), 16, alto, descripcion=imagen.group(1))
        self.xml.append(parrafo(dibujo, seguir=True, alineacion='center', interlineado=240))
        if pie:
            self.xml.append(nota_apa(nota))

    def tabla(self, filas, pie):
        con_imagenes = any(PATRON_IMAGEN.match(c.strip()) for f in filas for c in f)
        if pie and PATRON_PIE.match(pie[2]):
            m = PATRON_PIE.match(pie[2])
            titulo, nota = partir_pie(m.group(3))
            self.xml.append(etiqueta_apa(m.group(1), m.group(2), titulo))
        else:
            m, nota = None, ''
        if con_imagenes:
            self.xml.append(tabla_figuras(filas, self.imagenes))
        else:
            self.xml.append(tabla_datos(filas))
        if m:
            self.xml.append(nota_apa(nota))
        elif pie:
            self.pie_suelto(pie[2])
        else:
            self.xml.append(parrafo('', despues=120))

    def pie_suelto(self, linea):
        grupo = PATRON_FUENTE_GRUPO.match(linea)
        if grupo:
            self.xml.append(nota_apa(f'Las figuras {grupo.group(1)} son de elaboración propia.'))


# ----------------------------------------------------------- piezas fijas

def indice():
    return (
        parrafo(run('ÍNDICE', negrita=True), estilo='TtuloTDC', salto_antes=True)
        + parrafo(
            '<w:r><w:fldChar w:fldCharType="begin" w:dirty="true"/></w:r>'
            '<w:r><w:instrText xml:space="preserve"> TOC \\o "1-3" \\h \\z \\u </w:instrText></w:r>'
            '<w:r><w:fldChar w:fldCharType="separate"/></w:r>'
            + run('Abre el documento en Word y actualiza el índice.')
            + '<w:r><w:fldChar w:fldCharType="end"/></w:r>',
            estilo='TDC1')
    )


def introduccion_anexos():
    texto = ('Estos son los cinco anexos de la memoria. Los dos primeros, el manual de '
             'instalación y el manual de usuario, son los que exige la guía. Los otros tres '
             'amplían el diseño técnico, recogen las pruebas hechas a mano y guardan el diario '
             f'de desarrollo. El código fuente completo está en el repositorio público {REPOSITORIO}.')
    return (parrafo(run('10. Anexos'), estilo='Ttulo1', salto_antes=True)
            + parrafo(en_linea(texto), primera=SANGRIA))


def parrafos_de(xml):
    return re.findall(r'<w:p[ >].*?</w:p>', xml, re.S)


def rehacer(parrafo_xml, trozos):
    """Sustituye el texto de un párrafo de la portada manteniendo su formato.

    trozos es una lista de (texto, resaltado)."""
    ppr = re.search(r'<w:pPr>.*?</w:pPr>', parrafo_xml, re.S)
    rpr = re.search(r'<w:r[ >](?:(?!</w:r>).)*?(<w:rPr>.*?</w:rPr>)', parrafo_xml, re.S)
    formato = rpr.group(1) if rpr else ''
    formato = re.sub(r'<w:color [^>]*/>', '<w:color w:val="000000"/>', formato)
    runs = ''
    for texto, resaltado in trozos:
        f = formato
        if resaltado:
            f = f.replace('</w:rPr>', '<w:highlight w:val="yellow"/></w:rPr>') if f else '<w:rPr><w:highlight w:val="yellow"/></w:rPr>'
        runs += f'<w:r>{f}<w:t xml:space="preserve">{escape(texto)}</w:t></w:r>'
    apertura = re.match(r'<w:p[^>]*>', parrafo_xml).group(0)
    return f'{apertura}{ppr.group(0) if ppr else ""}{runs}</w:p>'


def portada(cuerpo_plantilla):
    fin = cuerpo_plantilla.index('<w:sdt>')
    xml = cuerpo_plantilla[:fin]
    cambios = {
        'TÍTULO DEL PROYECTO INTERMODULAR': [(f'“{TITULO}”', False)],
        'PROYECTO INTERMODULAR de (nombre de la línea escogida)': [(f'Proyecto intermodular de {LINEA}', False)],
        'Nombre del alumno': [(ALUMNO, False)],
        'Nombre del tutor del proyecto INTERMODULAR': [(TUTOR, False)],
    }
    for p in parrafos_de(xml):
        texto = ''.join(re.findall(r'<w:t(?: [^>]*)?>([^<]*)</w:t>', p)).strip('“” ')
        if texto in cambios:
            xml = xml.replace(p, rehacer(p, cambios[texto]), 1)
        elif '<w:br w:type="page"/>' in p:
            # La portada llena la hoja y este salto acababa en la página 2,
            # dejándola en blanco. El índice ya empieza solo en página nueva.
            xml = xml.replace(p, '', 1)
    return xml


def estilos(xml):
    # La plantilla pide capítulos en mayúscula y negrita; su estilo de título 1
    # ya tiene Arial 16, así que solo le falta eso.
    titulo1 = re.search(r'<w:style [^>]*w:styleId="Ttulo1".*?</w:style>', xml, re.S).group(0)
    nuevo = titulo1.replace('<w:spacing w:before="240"/>', '<w:spacing w:before="0" w:after="360"/>')
    nuevo = re.sub(r'(<w:rPr><w:rFonts [^>]*/>)', r'\1<w:b/><w:bCs/><w:caps/>', nuevo)
    xml = xml.replace(titulo1, nuevo)
    color = '<w:color w:val="365F91" w:themeColor="accent1" w:themeShade="BF"/>'
    extra = ''
    for nivel, tamano, antes in ((2, 28, 360), (3, 24, 240)):
        extra += (
            f'<w:style w:type="paragraph" w:styleId="Ttulo{nivel}"><w:name w:val="heading {nivel}"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="9"/>'
            '<w:unhideWhenUsed/><w:qFormat/><w:pPr><w:keepNext/><w:keepLines/>'
            f'<w:spacing w:before="{antes}" w:after="120"/><w:jc w:val="left"/>'
            f'<w:outlineLvl w:val="{nivel - 1}"/></w:pPr><w:rPr><w:b/><w:bCs/>{color}'
            f'<w:sz w:val="{tamano}"/><w:szCs w:val="{tamano}"/></w:rPr></w:style>'
        )
    for nivel, izquierda in ((2, 240), (3, 480)):
        extra += (
            f'<w:style w:type="paragraph" w:styleId="TDC{nivel}"><w:name w:val="toc {nivel}"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:autoRedefine/>'
            '<w:uiPriority w:val="39"/><w:unhideWhenUsed/>'
            f'<w:pPr><w:spacing w:after="100"/><w:ind w:left="{izquierda}"/></w:pPr></w:style>'
        )
    return xml.replace('</w:styles>', extra + '</w:styles>')


def cabecera(xml):
    # Deja el logo y cambia el «Título del Proyecto Intermodular» por el título real.
    sin_texto = re.sub(r'<w:r(?: [^>]*)?>(?:(?!</w:r>).)*?<w:t(?: [^>]*)?>[^<]*</w:t></w:r>', '', xml, flags=re.S)
    nuevo_run = f'<w:r><w:rPr><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr><w:t>{escape(TITULO)}</w:t></w:r>'
    return sin_texto.replace('</w:drawing></w:r>', '</w:drawing></w:r>' + nuevo_run, 1)


# ------------------------------------------------------------------- montaje

def cuerpo_memoria(imagenes):
    xml = []
    for nombre in CAPITULOS:
        modo = {'01-resumen.md': 'resumen', '02-abstract.md': 'resumen',
                '09-bibliografia.md': 'bibliografia'}.get(nombre, 'normal')
        capitulo = Capitulo(imagenes, modo)
        capitulo.convertir(leer_bloques((MEMORIA / nombre).read_text(encoding='utf-8')))
        xml += capitulo.xml
    xml.append(introduccion_anexos())
    for k, nombre in enumerate(ANEXOS):
        capitulo = Capitulo(imagenes, en_anexos=True, sigue_en_la_hoja=k == 0)
        capitulo.convertir(leer_bloques((MEMORIA / nombre).read_text(encoding='utf-8')))
        xml += capitulo.xml
    return ''.join(xml)


def generar(plantilla, salida):
    imagenes = Imagenes()
    with zipfile.ZipFile(plantilla) as zp:
        partes = {n: zp.read(n) for n in zp.namelist()}

    documento = partes['word/document.xml'].decode('utf-8')
    inicio = documento.index('<w:body>') + len('<w:body>')
    final = documento.rindex('<w:sectPr')
    cuerpo = documento[inicio:final]
    seccion = documento[final:]
    nuevo = documento[:inicio] + portada(cuerpo) + indice() + cuerpo_memoria(imagenes) + seccion
    partes['word/document.xml'] = nuevo.encode('utf-8')

    partes['word/styles.xml'] = estilos(partes['word/styles.xml'].decode('utf-8')).encode('utf-8')
    partes['word/header2.xml'] = cabecera(partes['word/header2.xml'].decode('utf-8')).encode('utf-8')

    relaciones = partes['word/_rels/document.xml.rels'].decode('utf-8')
    nuevas = ''.join(
        f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="{nombre}"/>'
        for nombre, _, rid in imagenes.lista)
    partes['word/_rels/document.xml.rels'] = relaciones.replace('</Relationships>', nuevas + '</Relationships>').encode('utf-8')
    for nombre, ruta, _ in imagenes.lista:
        partes['word/' + nombre] = ruta.read_bytes()

    nucleo = partes['docProps/core.xml'].decode('utf-8')
    nucleo = re.sub(r'<dc:title>.*?</dc:title>|<dc:title/>', f'<dc:title>{escape(TITULO)}</dc:title>', nucleo)
    partes['docProps/core.xml'] = nucleo.encode('utf-8')

    with zipfile.ZipFile(salida, 'w', zipfile.ZIP_DEFLATED) as zs:
        for nombre, datos in partes.items():
            zs.writestr(nombre, datos)
    print(f'{salida}: {len(imagenes.lista)} imágenes')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('Uso: python generar_word.py <plantilla.docx> <salida.docx>')
    generar(sys.argv[1], sys.argv[2])
