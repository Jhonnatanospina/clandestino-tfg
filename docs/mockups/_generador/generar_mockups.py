# -*- coding: utf-8 -*-
"""
Generador de los mockups de Clandestino (TFG DAM) para draw.io.

Qué hace
    Escribe un fichero .drawio (XML mxfile/mxGraphModel sin comprimir) por pantalla
    y, si draw.io está instalado, exporta cada uno a PNG (escala 2, borde 10).
    Todas las pantallas comparten las mismas funciones (marco de teléfono, cabecera,
    botón, chip, tarjeta, burbuja de hora, calendario, barra de navegación...), así
    que cualquier cambio de estilo se hace una sola vez, aquí.

Cómo se usa
    python generar_mockups.py              # genera los .drawio y los PNG
    python generar_mockups.py --sin-png    # solo los .drawio
    python generar_mockups.py A1 C2        # solo esas pantallas (A1, A2, A3, A3B, C1,
                                           # C2, C3, D1, D2, D3, D4, RESUMEN)

Decisiones de diseño
    * 1 px = 1 dp. La pantalla mide 360 x 740 dp; el marco del teléfono añade 6 px
      de bisel por cada lado (372 x 752).
    * Identidad visual de analisis/marca.json: fondo claro, tres capas de neutros,
      filetes finos, rojo #DD0426 solo para la acción afirmativa o la selección,
      titulares en mayúsculas con Impact (sustituta de Anton) y Segoe UI en el cuerpo.
    * Ningún texto baja de 12 px (la memoria se imprime en A4).
    * Los datos de ejemplo salen de un único conjunto (HORARIO, EXCEPCIONES, CITAS),
      de modo que el calendario de C2, las tarjetas de D1/D2 y la lista de C1 son
      coherentes entre sí y con el esquema (database/schema.sql). Antes de dibujar,
      verificar_coherencia() comprueba esas reglas (cupo, hueco único, horario igual
      al de database/seed.sql, C2 sin conflicto con las citas de @marta...) y se
      niega a generar si alguna falla.
    * El cliente no ve nunca el precio (api-contrato.md, GET /api/mis-citas) y la
      agenda del administrador solo lista citas 'confirmada' (UC09).
    * Cada elemento cuelga del marco del teléfono (coordenadas relativas), así que en
      draw.io basta con mover el marco para mover la pantalla entera.
"""
from __future__ import annotations

import argparse
import base64
import calendar
import re
import subprocess
import sys
from datetime import date, timedelta
from functools import lru_cache
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

from PIL import ImageFont

# --------------------------------------------------------------------------- rutas
SALIDA = Path(__file__).resolve().parent.parent
DRAWIO_EXE = Path(r"C:\Users\jhony\AppData\Local\Programs\draw.io\draw.io.exe")
FUENTES_WIN = Path(r"C:\Windows\Fonts")

# --------------------------------------------------------------------------- paleta
# Valores de analisis/marca.json (tema claro). El filete es algo más marcado que el 10 %
# de negro de la web para que no desaparezca al imprimir.
TINTA = "#121212"          # c-text
SECUNDARIO = "#5A5A5E"     # c-sec
SILENCIO = "#8E8E93"       # c-muted (solo deshabilitado y marcadores de posición)
APAGADO = "#B9B9BE"        # días fuera de horizonte o cerrados
SUPERFICIE = "#FFFFFF"     # c-surface
ELEVADO = "#F2F2F4"       # c-raised
MARCO = "#E9E9EC"          # c-frame
FILETE = "#DADADF"         # c-hair (tarjetas, divisores)
FILETE_FUERTE = "#BDBDC2"  # c-hair reforzado (campos y botones secundarios)
ACENTO = "#DD0426"         # c-accent: SOLO relleno de acción afirmativa o selección
ACENTO_TINTA = "#C40320"   # c-accent-ink: texto y borde rojos
ACENTO_SUAVE = "#FCE6E9"   # c-accent-soft
INFO = "#2563EB"           # azul informativo (vacaciones)
INFO_TINTA = "#1D4ED8"
INFO_SUAVE = "#E9F0FD"
BLANCO = "#FFFFFF"

# --------------------------------------------------------------------------- tipografía
TITULO = "Impact"          # sustituta visual de Anton
CUERPO = "Segoe UI"
CUERPO_SB = "Segoe UI Semibold"

_FICHEROS_FUENTE = {
    ("n", False): "segoeui.ttf",
    ("s", False): "seguisb.ttf",
    ("b", False): "segoeuib.ttf",
    ("n", True): "impact.ttf",
    ("s", True): "impact.ttf",
    ("b", True): "impact.ttf",
}


@lru_cache(maxsize=None)
def _ttf(fichero: str, tam: float):
    return ImageFont.truetype(str(FUENTES_WIN / fichero), tam)


def medir(texto: str, tam: float, peso: str = "n", titulo: bool = False, esp: float = 0.0) -> float:
    """Ancho aproximado en px de una línea de texto (para evitar desbordes)."""
    fuente = _ttf(_FICHEROS_FUENTE[(peso, titulo)], tam)
    return fuente.getlength(texto) + esp * len(texto)


def _lineas_necesarias(texto: str, ancho: float, tam: float, peso: str, esp: float) -> int:
    total = 0
    for parrafo in texto.split("\n"):
        palabras = parrafo.split(" ")
        actual = ""
        n = 1
        for pal in palabras:
            prueba = pal if not actual else actual + " " + pal
            if medir(prueba, tam, peso, esp=esp) <= ancho:
                actual = prueba
            else:
                n += 1
                actual = pal
        total += n
    return total


# --------------------------------------------------------------------------- iconos (trazo)
# Iconos de 24x24 de trazo redondeado, como pide marca.json (trazo 1,8-2, sin relleno).
_ICONOS = {
    "lista": '<path d="M9 6h12M9 12h12M9 18h12M4 6h.01M4 12h.01M4 18h.01"/>',
    "calendario": '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M3 10h18M8 3v4M16 3v4"/>',
    "calendario_mas": '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M3 10h18M8 3v4M16 3v4M12 13v5M9.5 15.5h5"/>',
    "ajustes": '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
    "bandeja": '<path d="M3 13l3-8h12l3 8v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"/><path d="M3 13h5l1.5 3h5l1.5-3h5"/>',
    "reloj": '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.2 2"/>',
    "usuarios": '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.4c2.2.7 3.5 2.6 3.5 5.6"/>',
    "refrescar": '<path d="M20 11a8 8 0 0 0-14.5-4M4 4v4h4"/><path d="M4 13a8 8 0 0 0 14.5 4M20 20v-4h-4"/>',
    "atras": '<path d="M19 12H5M11 6l-6 6 6 6"/>',
    "chev_izq": '<path d="M15 6l-6 6 6 6"/>',
    "chev_der": '<path d="M9 6l6 6-6 6"/>',
    "chev_abajo": '<path d="M6 9l6 6 6-6"/>',
    "ojo": '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    "check": '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    "mas": '<path d="M12 5v14M5 12h14"/>',
    "cerrar": '<path d="M6 6l12 12M18 6L6 18"/>',
    "copiar": '<rect x="8" y="8" width="13" height="13" rx="2.5"/><path d="M16 8V6.5A2.5 2.5 0 0 0 13.5 4h-7A2.5 2.5 0 0 0 4 6.5v7A2.5 2.5 0 0 0 6.5 16H8"/>',
    "salir": '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
    "papelera": '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
    "candado": '<rect x="4.5" y="11" width="15" height="10" rx="3"/><path d="M8 11V8a4 4 0 0 1 8 0v3M12 15v2.5"/>',
}
_cache_iconos: dict[tuple, str] = {}


def _svg_datauri(nombre: str, color: str, grosor: float) -> str:
    clave = (nombre, color, grosor)
    if clave not in _cache_iconos:
        svg = (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" '
            f'stroke="{color}" stroke-width="{grosor}" stroke-linecap="round" '
            f'stroke-linejoin="round">{_ICONOS[nombre]}</svg>'
        )
        _cache_iconos[clave] = base64.b64encode(svg.encode("utf-8")).decode("ascii")
    return "data:image/svg+xml," + _cache_iconos[clave]


