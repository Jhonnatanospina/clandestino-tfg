"""
Diagrama 01 · Arquitectura de tres capas de Clandestino.

Capa de presentación (app Android, MVVM) -> capa de negocio (API REST en Java)
-> capa de datos (MySQL). Los componentes salen de docs/PLAN-TFG-Clandestino-Android.md §3.
Disposición en serpiente: la app va de izquierda a derecha, la API de derecha a
izquierda, y así todas las flechas son rectas (ni codos ni cruces).
"""
from __future__ import annotations

from drawio_lib import (BLANCO, BORDE, FUENTE, NEUTRO_1, NEUTRO_2, ROJO, TEXTO, Diagrama,
                        ancho_texto, estilo)

CW = 152          # ancho de cada caja
G = 34            # hueco entre cajas
PAD = 16          # margen interior de las bandas
NCOL = 4
ANCHO_BANDA = NCOL * CW + (NCOL - 1) * G + 2 * PAD
CAB = 34          # alto de la cabecera de cada banda
ALTO_CAJA = 78
FS_T, FS_S = 15, 13   # tamaño del título y de las líneas de las cajas


def x_col(c: int) -> float:
    """x (relativa a la banda) de la columna c (0..3)."""
    return PAD + c * (CW + G)


def centro_col(c: int) -> float:
    return x_col(c) + CW / 2


def caja(d: Diagrama, parent: str, x: float, y: float, w: float, titulo: str,
         lineas: list[str], h: float = ALTO_CAJA, borde: str = BORDE, ancho_borde: float = 1.5,
         relleno: str = BLANCO) -> str:
    """Caja redondeada con un título en negrita y líneas de detalle."""
    assert ancho_texto(titulo, FS_T, True) + 16 <= w, titulo
    for ln in lineas:
        assert ancho_texto(ln, FS_S) + 16 <= w, ln
    html = f'<b style="font-size:{FS_T}px">{titulo}</b>' + "".join(
        f'<br><span style="font-size:{FS_S}px">{ln}</span>' for ln in lineas)
    return d.celda(
        html,
        estilo("rounded=1", "arcSize=8", "whiteSpace=wrap", "html=1", f"fillColor={relleno}",
               f"strokeColor={borde}", f"strokeWidth={ancho_borde}", f"fontColor={TEXTO}",
               f"fontFamily={FUENTE}", f"fontSize={FS_S}", "align=center", "verticalAlign=middle"),
        x, y, w, h, parent=parent)


def banda(d: Diagrama, y: float, h: float, titulo: str) -> str:
    return d.celda(
        titulo,
        estilo("swimlane", "html=1", "whiteSpace=wrap", "collapsible=0", f"startSize={CAB}",
               f"fillColor={ROJO}", f"swimlaneFillColor={NEUTRO_1}", f"strokeColor={BORDE}",
               "strokeWidth=1.5", f"fontColor={BLANCO}", "fontStyle=1", "fontSize=15",
               f"fontFamily={FUENTE}", "align=left", "spacingLeft=12", "rounded=0"),
        0, y, ANCHO_BANDA, h)


def flecha(d: Diagrama, origen: str, destino: str, color: str = TEXTO, grosor: float = 2,
           doble: bool = False, extra: str = "", valor: str = "", **kw) -> str:
    est = estilo("html=1", "edgeStyle=none", "rounded=0", "endArrow=block", "endFill=1",
                 "endSize=7", f"strokeColor={color}", f"strokeWidth={grosor}",
                 f"fontFamily={FUENTE}", "fontSize=13", f"fontColor={TEXTO}",
                 *(["startArrow=block", "startFill=1", "startSize=7"] if doble else []),
                 extra)
    return d.arista(origen, destino, est, valor=valor, **kw)


