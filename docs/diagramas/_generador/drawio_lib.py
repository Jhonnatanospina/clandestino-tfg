"""
Utilidades mínimas para generar ficheros .drawio (mxfile sin comprimir).

Se escribe el XML a mano con ElementTree, porque así el resultado es estable,
se puede versionar y draw.io lo abre y lo edita sin más. Cada diagrama del TFG
tiene su propio script que usa estas funciones.
"""
from __future__ import annotations

import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import ImageFont

# --- Paleta del TFG (marca de Clandestino) -----------------------------------
ROJO = "#DD0426"        # solo cabeceras y elementos destacados
TEXTO = "#121212"
NEUTRO_1 = "#F2F2F4"
NEUTRO_2 = "#E9E9EC"
BORDE = "#5A5A5E"
BLANCO = "#FFFFFF"

FUENTE = "Arial"

DRAWIO_EXE = r"C:\Users\jhony\AppData\Local\Programs\draw.io\draw.io.exe"

_FUENTES = {}


def ancho_texto(texto: str, px: int, negrita: bool = False) -> float:
    """Ancho aproximado de un texto en Arial, para dimensionar columnas."""
    clave = (px, negrita)
    if clave not in _FUENTES:
        fichero = "arialbd.ttf" if negrita else "arial.ttf"
        _FUENTES[clave] = ImageFont.truetype(rf"C:\Windows\Fonts\{fichero}", px)
    return _FUENTES[clave].getlength(texto)


def estilo(*partes, **clave_valor) -> str:
    """Compone un estilo de draw.io: estilo('swimlane', fillColor='#fff') -> 'swimlane;fillColor=#fff;'."""
    trozos = [p.rstrip(";") for p in partes if p]
    trozos += [f"{k}={v}" for k, v in clave_valor.items()]
    return ";".join(trozos) + ";"


class Diagrama:
    """Un diagrama de una sola página."""

    def __init__(self, nombre: str, ancho: int, alto: int):
        self.nombre = nombre
        self.ancho = ancho
        self.alto = alto
        self._contador = 1
        self.raiz = ET.Element("root")
        ET.SubElement(self.raiz, "mxCell", id="0")
        ET.SubElement(self.raiz, "mxCell", id="1", parent="0")

    # -- identificadores --------------------------------------------------
    def nuevo_id(self, prefijo: str = "c") -> str:
        self._contador += 1
        return f"{prefijo}{self._contador}"

    # -- celdas -----------------------------------------------------------
    def celda(self, valor: str, est: str, x: float, y: float, w: float, h: float,
              parent: str = "1", id: str | None = None) -> str:
        """Añade un vértice. Las coordenadas son relativas al padre."""
        id = id or self.nuevo_id()
        c = ET.SubElement(self.raiz, "mxCell", id=id, value=valor, style=est,
                          vertex="1", parent=parent)
        ET.SubElement(c, "mxGeometry", {"x": _n(x), "y": _n(y), "width": _n(w),
                                         "height": _n(h), "as": "geometry"})
        return id

    def arista(self, origen: str | None, destino: str | None, est: str, valor: str = "",
               puntos: list[tuple[float, float]] | None = None,
               origen_punto: tuple[float, float] | None = None,
               destino_punto: tuple[float, float] | None = None,
               desplazamiento: tuple[float, float] | None = None,
               posicion: float | None = None,
               id: str | None = None) -> str:
        """Añade una arista. Si origen/destino son None se usan los puntos absolutos."""
        id = id or self.nuevo_id("e")
        attrs = dict(id=id, value=valor, style=est, edge="1", parent="1")
        if origen:
            attrs["source"] = origen
        if destino:
            attrs["target"] = destino
        c = ET.SubElement(self.raiz, "mxCell", attrs)
        g = ET.SubElement(c, "mxGeometry", {"relative": "1", "as": "geometry"})
        if posicion is not None:
            g.set("x", _n(posicion))
        if origen_punto:
            ET.SubElement(g, "mxPoint", {"x": _n(origen_punto[0]), "y": _n(origen_punto[1]),
                                          "as": "sourcePoint"})
        if destino_punto:
            ET.SubElement(g, "mxPoint", {"x": _n(destino_punto[0]), "y": _n(destino_punto[1]),
                                          "as": "targetPoint"})
        if puntos:
            arr = ET.SubElement(g, "Array", {"as": "points"})
            for px, py in puntos:
                ET.SubElement(arr, "mxPoint", {"x": _n(px), "y": _n(py)})
        if desplazamiento:
            ET.SubElement(g, "mxPoint", {"x": _n(desplazamiento[0]), "y": _n(desplazamiento[1]),
                                          "as": "offset"})
        return id

    # -- salida -----------------------------------------------------------
    def a_xml(self) -> str:
        mxfile = ET.Element("mxfile", host="app.diagrams.net", agent="generador TFG Clandestino",
                            version="24.0.0")
        diag = ET.SubElement(mxfile, "diagram", id=self.nombre, name=self.nombre)
        modelo = ET.SubElement(diag, "mxGraphModel", dx="0", dy="0", grid="1", gridSize="10",
                               guides="1", tooltips="1", connect="1", arrows="1", fold="1",
                               page="1", pageScale="1", pageWidth=str(self.ancho),
                               pageHeight=str(self.alto), math="0", shadow="0",
                               background=BLANCO)
        modelo.append(self.raiz)
        ET.indent(mxfile, space="  ")
        return '<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(mxfile, encoding="unicode")

    def guardar(self, ruta: Path) -> None:
        Path(ruta).write_text(self.a_xml(), encoding="utf-8")


def _n(v: float) -> str:
    return str(int(v)) if float(v).is_integer() else f"{v:.1f}"


def exportar_png(drawio: Path, png: Path, escala: int = 2, borde: int = 10) -> None:
    """Exporta a PNG con la línea de órdenes de draw.io."""
    resultado = subprocess.run(
        [DRAWIO_EXE, "-x", "-f", "png", "-s", str(escala), "-b", str(borde),
         "-o", str(png), str(drawio)],
        capture_output=True, text=True, timeout=120)
    if resultado.returncode != 0 or not Path(png).exists():
        raise RuntimeError(f"Fallo al exportar {drawio.name}: {resultado.stderr or resultado.stdout}")