# --------------------------------------------------------------------------- datos de ejemplo
# Un único conjunto de datos para que todas las pantallas cuenten la misma historia.
# "Hoy" es lunes 1 de febrero de 2027, las 18:20; el horizonte de reserva es de dos semanas.
HOY = date(2027, 2, 1)
HORA_AHORA = "18:20"       # la que marca la barra de estado de todas las pantallas
AHORA_MIN = 18 * 60 + 20   # lo mismo en minutos: una cita es "pasada" si su hora es <= que esto
HORIZONTE_DIAS = 14
CLIENTE_C1 = "marta"       # la cuenta con la que se ve C1, C2 y C3
# Lo que @marta intenta pedir en C2 (el "antes" de enviar la solicitud).
DIA_C2 = date(2027, 2, 11)
HORA_C2 = 19
DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"]
DIAS_CORTO = ["LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM"]
MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
         "septiembre", "octubre", "noviembre", "diciembre"]
MESES_CORTO = ["ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"]

# horarios_semanales: dia_semana (1 = lunes ... 7 = domingo) -> (inicio, fin, cupo, activo)
# Lunes a jueves de 16:00 a 22:00 con cupo 2, igual que database/seed.sql. Viernes a domingo
# no tienen fila en la base de datos: D3 los dibuja con el valor por defecto y apagados.
HORARIO = {
    1: (16, 22, 2, True),
    2: (16, 22, 2, True),
    3: (16, 22, 2, True),
    4: (16, 22, 2, True),
    5: (16, 22, 2, False),
    6: (16, 22, 2, False),
    7: (16, 22, 2, False),
}
# excepciones_dia: fecha -> (tipo, inicio, fin, cupo, nota)
EXCEPCIONES = {
    date(2027, 2, 9): ("cerrado", None, None, None, "Vacaciones"),
    date(2027, 2, 10): ("cerrado", None, None, None, "Vacaciones"),
    date(2027, 2, 13): ("abierto", 10, 14, 2, "Día extra"),
}
# citas: (cliente, fecha, hora, estado). Solo pendiente y confirmada ocupan hueco y cupo.
# Con cupo 2, ningún día tiene más de dos citas activas (lo comprueba verificar_coherencia).
CITAS = [
    # historial de @marta (C1)
    ("marta", date(2027, 1, 20), 16, "cancelada"),
    ("marta", date(2027, 1, 25), 17, "hecha"),
    # lunes 1 (hoy): @sergio ya pasó por la silla y salió de la agenda (hecha); @ana (17:00)
    # ya ha pasado y toca pasar lista; @javi (19:00) todavía no ha llegado
    ("sergio", date(2027, 2, 1), 16, "hecha"),
    ("ana", date(2027, 2, 1), 17, "confirmada"),
    ("javi", date(2027, 2, 1), 19, "confirmada"),
    # martes 2: la solicitud de @marta (C1 y D1) más la cita de @carlos = cupo completo
    ("marta", date(2027, 2, 2), 16, "pendiente"),
    ("carlos", date(2027, 2, 2), 17, "confirmada"),
    ("lucas", date(2027, 2, 3), 17, "pendiente"),
    ("ana", date(2027, 2, 4), 16, "confirmada"),
    ("marta", date(2027, 2, 4), 18, "confirmada"),
    ("carlos", date(2027, 2, 8), 16, "confirmada"),
    ("sergio", date(2027, 2, 8), 17, "confirmada"),
    # jueves 11: solo la solicitud de @pablo; el día sigue libre y es el que elige @marta en C2
    ("pablo", date(2027, 2, 11), 17, "pendiente"),
]
ACTIVAS = ("pendiente", "confirmada")
# invitaciones (D4): (código, nota, estado, detalle). La primera es la que se acaba de generar;
# la segunda es la que @diego usa en A2 y la tercera ya no sirve. Alfabeto de RN-20 (sin 0 O 1 I L).
INVITACIONES = [
    ("H8TQ5C3ZN6VA", "Sofía", "pendiente", "caduca en 7 días"),
    ("K7M4X9PQR2WD", "Diego", "usada", "usada por @diego"),
    ("W6D4M9XTJ2EC", "Pedro", "caducada", "caducó el 24 ene"),
]


def tramo_del_dia(d: date):
    """(inicio, fin, cupo) con que se puede reservar ese día, o None si está cerrado."""
    exc = EXCEPCIONES.get(d)
    if exc:
        if exc[0] == "cerrado":
            return None
        return exc[1], exc[2], exc[3] or 2
    ini, fin, cupo, activo = HORARIO[d.isoweekday()]
    return (ini, fin, cupo) if activo else None


def citas_activas(d: date):
    return [c for c in CITAS if c[1] == d and c[3] in ACTIVAS]


def estado_dia(d: date) -> str:
    """'libre', 'completo', 'vacaciones' o 'cerrado' (incluye fuera del horizonte)."""
    if d < HOY or d >= HOY + timedelta(days=HORIZONTE_DIAS):
        return "cerrado"
    exc = EXCEPCIONES.get(d)
    if exc and exc[0] == "cerrado":
        return "vacaciones"
    tramo = tramo_del_dia(d)
    if tramo is None:
        return "cerrado"
    return "completo" if len(citas_activas(d)) >= tramo[2] else "libre"


def horas_del_dia(d: date):
    """[(hora, ocupada)] con una cita de una hora por hueco."""
    tramo = tramo_del_dia(d)
    ocupadas = {c[2] for c in citas_activas(d)}
    return [(h, h in ocupadas) for h in range(tramo[0], tramo[1])]


def etiqueta_corta(d: date) -> str:           # MAR 2
    return f"{DIAS_CORTO[d.weekday()]} {d.day}"


def etiqueta_con_mes(d: date) -> str:         # MAR 2 FEB
    return f"{DIAS_CORTO[d.weekday()]} {d.day} {MESES_CORTO[d.month - 1]}"


def hh(h: int) -> str:
    return f"{h:02d}:00"


def citas_de(cliente: str):
    return [c for c in CITAS if c[0] == cliente]


def es_pasada(fecha: date, hora: int) -> bool:
    """Cita pasada: su fecha y hora ya han llegado (RN, definiciones)."""
    return (fecha, hora * 60) <= (HOY, AHORA_MIN)


def verificar_coherencia() -> list[str]:
    """Reglas del esquema y de los documentos que todos los mockups deben cumplir a la vez."""
    err: list[str] = []
    vistos: dict[tuple, str] = {}
    por_dia: dict[date, int] = {}
    for cli, fecha, hora, estado in CITAS:
        if estado not in ACTIVAS:
            continue
        if (fecha, hora) in vistos:                                  # uq_citas_hueco_activo
            err.append(f"hueco repetido {fecha} {hh(hora)}: @{cli} y @{vistos[(fecha, hora)]}")
        vistos[(fecha, hora)] = cli
        tramo = tramo_del_dia(fecha)
        if tramo is None:
            err.append(f"cita activa de @{cli} en un día cerrado ({fecha})")
        elif not tramo[0] <= hora < tramo[1]:
            err.append(f"cita de @{cli} fuera del tramo ({fecha} {hh(hora)})")
        por_dia[fecha] = por_dia.get(fecha, 0) + 1
    for fecha, n in por_dia.items():
        tramo = tramo_del_dia(fecha)
        if tramo and n > tramo[2]:
            err.append(f"{fecha}: {n} citas activas y cupo {tramo[2]}")
    # C2: el día y la hora que elige @marta
    if estado_dia(DIA_C2) != "libre":
        err.append(f"C2: {DIA_C2} no está libre ({estado_dia(DIA_C2)})")
    elif len(citas_activas(DIA_C2)) >= tramo_del_dia(DIA_C2)[2]:
        err.append("C2: el día elegido ya tiene el cupo completo")
    if dict(horas_del_dia(DIA_C2)).get(HORA_C2) is not False:
        err.append(f"C2: {hh(HORA_C2)} no está libre el {DIA_C2}")
    if any(c[1] == DIA_C2 and c[2] == HORA_C2 for c in citas_de(CLIENTE_C1)):
        err.append("C2: @marta ya tiene una cita a esa fecha y hora")
    # D2: solo confirmadas, y las dos situaciones (pasada y futura) en el día de hoy
    hoy = [c for c in citas_activas(HOY) if c[3] == "confirmada"]
    if not any(es_pasada(c[1], c[2]) for c in hoy) or all(es_pasada(c[1], c[2]) for c in hoy):
        err.append("D2: falta una cita confirmada pasada y otra futura para hoy")
    # el horario de ejemplo es el de database/seed.sql
    seed = SALIDA.parent.parent / "database" / "seed.sql"
    if seed.exists():
        filas = re.findall(r"\(\s*(\d)\s*,\s*'(\d\d):00'\s*,\s*'(\d\d):00'\s*,\s*(\d+)\s*,\s*TRUE\s*\)",
                           seed.read_text(encoding="utf-8"))
        del_seed = {int(d): (int(i), int(f), int(c)) for d, i, f, c in filas}
        del_script = {d: (v[0], v[1], v[2]) for d, v in HORARIO.items() if v[3]}
        if del_seed != del_script:
            err.append(f"HORARIO no coincide con seed.sql: {del_script} frente a {del_seed}")
    else:
        err.append(f"no encuentro {seed} para comparar el horario")
    return err


