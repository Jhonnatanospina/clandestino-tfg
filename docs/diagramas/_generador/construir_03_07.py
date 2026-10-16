# -*- coding: utf-8 -*-
"""
Genera los .drawio de los diagramas 03 a 07 y los exporta a PNG.

Uso (desde esta carpeta):
    python construir_03_07.py            # todos
    python construir_03_07.py 07 03      # solo los indicados
Requiere draw.io de escritorio (ruta en dio.DRAWIO) y Pillow.
"""
import importlib
import os
import sys

import uml_dio as dio

AQUI = os.path.dirname(os.path.abspath(__file__))
SALIDA = os.path.dirname(AQUI)          # docs/diagramas

# numero -> modulos generadores (cada uno puede devolver varios diagramas)
MODULOS = {
    '03': ['gen_03'],
    '04': ['gen_04'],
    '05': ['gen_05'],
    '06': ['gen_06'],
    '07': ['gen_07'],
}


def main(args):
    sys.path.insert(0, AQUI)
    quiero = args or sorted(MODULOS)
    for num in quiero:
        for nombre_mod in MODULOS[num]:
            mod = importlib.import_module(nombre_mod)
            importlib.reload(mod)
            resultado = mod.construir()
            # un generador devuelve un Doc o una lista de (Doc, nombre)
            if isinstance(resultado, dio.Doc):
                resultado = [(resultado, resultado.nombre)]
            for doc, nombre in resultado:
                ruta = os.path.join(SALIDA, nombre + '.drawio')
                doc.guardar(ruta)
                png = os.path.join(SALIDA, nombre + '.png')
                dio.exporta(ruta, png)
                print('OK', nombre)


if __name__ == '__main__':
    main(sys.argv[1:])