def construir() -> Diagrama:
    # --- Alturas -------------------------------------------------------------
    y1 = 0
    fila1 = CAB + 18                   # y de las cajas de la primera fila (relativa a la banda)
    fila2 = fila1 + ALTO_CAJA + 38     # segunda fila (Keystore / auth)
    alto_fila2 = 66
    h_banda = fila2 + alto_fila2 + 16
    hueco1 = 96                        # espacio entre banda 1 y banda 2 (lleva la etiqueta HTTP)
    y2 = y1 + h_banda + hueco1
    hueco2 = 74
    y3 = y2 + h_banda + hueco2
    h_banda3 = CAB + 18 + 92 + 16

    d = Diagrama("01-arquitectura", ANCHO_BANDA + 20, int(y3 + h_banda3 + 20))

    # =========================================================================
    # Banda 1 · App Android (MVVM)
    # =========================================================================
    b1 = banda(d, y1, h_banda, "Capa de presentación · App Android en el emulador (Java, XML, Material 3, MVVM)")
    vista = caja(d, b1, x_col(0), fila1, CW, "Vista", ["Activity y Fragment", "layouts XML"])
    vm = caja(d, b1, x_col(1), fila1, CW, "ViewModel", ["estado con LiveData", "resiste la rotación"])
    repo_app = caja(d, b1, x_col(2), fila1, CW, "Repositorio", ["única puerta a los", "datos de la app"])
    cliente = caja(d, b1, x_col(3), fila1, CW, "ClienteApi", ["HttpURLConnection", "en hilo secundario"])
    # Keystore: ocupa dos columnas (2 y 3 de la banda: índices 1 y 2) y se resalta en rojo
    w_ks = 2 * CW + G
    keystore = caja(d, b1, x_col(1), fila2, w_ks, "Android Keystore",
                    ["clave AES-GCM que cifra el JWT de la sesión,", "guardado en SharedPreferences"],
                    h=alto_fila2, borde=ROJO, ancho_borde=2.5, relleno=BLANCO)

    # Banda 1: flechas de izquierda a derecha (cada capa usa a la siguiente)
    sale_der = "exitX=1;exitY=0.5;entryX=0;entryY=0.5;"
    flecha(d, vista, vm, extra=sale_der)
    flecha(d, vm, repo_app, extra=sale_der)
    flecha(d, repo_app, cliente, extra=sale_der)
    # Repositorio <-> Keystore: guarda el JWT al iniciar sesión y lo lee en cada petición
    flecha(d, keystore, repo_app, doble=True,
           extra=f"exitX={(CW + G + CW / 2) / w_ks:.4f};exitY=0;entryX=0.5;entryY=1;"
                 "align=left;spacingLeft=14;labelBackgroundColor=none;",
           valor="guarda y lee el JWT")

    # =========================================================================
    # Banda 2 · API REST (de derecha a izquierda)
    # =========================================================================
    b2 = banda(d, y2, h_banda, "Capa de negocio · API REST en el PC (Java 21, HttpServer del JDK)")
    http = caja(d, b2, x_col(3), fila1, CW, "http", ["Enrutador y rutas", "pool de 8 hilos"])
    servicio = caja(d, b2, x_col(2), fila1, CW, "servicio", ["reglas de negocio:", "reserva y cupo"])
    repo_api = caja(d, b2, x_col(1), fila1, CW, "repositorio", ["JDBC con", "PreparedStatement"])
    w_auth = CW * 2 + G
    auth = caja(d, b2, x_col(2), fila2, w_auth, "seguridad",
                ["PBKDF2 y JWT (HS256) firmado con HMAC,", "filtro que valida el token en cada petición"],
                h=alto_fila2)

    sale_izq = "exitX=0;exitY=0.5;entryX=1;entryY=0.5;"
    flecha(d, http, servicio, extra=sale_izq)
    flecha(d, servicio, repo_api, extra=sale_izq)
    # http y servicio usan auth (flechas cortas hacia abajo)
    flecha(d, http, auth, extra=f"exitX=0.5;exitY=1;entryX={(CW + G + CW / 2) / w_auth:.4f};entryY=0;")
    flecha(d, servicio, auth, extra=f"exitX=0.5;exitY=1;entryX={(CW / 2) / w_auth:.4f};entryY=0;")

    # =========================================================================
    # Banda 3 · MySQL
    # =========================================================================
    b3 = banda(d, y3, h_banda3, "Capa de datos · MySQL 9.7 LTS")
    y_bd = CAB + 18
    bd = d.celda(
        f'<b style="font-size:{FS_T}px">clandestino</b><br><span style="font-size:{FS_S}px">'
        "base de datos local</span>",
        estilo("shape=cylinder3", "boundedLbl=1", "backgroundOutline=1", "size=12", "html=1",
               f"fillColor={BLANCO}", f"strokeColor={BORDE}", "strokeWidth=1.5",
               f"fontColor={TEXTO}", f"fontFamily={FUENTE}", f"fontSize={FS_S}"),
        x_col(1), y_bd, CW, 92, parent=b3)
    caja(d, b3, x_col(2), y_bd + 8, w_auth, "5 tablas",
         ["usuarios · invitaciones · citas", "horarios_semanales · excepciones_dia"], h=76)

    # =========================================================================
    # Enlaces entre capas (más gruesos: es lo que viaja por la red o por JDBC)
    # =========================================================================
    # App -> API: de ClienteApi (columna 4) a http (columna 4), recta
    cx = centro_col(3)
    y_cli_bajo = y1 + fila1 + ALTO_CAJA
    y_http_alto = y2 + fila1
    id_http_link = flecha(
        d, cliente, http, color=TEXTO, grosor=3, doble=True,
        extra="exitX=0.5;exitY=1;entryX=0.5;entryY=0;align=right;spacingRight=12;"
              "labelBackgroundColor=none;",
        valor="<b>HTTP + JSON</b><br>JWT en la cabecera Authorization<br>"
              "Emulador: http://10.0.2.2:8080",
        posicion=((y1 + h_banda + hueco1 / 2) - (y_cli_bajo + y_http_alto) / 2)
                 / ((y_http_alto - y_cli_bajo) / 2))

    # API -> MySQL: del repositorio (columna 2) al cilindro
    y_rep_bajo = y2 + fila1 + ALTO_CAJA
    y_bd_alto = y3 + y_bd
    flecha(d, repo_api, bd, color=TEXTO, grosor=3, doble=True,
           extra="exitX=0.5;exitY=1;entryX=0.5;entryY=0;align=left;spacingLeft=12;"
                 "labelBackgroundColor=none;",
           valor="<b>JDBC</b><br>consultas parametrizadas",
           posicion=((y2 + h_banda + hueco2 / 2) - (y_rep_bajo + y_bd_alto) / 2)
                    / ((y_bd_alto - y_rep_bajo) / 2))
    return d