# --------------------------------------------------------------------------- lienzo draw.io
def _r(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s if s else "0"


def est(**kw) -> str:
    """Construye el atributo style de draw.io (k=v;k=v;)."""
    partes = []
    for k, v in kw.items():
        if v is None:
            continue
        if v is True:
            v = 1
        elif v is False:
            v = 0
        partes.append(f"{k}={v}")
    return ";".join(partes) + ";"


class Lienzo:
    """Una página de draw.io. Todas las funciones de dibujo cuelgan de aquí."""

    def __init__(self, nombre: str):
        self.nombre = nombre
        self._n = 1
        self.xml: list[str] = []
        self.avisos: list[str] = []

    # ---- núcleo
    def _id(self) -> str:
        self._n += 1
        return f"m{self._n}"

    def _celda(self, padre: str, x, y, w, h, estilo: str, valor: str = "") -> str:
        cid = self._id()
        self.xml.append(
            f'<mxCell id="{cid}" value={quoteattr(valor)} style="{estilo}" vertex="1" parent="{padre}">'
            f'<mxGeometry x="{_r(x)}" y="{_r(y)}" width="{_r(w)}" height="{_r(h)}" as="geometry"/></mxCell>'
        )
        return cid

    def _aviso(self, msg: str):
        self.avisos.append(f"[{self.nombre}] {msg}")

    # ---- primitivas
    def caja(self, p, x, y, w, h, relleno=SUPERFICIE, borde=FILETE, grosor=1, radio=0, valor="", extra=None):
        estilo = est(
            rounded=1 if radio else 0,
            absoluteArcSize=1 if radio else None,
            arcSize=_r(min(radio * 2, w, h)) if radio else None,
            whiteSpace="wrap",
            html=1,
            fillColor=relleno or "none",
            strokeColor=borde or "none",
            strokeWidth=grosor if borde else None,
            **(extra or {}),
        )
        return self._celda(p, x, y, w, h, estilo, valor)

    def circulo(self, p, x, y, d, relleno=SUPERFICIE, borde=None, grosor=1):
        estilo = "ellipse;" + est(whiteSpace="wrap", html=1,
                                  fillColor=relleno or "none", strokeColor=borde or "none",
                                  strokeWidth=grosor if borde else None)
        return self._celda(p, x, y, d, d, estilo)

    def texto(self, p, x, y, w, h, valor, tam=14, color=TINTA, peso="n", alinea="left",
              vert="middle", esp=0.0, tachado=False, ajustar=False, titulo=False):
        """Caja de texto. Falla (aviso) si el texto no cabe o baja de 12 px."""
        if tam < 12:
            self._aviso(f"texto por debajo de 12 px: {valor!r} ({tam})")
        if not ajustar and "\n" not in valor:
            ancho = medir(valor, tam, peso, titulo, esp)
            if ancho > w + 1:
                self._aviso(f"texto más ancho que su caja ({ancho:.0f} > {w:.0f}): {valor!r}")
        else:
            n = _lineas_necesarias(valor, w, tam, peso, esp)
            if n * tam * 1.3 > h + 2:
                self._aviso(f"texto de {n} líneas no cabe en {h:.0f} px de alto: {valor[:40]!r}")
        html = escape(valor).replace("\n", "<br>")
        if tachado:
            html = f"<strike>{html}</strike>"
        if esp:
            html = f'<span style="letter-spacing:{_r(esp)}px">{html}</span>'
        if titulo:
            familia, negrita = TITULO, 0
        elif peso == "s":
            familia, negrita = CUERPO_SB, 0
        elif peso == "b":
            familia, negrita = CUERPO, 1
        else:
            familia, negrita = CUERPO, 0
        estilo = "text;" + est(
            html=1, strokeColor="none", fillColor="none",
            align=alinea, verticalAlign=vert, spacing=0,
            whiteSpace="wrap" if (ajustar or "\n" in valor) else None,
            fontFamily=familia, fontSize=tam, fontColor=color, fontStyle=negrita,
        )
        return self._celda(p, x, y, w, h, estilo, html)

    def icono(self, p, nombre, x, y, s=24, color=TINTA, grosor=2):
        estilo = est(shape="image", aspect="fixed", imageAspect=0, image=_svg_datauri(nombre, color, grosor),
                     html=1, fillColor="none", strokeColor="none")
        return self._celda(p, x, y, s, s, estilo)

    def separador(self, p, x, y, w, color=FILETE):
        return self.caja(p, x, y, w, 1, relleno=color, borde=None)

    # ---- teléfono, barra de estado, cabecera y navegación
    def telefono(self, x, y) -> str:
        """Marco de teléfono (bisel de 6 px). Devuelve el id de la pantalla de 360 x 740."""
        marco = self.caja("1", x, y, 372, 752, relleno=TINTA, borde=None, radio=40)
        return self.caja(marco, 6, 6, 360, 740, relleno=SUPERFICIE, borde=None, radio=34)

    def barra_estado(self, p, hora=HORA_AHORA):
        self.texto(p, 28, 4, 60, 20, hora, 13, TINTA, "s")
        self.circulo(p, 174, 8, 12, TINTA)  # cámara frontal
        for i in range(4):  # cobertura
            alto = 5 + i * 2.5
            self.caja(p, 280 + i * 6, 20 - alto, 4, alto, relleno=TINTA, borde=None, radio=1)
        self.caja(p, 308, 8, 24, 12, relleno=SUPERFICIE, borde=TINTA, grosor=1.5, radio=4)  # batería
        self.caja(p, 310.5, 10.5, 15, 7, relleno=TINTA, borde=None, radio=2)
        self.caja(p, 333, 11, 2, 6, relleno=TINTA, borde=None, radio=1)

    def boton_circular(self, p, x, y, icono, d=40, color=SECUNDARIO):
        self.circulo(p, x, y, d, SUPERFICIE, FILETE_FUERTE)
        self.icono(p, icono, x + (d - 20) / 2, y + (d - 20) / 2, 20, color)

    def cabecera(self, p, eyebrow, titulo, atras=False, refrescar=False, guardar=False):
        x0 = 64 if atras else 16
        if atras:
            self.boton_circular(p, 12, 42, "atras", 40, TINTA)
        self.texto(p, x0, 34, 230, 16, eyebrow.upper(), 13, SECUNDARIO, "b", esp=1.4)
        self.texto(p, x0, 50, 236, 40, titulo.upper(), 30, TINTA, titulo=True, esp=1.5)
        if refrescar:
            self.boton_circular(p, 304, 44, "refrescar")
        if guardar:
            self.boton(p, 240, 44, 104, 40, "Guardar", "primario", tam=15, radio=12)

    def barra_navegacion(self, p, pestanas, activa):
        """pestanas = [(icono, etiqueta)], activa = índice. Material 3: 80 dp con gesto."""
        self.separador(p, 0, 660, 360)
        ancho = 360 / len(pestanas)
        for i, (ico, etiqueta) in enumerate(pestanas):
            cx = ancho * i + ancho / 2
            on = i == activa
            if on:
                self.caja(p, cx - 32, 668, 64, 32, relleno=ACENTO_SUAVE, borde=None, radio=16)
            self.icono(p, ico, cx - 12, 672, 24, ACENTO_TINTA if on else SECUNDARIO)
            self.texto(p, cx - ancho / 2, 704, ancho, 16, etiqueta, 13,
                       ACENTO_TINTA if on else SECUNDARIO, "b" if on else "n", alinea="center")
        self.caja(p, 126, 728, 108, 4, relleno=TINTA, borde=None, radio=2)

    def nav_cliente(self, p, activa):
        self.barra_navegacion(p, [("lista", "Mis citas"), ("calendario_mas", "Pedir"), ("ajustes", "Ajustes")], activa)

    def nav_admin(self, p, activa):
        self.barra_navegacion(p, [("bandeja", "Solicitudes"), ("calendario", "Agenda"),
                                  ("reloj", "Horarios"), ("usuarios", "Clientes")], activa)

    def gesto(self, p):
        self.caja(p, 126, 728, 108, 4, relleno=TINTA, borde=None, radio=2)

    # ---- componentes
    def tarjeta(self, p, x, y, w, h, relleno=SUPERFICIE, borde=FILETE):
        return self.caja(p, x, y, w, h, relleno=relleno, borde=borde, radio=16)

    def eyebrow(self, p, x, y, w, texto, alinea="left"):
        return self.texto(p, x, y, w, 16, texto.upper(), 13, SECUNDARIO, "b", alinea=alinea, esp=1.4)

    def boton(self, p, x, y, w, h, texto, tipo="primario", tam=15, icono=None, radio=None):
        """tipo: primario (rojo, afirmativo) | secundario (filete) | texto | apagado (deshabilitado)."""
        radio = radio if radio is not None else (16 if h >= 52 else 12)
        relleno, borde, color = {
            "primario": (ACENTO, None, BLANCO),
            "secundario": (SUPERFICIE, FILETE_FUERTE, SECUNDARIO),
            "texto": (None, None, SECUNDARIO),
            "apagado": (ELEVADO, None, SILENCIO),
        }[tipo]
        self.caja(p, x, y, w, h, relleno=relleno, borde=borde, radio=radio)
        ancho_t = medir(texto, tam, "s")
        total = ancho_t + (20 + 8 if icono else 0)
        if total > w - 8:
            self._aviso(f"botón {texto!r} no cabe ({total:.0f} > {w - 8:.0f})")
        ini = x + (w - total) / 2
        if icono:
            self.icono(p, icono, ini, y + (h - 20) / 2, 20, color)
            ini += 28
        self.texto(p, ini - 1, y, ancho_t + 2, h, texto, tam, color, "s", alinea="left")

    def chip(self, p, x, y, texto, tono="neutro", derecha=False, h=26, tam=13):
        """Chip de estado: positivo (rojo suave), neutro (gris) o info (azul). Devuelve su ancho."""
        relleno, color, punto = {
            "positivo": (ACENTO_SUAVE, ACENTO_TINTA, ACENTO),
            "neutro": (ELEVADO, SECUNDARIO, SILENCIO),
            "info": (INFO_SUAVE, INFO_TINTA, INFO),
        }[tono]
        ancho_t = medir(texto, tam, "s")
        w = 10 + 8 + 6 + ancho_t + 12
        if derecha:
            x = x - w
        self.caja(p, x, y, w, h, relleno=relleno, borde=None, radio=h / 2)
        self.circulo(p, x + 10, y + (h - 8) / 2, 8, punto)
        self.texto(p, x + 24, y, ancho_t + 4, h, texto, tam, color, "s")
        return w

    def burbuja_hora(self, p, x, y, w, h, hora, estado="libre"):
        """estado: libre | seleccionada | ocupada."""
        if estado == "seleccionada":
            self.caja(p, x, y, w, h, relleno=ACENTO, borde=ACENTO, radio=h / 2)
            self.texto(p, x, y, w, h, hora, 17, BLANCO, "b", alinea="center")
        elif estado == "ocupada":
            self.caja(p, x, y, w, h, relleno=SUPERFICIE, borde=FILETE, radio=h / 2)
            self.texto(p, x, y, w, h, hora, 17, SILENCIO, "n", alinea="center", tachado=True)
        else:
            self.caja(p, x, y, w, h, relleno=SUPERFICIE, borde=FILETE_FUERTE, radio=h / 2)
            self.texto(p, x, y, w, h, hora, 17, TINTA, "s", alinea="center")

    def interruptor(self, p, x, y, activo):
        if activo:
            self.caja(p, x, y, 44, 26, relleno=ACENTO, borde=None, radio=13)
            self.circulo(p, x + 21, y + 3, 20, BLANCO)
        else:
            self.caja(p, x, y, 44, 26, relleno=ELEVADO, borde=FILETE_FUERTE, radio=13)
            self.circulo(p, x + 3, y + 3, 20, BLANCO, FILETE_FUERTE)

    def campo(self, p, x, y, w, etiqueta, valor, h=48, icono_der=None, ayuda=None, color=TINTA):
        """Campo con etiqueta encima. Devuelve la y del borde inferior (incluida la ayuda)."""
        self.texto(p, x, y, w, 18, etiqueta, 13, SECUNDARIO, "s")
        yc = y + 22
        self.caja(p, x, yc, w, h, relleno=ELEVADO, borde=FILETE_FUERTE, radio=12)
        reserva = 44 if icono_der else 14
        self.texto(p, x + 14, yc, w - 14 - reserva, h, valor, 16, color)
        if icono_der:
            self.icono(p, icono_der, x + w - 38, yc + (h - 24) / 2, 24, SECUNDARIO)
        fin = yc + h
        if ayuda:
            self.texto(p, x + 2, fin + 3, w - 4, 16, ayuda, 13, SECUNDARIO)
            fin += 19
        return fin

    def selector(self, p, x, y, w, h, valor, tam=16):
        """Desplegable (hora final, etc.)."""
        self.caja(p, x, y, w, h, relleno=ELEVADO, borde=FILETE_FUERTE, radio=12)
        self.texto(p, x + 14, y, w - 50, h, valor, tam, TINTA, "s")
        self.icono(p, "chev_abajo", x + w - 34, y + (h - 22) / 2, 22, SECUNDARIO)

    def segmentado(self, p, x, y, opciones, activa, ancho_op=None, h=36, ancho_total=None):
        """Selector en píldora (Semana | Mes). La opción activa va en rojo (selección)."""
        n = len(opciones)
        if ancho_total:
            ancho_op = (ancho_total - 8) / n
        elif ancho_op is None:
            ancho_op = max(medir(o, 14, "s") for o in opciones) + 32
        total = ancho_op * n + 8
        self.caja(p, x, y, total, h + 8, relleno=SUPERFICIE, borde=FILETE_FUERTE, radio=(h + 8) / 2)
        for i, o in enumerate(opciones):
            ox = x + 4 + i * ancho_op
            if i == activa:
                self.caja(p, ox, y + 4, ancho_op, h, relleno=ACENTO, borde=None, radio=h / 2)
            self.texto(p, ox, y + 4, ancho_op, h, o, 14, BLANCO if i == activa else SECUNDARIO, "s", alinea="center")
        return total

    def avatar(self, p, x, y, letra, d=44):
        self.circulo(p, x, y, d, ELEVADO, FILETE)
        self.texto(p, x, y, d, d, letra.upper(), 17, SECUNDARIO, "s", alinea="center")

    def calendario(self, p, x, y, anio, mes, estado_de, elegido=None, hoy=None, celda=42, hueco=5):
        """Calendario mensual (lunes primero). Devuelve el alto ocupado.

        Estados por celda (marca.json): libre, seleccionado (relleno rojo), hoy (borde rojo de 2),
        completo (gris y tachado), vacaciones (borde azul de 2) y cerrado (solo el número, apagado).
        """
        paso = celda + hueco
        for i, letra in enumerate("LMXJVSD"):
            self.texto(p, x + i * paso, y, celda, 16, letra, 13, SECUNDARIO, "b", alinea="center", esp=1)
        y0 = y + 22
        primero = date(anio, mes, 1)
        desfase = primero.weekday()
        n_dias = calendar.monthrange(anio, mes)[1]
        filas = (desfase + n_dias + 6) // 7
        for dnum in range(1, n_dias + 1):
            fila, col = divmod(desfase + dnum - 1, 7)
            cx, cy = x + col * paso, y0 + fila * paso
            dia = date(anio, mes, dnum)
            st = estado_de(dia)
            num = str(dnum)
            if elegido == dia:
                self.caja(p, cx, cy, celda, celda, relleno=ACENTO, borde=ACENTO, radio=12)
                self.texto(p, cx, cy, celda, celda, num, 16, BLANCO, "b", alinea="center")
            elif st == "cerrado":
                self.texto(p, cx, cy, celda, celda, num, 15, APAGADO, "n", alinea="center")
            elif st == "completo":
                # si además es hoy, conserva el borde rojo de "hoy" (el día no se puede elegir)
                es_hoy = hoy == dia
                self.caja(p, cx, cy, celda, celda, relleno=ELEVADO, borde=ACENTO if es_hoy else FILETE,
                          grosor=2 if es_hoy else 1, radio=12)
                self.texto(p, cx, cy, celda, celda, num, 15, SILENCIO, "n", alinea="center", tachado=True)
            elif st == "vacaciones":
                self.caja(p, cx, cy, celda, celda, relleno=INFO_SUAVE, borde=INFO, grosor=2, radio=12)
                self.texto(p, cx, cy, celda, celda, num, 15, INFO_TINTA, "s", alinea="center")
            elif hoy == dia:
                self.caja(p, cx, cy, celda, celda, relleno=SUPERFICIE, borde=ACENTO, grosor=2, radio=12)
                self.texto(p, cx, cy, celda, celda, num, 16, ACENTO_TINTA, "b", alinea="center")
            else:
                self.caja(p, cx, cy, celda, celda, relleno=SUPERFICIE, borde=FILETE_FUERTE, radio=12)
                self.texto(p, cx, cy, celda, celda, num, 15, TINTA, "s", alinea="center")
        return 22 + filas * paso - hueco

    # ---- salida
    def a_xml(self) -> str:
        cuerpo = "\n".join(self.xml)
        return (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<mxfile host="app.diagrams.net" agent="generar_mockups.py" version="24.7.0" type="device">\n'
            f'<diagram id="{self.nombre}" name="{self.nombre}">\n'
            '<mxGraphModel dx="1200" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" '
            'arrows="1" fold="1" page="0" pageScale="1" pageWidth="827" pageHeight="1169" math="0" '
            'shadow="0" background="#FFFFFF">\n<root>\n<mxCell id="0"/>\n<mxCell id="1" parent="0"/>\n'
            f"{cuerpo}\n</root>\n</mxGraphModel>\n</diagram>\n</mxfile>\n"
        )

    def guardar(self, ruta: Path):
        ruta.write_text(self.a_xml(), encoding="utf-8")


# =========================================================================== PANTALLAS
# Coordenadas relativas a la pantalla (0,0 = esquina superior izquierda de los 360 x 740).

def pantalla_A1(L: Lienzo, p: str):
    """Entrar: usuario + contraseña (el plan sustituye al @usuario + teléfono del original)."""
    L.barra_estado(p)
    L.texto(p, 16, 108, 328, 64, "CLANDESTINO", 52, TINTA, alinea="center", titulo=True, esp=3)
    L.texto(p, 16, 176, 328, 18, "ESTUDIO PRIVADO · SOLO CON CITA", 13, SECUNDARIO, "b", alinea="center", esp=1.6)
    L.campo(p, 24, 250, 312, "Usuario", "@marta")
    L.campo(p, 24, 340, 312, "Contraseña", "••••••••••", icono_der="ojo")
    L.boton(p, 24, 440, 312, 52, "Entrar", "primario", tam=16)
    L.separador(p, 24, 526, 120)
    L.texto(p, 150, 518, 60, 18, "o", 13, SECUNDARIO, alinea="center")
    L.separador(p, 216, 526, 120)
    L.texto(p, 24, 548, 312, 20, "¿Tienes un código de invitación?", 14, SECUNDARIO, alinea="center")
    L.boton(p, 24, 576, 312, 52, "Crear cuenta con código", "secundario", tam=16)
    L.texto(p, 24, 676, 312, 18, "Acceso solo por invitación del barbero", 13, SECUNDARIO, alinea="center")
    L.gesto(p)


def pantalla_A2(L: Lienzo, p: str):
    """Registro con código de invitación (UC01). El cliente nace 'pendiente'."""
    L.barra_estado(p)
    L.cabecera(p, "Registro con invitación", "Crear cuenta", atras=True)
    y = 108
    y = L.campo(p, 24, y, 312, "Código de invitación", "K7M4X9PQR2WD", ayuda="Te lo da el barbero. Vale 7 días y un solo uso.")
    y = L.campo(p, 24, y + 14, 312, "Usuario", "@diego", ayuda="De 3 a 20: minúsculas, números y _")
    y = L.campo(p, 24, y + 14, 312, "Teléfono", "+34 611 222 333", ayuda="Con prefijo internacional")
    y = L.campo(p, 24, y + 14, 312, "Contraseña", "••••••••••", icono_der="ojo", ayuda="Mínimo 8 caracteres")
    L.boton(p, 24, y + 22, 312, 52, "Crear cuenta", "primario", tam=16)
    # aviso de privacidad antes de enviar (DP-10, RNF-PRI-05): quién, para qué y derechos
    L.texto(p, 32, y + 84, 296, 40, "Tus datos los trata el barbero solo para gestionar citas. Tus derechos, pídeselos a él.",
            13, SECUNDARIO, alinea="center", ajustar=True)
    L.texto(p, 32, y + 132, 296, 40, "Tu acceso quedará pendiente hasta que el barbero lo apruebe.",
            13, SECUNDARIO, alinea="center", ajustar=True)
    L.gesto(p)


def _pantalla_espera(L: Lienzo, p: str, icono, titulo, usuario, texto_cuerpo, estado_txt):
    """Estructura común de A3: el mismo diseño con un texto distinto según el estado (RF-08)."""
    L.barra_estado(p)
    # indicador de "desliza para actualizar" (SwipeRefreshLayout)
    L.boton_circular(p, 160, 54, "refrescar", 40)
    L.texto(p, 60, 98, 240, 18, "Desliza para comprobar", 13, SECUNDARIO, alinea="center")
    L.circulo(p, 132, 166, 96, ELEVADO, FILETE)
    L.icono(p, icono, 156, 190, 48, TINTA, 1.8)
    L.texto(p, 16, 290, 328, 44, titulo, 36, TINTA, alinea="center", titulo=True, esp=1.5)
    L.texto(p, 24, 346, 312, 24, f"Hola, @{usuario}", 17, TINTA, "s", alinea="center")
    L.texto(p, 32, 380, 296, 100, texto_cuerpo, 15, SECUNDARIO, alinea="center", ajustar=True, vert="top")
    ancho_chip = 10 + 8 + 6 + medir(estado_txt, 13, "s") + 12   # mismo cálculo que Lienzo.chip
    L.chip(p, 180 - ancho_chip / 2, 500, estado_txt, "neutro")
    L.boton(p, 24, 548, 312, 52, "Comprobar de nuevo", "primario", tam=16)
    L.boton(p, 24, 612, 312, 48, "Cerrar sesión", "secundario", tam=15)
    L.gesto(p)


def pantalla_A3(L: Lienzo, p: str):
    """Espera de aprobación: cuenta recién creada, estado 'pendiente' (UC01, RF-08)."""
    _pantalla_espera(
        L, p, "reloj", "ACCESO PENDIENTE", "diego",
        "El barbero tiene que aprobar tu cuenta antes de que puedas pedir cita. "
        "Vuelve a abrir la app más tarde o desliza hacia abajo para comprobarlo.",
        "Pendiente")


def pantalla_A3b(L: Lienzo, p: str):
    """Misma pantalla A3 con el estado 'bloqueado' (RF-08, RN-23, DP-13): texto distinto."""
    _pantalla_espera(
        L, p, "candado", "ACCESO BLOQUEADO", "raul",
        "El barbero ha bloqueado tu acceso. Mientras siga así no puedes pedir ni cancelar citas. "
        "Si crees que es un error, habla con él. Desliza hacia abajo para comprobar si ha cambiado.",
        "Bloqueado")


def _tarjeta_cita_cliente(L, p, y, fecha, hora_txt, estado_txt, tono, boton_txt=None):
    alto = 134 if boton_txt else 72
    L.tarjeta(p, 16, y, 328, alto)
    L.texto(p, 30, y + 12, 190, 24, etiqueta_con_mes(fecha), 17, TINTA, "s", esp=0.5)
    L.texto(p, 30, y + 38, 230, 20, hora_txt, 14, SECUNDARIO)
    L.chip(p, 330, y + 14, estado_txt, tono, derecha=True)
    if boton_txt:
        L.boton(p, 30, y + 70, 300, 48, boton_txt, "secundario", tam=15)
    return y + alto


def pantalla_C1(L: Lienzo, p: str):
    """Mis citas (UC05): RecyclerView + tarjetas; cancelar solo en pendiente/confirmada.

    Las tarjetas salen de CITAS (las de @marta). El cliente no recibe el precio ni la propina
    (api-contrato.md, GET /api/mis-citas), así que el historial solo lleva día, hora y estado.
    """
    # estado -> (texto de la hora, texto del chip, tono, botón de cancelar)
    aspecto = {
        "pendiente": (lambda h: f"Pediste a las {hh(h)}", "Pendiente", "neutro", "Cancelar solicitud"),
        "confirmada": (lambda h: f"Confirmada a las {hh(h)}", "Confirmada", "positivo", "Cancelar cita"),
        "hecha": (hh, "Hecha", "positivo", None),
        "cancelada": (hh, "Cancelada", "neutro", None),
    }

    def tarjeta(y, cita):
        _, fecha, hora, estado = cita
        txt, chip, tono, boton = aspecto[estado]
        return _tarjeta_cita_cliente(L, p, y, fecha, txt(hora), chip, tono, boton)

    mias = sorted(citas_de(CLIENTE_C1), key=lambda c: (c[1], c[2]))
    proximas = [c for c in mias if c[3] in ACTIVAS]
    historial = sorted((c for c in mias if c[3] not in ACTIVAS), key=lambda c: (c[1], c[2]), reverse=True)
    L.barra_estado(p)
    L.cabecera(p, f"Hola, @{CLIENTE_C1}", "Mis citas", refrescar=True)
    L.eyebrow(p, 18, 104, 200, "Próximas")
    y = 126
    for i, cita in enumerate(proximas):
        y = tarjeta(y + (10 if i else 0), cita)
    L.eyebrow(p, 18, y + 16, 200, "Historial")
    y += 38
    for i, cita in enumerate(historial):
        y = tarjeta(y + (10 if i else 0), cita)
    L.nav_cliente(p, 0)


def pantalla_C2(L: Lienzo, p: str):
    """Pedir cita (UC03 + UC04): calendario mensual, horas en burbujas y botón ancho.

    @marta ya tiene una solicitud el martes 2 (16:00) y una cita el jueves 4 (18:00), que ocupan
    su sitio en C1 y D1. Aquí pide otro día: el jueves 11, que sigue libre (1 cita activa < cupo 2).
    """
    elegido = DIA_C2
    L.barra_estado(p)
    L.cabecera(p, "Elige día y hora", "Pedir cita")
    L.boton_circular(p, 16, 100, "chev_izq", 36, APAGADO)
    L.texto(p, 60, 100, 240, 36, "FEBRERO 2027", 16, TINTA, "b", alinea="center", esp=1.2)
    L.boton_circular(p, 308, 100, "chev_der", 36, TINTA)
    alto = L.calendario(p, 18, 146, 2027, 2, estado_dia, elegido=elegido, hoy=HOY)
    yl = 146 + alto + 12
    x = 16
    for etiqueta, tipo in (("Libre", "libre"), ("Completo", "completo"), ("Vacaciones", "vac"), ("Hoy", "hoy")):
        if tipo == "libre":
            L.caja(p, x, yl + 2, 14, 14, SUPERFICIE, FILETE_FUERTE, 1, 4)
        elif tipo == "completo":
            L.caja(p, x, yl + 2, 14, 14, ELEVADO, FILETE, 1, 4)
        elif tipo == "vac":
            L.caja(p, x, yl + 2, 14, 14, INFO_SUAVE, INFO, 2, 4)
        else:
            L.caja(p, x, yl + 2, 14, 14, SUPERFICIE, ACENTO, 2, 4)
        w_t = medir(etiqueta, 13, "n")
        L.texto(p, x + 20, yl, w_t + 4, 18, etiqueta, 13, SECUNDARIO, tachado=(tipo == "completo"))
        x += 20 + w_t + 18
    yh = yl + 34
    L.eyebrow(p, 18, yh, 220, f"Horas del {etiqueta_con_mes(elegido)}")
    L.texto(p, 224, yh, 120, 16, "Duran 1 hora", 13, SECUNDARIO, alinea="right")
    horas = horas_del_dia(elegido)
    for i, (h, ocupada) in enumerate(horas):
        fila, col = divmod(i, 3)
        estado = "ocupada" if ocupada else ("seleccionada" if h == HORA_C2 else "libre")
        L.burbuja_hora(p, 16 + col * 113, yh + 26 + fila * 58, 102, 48, hh(h), estado)
    yb = yh + 26 + 2 * 58 + 8
    L.boton(p, 16, yb, 328, 52, f"Pedir cita · {etiqueta_corta(elegido)} · {hh(HORA_C2)}", "primario", tam=16)
    L.texto(p, 16, yb + 60, 328, 18, "Quedará pendiente hasta que el barbero la confirme", 13, SECUNDARIO, alinea="center")
    L.nav_cliente(p, 1)


def pantalla_C3(L: Lienzo, p: str):
    """Ajustes: tema claro/oscuro y cerrar sesión (cambiar @usuario es P2)."""
    L.barra_estado(p)
    L.cabecera(p, "Hola, @marta", "Ajustes")
    L.eyebrow(p, 18, 104, 200, "Cuenta")
    L.tarjeta(p, 16, 126, 328, 88)
    L.avatar(p, 30, 148, "M", 44)
    L.texto(p, 86, 146, 150, 24, "@marta", 17, TINTA, "s")
    L.texto(p, 86, 172, 160, 20, "+34 600 111 222", 14, SECUNDARIO)
    L.chip(p, 330, 158, "Aprobado", "positivo", derecha=True)
    L.eyebrow(p, 18, 238, 200, "Tema")
    L.tarjeta(p, 16, 260, 328, 96)
    # solo claro u oscuro (RNF-USA-08); seguir el tema del sistema no es un requisito
    L.segmentado(p, 28, 272, ["Claro", "Oscuro"], 0, ancho_total=304)
    L.texto(p, 30, 328, 300, 18, "Se guarda en este móvil", 13, SECUNDARIO)
    L.eyebrow(p, 18, 380, 200, "Sesión")
    L.boton(p, 16, 402, 328, 52, "Cerrar sesión", "secundario", tam=16, icono="salir")
    L.texto(p, 16, 612, 328, 18, "Clandestino · versión 1.0", 13, SECUNDARIO, alinea="center")
    L.nav_cliente(p, 2)


def _solicitud(L, p, y, usuario, fecha, hora_pedida, hora_final):
    L.tarjeta(p, 16, y, 328, 174)
    L.texto(p, 30, y + 12, 160, 24, f"@{usuario}", 17, TINTA, "s")
    L.chip(p, 330, y + 12, "Pendiente", "neutro", derecha=True)
    L.texto(p, 30, y + 38, 296, 20, f"Pidió {etiqueta_con_mes(fecha)} sobre las {hh(hora_pedida)}", 14, SECUNDARIO)
    L.texto(p, 30, y + 66, 120, 40, "Hora final", 14, SECUNDARIO, "s")
    L.selector(p, 190, y + 66, 140, 40, hh(hora_final), tam=16)
    L.boton(p, 30, y + 114, 120, 48, "Rechazar", "secundario", tam=15)
    L.boton(p, 158, y + 114, 172, 48, "Confirmar", "primario", tam=15)
    return y + 174


def pantalla_D1(L: Lienzo, p: str):
    """Solicitudes (UC08): confirmar (con hora final) o rechazar."""
    L.barra_estado(p)
    # GET /api/admin/citas?estado=pendiente: ordenadas por fecha y hora (RF-22)
    pendientes = sorted((c for c in CITAS if c[3] == "pendiente"), key=lambda c: (c[1], c[2]))
    L.cabecera(p, f"{len(pendientes)} pendientes", "Solicitudes", refrescar=True)
    y = 100
    finales = {"marta": 16, "lucas": 18, "pablo": 17}
    for usuario, fecha, hora, _ in pendientes:
        y = _solicitud(L, p, y, usuario, fecha, hora, finales[usuario]) + 8
    L.nav_admin(p, 0)


def pantalla_D2(L: Lienzo, p: str):
    """Agenda (UC09): semana/mes, citas confirmadas con Asistió / No vino / Cancelar."""
    dia = HOY
    L.barra_estado(p)
    L.cabecera(p, "Tu agenda", "Agenda", refrescar=True)
    L.segmentado(p, 16, 98, ["Semana", "Mes"], 0, ancho_op=84)
    # navegación de semana
    lunes = HOY - timedelta(days=HOY.weekday())
    domingo = lunes + timedelta(days=6)
    L.boton_circular(p, 16, 152, "chev_izq", 36, TINTA)
    L.texto(p, 60, 152, 240, 36,
            f"{lunes.day} – {domingo.day} {MESES_CORTO[domingo.month - 1]} {domingo.year}",
            16, TINTA, "b", alinea="center", esp=1.2)
    L.boton_circular(p, 308, 152, "chev_der", 36, TINTA)
    # tira de semana
    for i in range(7):
        d = lunes + timedelta(days=i)
        cx = 18 + i * 47
        activo = d == dia
        if activo:
            L.caja(p, cx, 198, 42, 66, ACENTO_SUAVE, ACENTO, 1.5, 14)
        else:
            L.caja(p, cx, 198, 42, 66, SUPERFICIE, FILETE, 1, 14)
        cerrado = estado_dia(d) == "cerrado"
        col_t = ACENTO_TINTA if activo else (APAGADO if cerrado else SECUNDARIO)
        col_n = ACENTO_TINTA if activo else (APAGADO if cerrado else TINTA)
        L.texto(p, cx, 203, 42, 16, DIAS_CORTO[d.weekday()], 13, col_t, "b", alinea="center")
        L.texto(p, cx, 219, 42, 26, str(d.day), 19, col_n, "s", alinea="center")
        # la agenda solo lista citas confirmadas (GET /api/admin/citas?estado=confirmada)
        if any(c[1] == d and c[3] == "confirmada" for c in CITAS):
            L.circulo(p, cx + 18, 250, 6, ACENTO)
    # citas confirmadas del día seleccionado, por hora. Las hechas y las no asistidas "salen de
    # la agenda" (UC09), así que @sergio (hecha a las 16:00) ya no aparece.
    del_dia = sorted((c for c in CITAS if c[1] == dia and c[3] == "confirmada"), key=lambda c: c[2])
    L.eyebrow(p, 18, 280, 230, f"{DIAS[dia.weekday()]} {dia.day} de {MESES[dia.month - 1]}")
    L.texto(p, 244, 280, 100, 16, f"{len(del_dia)} citas", 13, SECUNDARIO, alinea="right")
    y = 304
    for usuario, fecha, hora, _ in del_dia:
        # Asistió / No vino solo si la hora ya ha pasado (cita_futura en caso contrario);
        # Cancelar solo si NO ha pasado (cita_pasada en caso contrario): RN-16, RN-17.
        pasada = es_pasada(fecha, hora)
        L.tarjeta(p, 16, y, 328, 112)
        L.texto(p, 30, y + 10, 56, 28, hh(hora), 18, TINTA, "b")
        L.texto(p, 92, y + 10, 120, 28, f"@{usuario}", 16, TINTA, "s")
        L.chip(p, 330, y + 11, "Confirmada", "positivo", derecha=True)
        L.boton(p, 30, y + 50, 94, 48, "Asistió", "primario" if pasada else "apagado", tam=15)
        L.boton(p, 130, y + 50, 94, 48, "No vino", "secundario" if pasada else "apagado", tam=15)
        L.boton(p, 230, y + 50, 100, 48, "Cancelar", "apagado" if pasada else "secundario", tam=15)
        y += 120
    L.nav_admin(p, 1)


def pantalla_D3(L: Lienzo, p: str):
    """Horarios (UC10): plantilla semanal (interruptor, inicio, fin, cupo) y vacaciones."""
    L.barra_estado(p)
    L.cabecera(p, "Disponibilidad", "Horarios", guardar=True)
    L.eyebrow(p, 18, 100, 230, "Plantilla semanal")
    ytab = 122
    alto_fila = 40
    L.tarjeta(p, 16, ytab, 328, 28 + 7 * alto_fila + 6)
    columnas = ((156, 58), (220, 58), (284, 44))
    for txt, (cx, w) in zip(("INICIO", "FIN", "CUPO"), columnas):
        L.texto(p, cx, ytab + 6, w, 16, txt, 13, SECUNDARIO, "b", alinea="center", esp=1)
    for i in range(7):
        ini, fin, cupo, activo = HORARIO[i + 1]
        ry = ytab + 28 + i * alto_fila
        if i:
            L.separador(p, 28, ry, 304, FILETE)
        L.interruptor(p, 28, ry + 7, activo)
        L.texto(p, 82, ry + 4, 72, 32, DIAS[i], 15, TINTA if activo else SILENCIO, "s" if activo else "n")
        for (cx, w), valor in zip(columnas, (hh(ini), hh(fin), str(cupo))):
            L.caja(p, cx, ry + 4, w, 32, ELEVADO if activo else SUPERFICIE, FILETE, 1, 10)
            L.texto(p, cx, ry + 4, w, 32, valor, 15, TINTA if activo else SILENCIO,
                    "s" if activo else "n", alinea="center")
    y2 = ytab + 28 + 7 * alto_fila + 6 + 12
    L.eyebrow(p, 18, y2 + 8, 232, "Vacaciones y días extra")
    L.boton(p, 252, y2, 92, 32, "Añadir", "secundario", tam=14, icono="mas", radio=16)
    y3 = y2 + 40
    alto_exc = 48
    L.tarjeta(p, 16, y3, 328, 3 * alto_exc + 4)
    for i, (fecha, (tipo, ini, fin, cupo, nota)) in enumerate(sorted(EXCEPCIONES.items())):
        ry = y3 + 2 + i * alto_exc
        if i:
            L.separador(p, 28, ry, 304, FILETE)
        L.texto(p, 30, ry + 4, 200, 22, f"{DIAS[fecha.weekday()]} {fecha.day} de {MESES[fecha.month - 1]}", 15, TINTA, "s")
        detalle = nota if tipo == "cerrado" else f"{hh(ini)}–{hh(fin)} · {nota}"
        L.texto(p, 30, ry + 26, 200, 18, detalle, 13, SECUNDARIO)
        L.chip(p, 296, ry + 11, "Cerrado" if tipo == "cerrado" else "Abierto",
               "neutro" if tipo == "cerrado" else "positivo", derecha=True)
        L.icono(p, "cerrar", 304, ry + 13, 22, SECUNDARIO, 2)
    L.nav_admin(p, 2)


def pantalla_D4(L: Lienzo, p: str):
    """Clientes e invitaciones (UC06 + UC07): generar código, aprobar, bloquear."""
    L.barra_estado(p)
    L.cabecera(p, "Accesos", "Clientes", refrescar=True)
    # --- invitar: nota + Generar, y el último código generado (UC06 paso 4)
    L.eyebrow(p, 18, 98, 300, "Invitar a un cliente")
    L.tarjeta(p, 16, 116, 328, 124)
    L.caja(p, 30, 126, 190, 48, ELEVADO, FILETE_FUERTE, 1, 12)
    L.texto(p, 44, 126, 170, 48, "Nota: Sofía", 15, TINTA)
    L.boton(p, 228, 126, 102, 48, "Generar", "primario", tam=15, radio=12)
    L.caja(p, 30, 180, 300, 54, ELEVADO, None, 0, 12)
    L.texto(p, 44, 183, 180, 26, INVITACIONES[0][0], 20, TINTA, "b", esp=1.5)
    L.texto(p, 44, 210, 200, 18, "Caduca en 7 días · un solo uso", 13, SECUNDARIO)
    L.boton(p, 238, 187, 82, 40, "Copiar", "secundario", tam=14, icono="copiar", radio=12)
    # --- invitaciones: código, nota, estado y quién la usó (UC06 paso 5, RF-18).
    # "Caducada" no es un estado guardado: es una pendiente con caduca_en pasada (RN-22).
    L.eyebrow(p, 18, 248, 300, "Invitaciones")
    alto_inv = 44
    L.tarjeta(p, 16, 266, 328, len(INVITACIONES) * alto_inv + 4)
    tono_inv = {"pendiente": "neutro", "usada": "positivo", "caducada": "neutro"}
    for i, (codigo, nota, estado, detalle) in enumerate(INVITACIONES):
        ry = 268 + i * alto_inv
        if i:
            L.separador(p, 28, ry, 304, FILETE)
        L.texto(p, 30, ry + 3, 170, 20, codigo, 15, TINTA, "b", esp=1.2)
        L.texto(p, 30, ry + 22, 190, 18, f"{nota} · {detalle}", 13, SECUNDARIO)
        L.chip(p, 330, ry + 9, estado.capitalize(), tono_inv[estado], derecha=True)
    # --- clientes: aprobar o bloquear (UC07)
    L.eyebrow(p, 18, 410, 300, "Clientes · 9")
    clientes = [
        ("diego", "+34 611 222 333", "Pendiente", "neutro", ("Aprobar", "Bloquear")),
        ("marta", "+34 600 111 222", "Aprobado", "positivo", ("Bloquear",)),
        ("raul", "+34 622 333 444", "Bloqueado", "neutro", ("Aprobar",)),
    ]
    alto_cli = 72
    L.tarjeta(p, 16, 428, 328, len(clientes) * alto_cli)
    for i, (usuario, tel, estado, tono, acciones) in enumerate(clientes):
        ry = 428 + i * alto_cli
        if i:
            L.separador(p, 28, ry, 304, FILETE)
        L.texto(p, 30, ry + 6, 120, 22, f"@{usuario}", 17, TINTA, "s")
        L.texto(p, 30, ry + 29, 130, 16, tel, 13, SECUNDARIO)
        L.chip(p, 30, ry + 46, estado, tono, h=22)
        if len(acciones) == 2:
            L.boton(p, 168, ry + 16, 76, 40, acciones[0], "primario", tam=14, radio=12)
            L.boton(p, 250, ry + 16, 80, 40, acciones[1], "secundario", tam=14, radio=12)
        else:
            tipo = "primario" if acciones[0] == "Aprobar" else "secundario"
            L.boton(p, 250, ry + 16, 80, 40, acciones[0], tipo, tam=14, radio=12)
    L.nav_admin(p, 3)


PANTALLAS = {
    "A1": ("A1-login", pantalla_A1, "Entrar"),
    "A2": ("A2-registro-codigo", pantalla_A2, "Crear cuenta"),
    "A3": ("A3-espera", pantalla_A3, "Espera"),
    "A3B": ("A3b-bloqueado", pantalla_A3b, "Acceso bloqueado"),
    "C1": ("C1-mis-citas", pantalla_C1, "Mis citas"),
    "C2": ("C2-pedir-cita", pantalla_C2, "Pedir cita"),
    "C3": ("C3-ajustes", pantalla_C3, "Ajustes"),
    "D1": ("D1-solicitudes", pantalla_D1, "Solicitudes"),
    "D2": ("D2-agenda", pantalla_D2, "Agenda"),
    "D3": ("D3-horarios", pantalla_D3, "Horarios"),
    "D4": ("D4-clientes-invitaciones", pantalla_D4, "Clientes e invitaciones"),
}
RESUMEN = ["A1", "C1", "C2", "D1", "D2", "D3"]


def construir_pantalla(clave: str) -> Lienzo:
    nombre, funcion, _ = PANTALLAS[clave]
    L = Lienzo(nombre)
    p = L.telefono(0, 0)
    funcion(L, p)
    return L


def construir_resumen() -> Lienzo:
    """Hoja de visión general: 6 pantallas en dos filas de tres, cada una con su rótulo.

    Es una hoja para ver el conjunto (A4 en vertical, tres teléfonos por fila): a 15 cm de ancho
    el texto de las pantallas queda en unos 5 pt, así que para leer el detalle están los
    mockups individuales. Se ha reducido el hueco entre teléfonos para que cada uno gane
    ancho y los rótulos son más grandes.
    """
    L = Lienzo("mockups-resumen")
    margen, hueco_x, alto_rotulo = 24, 20, 40
    fila_h = 752 + 12 + alto_rotulo
    for i, clave in enumerate(RESUMEN):
        fila, col = divmod(i, 3)
        x = margen + col * (372 + hueco_x)
        y = margen + fila * (fila_h + 12)
        p = L.telefono(x, y)
        PANTALLAS[clave][1](L, p)
        rotulo = f"{clave} · {PANTALLAS[clave][2]}"
        L.texto("1", x, y + 752 + 10, 372, alto_rotulo - 8, rotulo, 26, TINTA, "b", alinea="center")
    return L


def exportar_png(drawio: Path, png: Path) -> bool:
    if not DRAWIO_EXE.exists():
        print(f"  (sin PNG: no encuentro {DRAWIO_EXE})")
        return False
    r = subprocess.run([str(DRAWIO_EXE), "-x", "-f", "png", "-s", "2", "-b", "10", "-o", str(png), str(drawio)],
                       capture_output=True, text=True, timeout=120)
    if r.returncode != 0 or not png.exists():
        print("  ERROR al exportar:", r.stdout.strip(), r.stderr.strip())
        return False
    return True


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pantallas", nargs="*", help="A1 A2 A3 A3B C1 C2 C3 D1 D2 D3 D4 RESUMEN (por defecto, todas)")
    ap.add_argument("--sin-png", action="store_true", help="solo escribe los .drawio")
    args = ap.parse_args(argv)
    incoherencias = verificar_coherencia()
    if incoherencias:
        print("LOS DATOS DE EJEMPLO NO SON COHERENTES:")
        for e in incoherencias:
            print("  -", e)
        return 2
    claves = [c.upper() for c in args.pantallas] or list(PANTALLAS) + ["RESUMEN"]
    avisos = []
    for clave in claves:
        if clave == "RESUMEN":
            L = construir_resumen()
        elif clave in PANTALLAS:
            L = construir_pantalla(clave)
        else:
            sys.exit(f"Pantalla desconocida: {clave}")
        ruta = SALIDA / f"{L.nombre}.drawio"
        L.guardar(ruta)
        print(f"{ruta.name}")
        avisos += L.avisos
        if not args.sin_png:
            exportar_png(ruta, ruta.with_suffix(".png"))
    if avisos:
        print("\nAVISOS DE MAQUETACIÓN:")
        for a in avisos:
            print("  -", a)
    return 1 if avisos else 0


if __name__ == "__main__":
    sys.exit(main())
