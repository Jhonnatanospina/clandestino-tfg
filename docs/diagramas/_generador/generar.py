"""
Genera los diagramas .drawio de docs/diagramas y los exporta a PNG con draw.io.

Uso (desde cualquier carpeta):
    python docs/diagramas/_generador/generar.py            # todos
    python docs/diagramas/_generador/generar.py 02         # solo el 02
"""
from __future__ import annotations

import sys
from pathlib import Path

from drawio_lib import exportar_png

SALIDA = Path(__file__).resolve().parent.parent


def main(filtro: list[str]) -> None:
    trabajos = []
    if not filtro or "01" in filtro:
        import diagrama_01_arquitectura as m
        trabajos.append(("01-arquitectura", m))
    if not filtro or "02" in filtro:
        import diagrama_02_er as m
        trabajos.append(("02-er", m))
    if not filtro or "08" in filtro:
        import diagrama_08_gantt as m
        trabajos.append(("08-gantt", m))

    for nombre, modulo in trabajos:
        drawio = SALIDA / f"{nombre}.drawio"
        png = SALIDA / f"{nombre}.png"
        modulo.construir().guardar(drawio)
        exportar_png(drawio, png)
        print(f"OK {drawio.name} + {png.name}")


if __name__ == "__main__":
    main(sys.argv[1:])
