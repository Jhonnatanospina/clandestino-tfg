# -*- coding: utf-8 -*-
"""04 · Diagramas de clases de la API.

   04a-clases-api-http-auth   paquetes http y seguridad, más el arranque (con los servicios y repositorios como referencia)
   04b-clases-api-negocio     paquetes servicio y repositorio (con seguridad y modelo como referencia)
   04-clases-api-modelo       clases del modelo

El diagrama único «04-clases-api» se sustituye por 04a y 04b: a 15 cm de ancho su letra salía a unos 5,8 pt.
Los nombres, atributos y métodos salen del código compilado (javap), no del diseño inicial.
"""
import html as _html
import re

from uml_dio import *

NOMBRE_HTTP = '04a-clases-api-http-auth'
NOMBRE_NEGOCIO = '04b-clases-api-negocio'
NOMBRE_MODELO = '04-clases-api-modelo'

RECORD = '«record»'


# ---------------------------------------------------------------------------
#   Utilidades de texto
# ---------------------------------------------------------------------------
def texto_plano(t):
    return _html.unescape(re.sub(r'<[^>]+>', '', t)).replace('\n', ' ')


def n_lineas(texto, ancho, fs=13):
    """Líneas que ocupa un texto al partirlo por palabras en un ancho dado (con un 4 % de margen)."""
    n, actual = 1, ''
    for p in texto.split():
        prueba = (actual + ' ' + p).strip()
        if actual and tw(prueba, fs) * 1.04 > ancho:
            n += 1
            actual = p
        else:
            actual = prueba
    return n


# ---------------------------------------------------------------------------
#   Elementos de maqueta
# ---------------------------------------------------------------------------
class Spec:
    """Clase por dibujar: miembros y tamaño calculado de antemano.
    ancho: si se da, la clase no ocupa toda la columna y se pega a su borde derecho (deja un pasillo
    libre a la izquierda para una flecha)."""

    def __init__(self, nombre, attrs=(), metodos=(), ancho=None, **kw):
        self.nombre, self.attrs, self.metodos, self.kw = nombre, list(attrs), list(metodos), kw
        self.w, self.h = tam_clase(nombre, attrs, metodos, **kw)
        self.ancho = max(ancho, self.w) if ancho else None
        if self.ancho:
            self.w = self.ancho
        self.caja = None
        self.abajo = False       # True: se alinea con el borde inferior de su fila

    def dibuja(self, doc, x, y, w, padre):
        if self.ancho:
            x, w = x + w - self.ancho, self.ancho
        self.caja = clase(doc, self.nombre, self.attrs, self.metodos, x=x, y=y, w=w, padre=padre, **self.kw)
        return self.caja


class NotaSpec:
    """Nota UML. Si no se da el alto (hh), se calcula a partir del texto y del ancho de la columna.
    span: columnas que ocupa; estira: ocupa todo el alto de su fila."""

    def __init__(self, html_texto, w=0, hh=None, fs=13, span=1, estira=False):
        self.html, self.w, self.h, self.fs = html_texto, w, hh, fs
        self.fija = hh is not None
        self.span, self.estira = span, estira
        self.caja = None
        self.abajo = False

    def ajusta(self, ancho):
        if not self.fija:
            self.h = n_lineas(texto_plano(self.html), ancho - 20, self.fs) * 16 + 18

    def dibuja(self, doc, x, y, w, padre):
        self.caja = nota(doc, self.html, x, y, w, self.h, fs=self.fs, padre=padre)
        return self.caja


def _gaps(gap_col, ncol):
    return list(gap_col) if isinstance(gap_col, (list, tuple)) else [gap_col] * (ncol - 1)


def _anchos(bandas, gap_col, pad):
    ncol = 1 + max(c for _, filas in bandas for f in filas for c in f)
    gaps = _gaps(gap_col, ncol)
    cw = [0] * ncol
    for _, filas in bandas:
        for f in filas:
            for c, s in f.items():
                if getattr(s, 'span', 1) == 1:
                    cw[c] = max(cw[c], s.w)
    return cw, gaps, sum(cw) + sum(gaps) + 2 * pad


def maqueta(doc, bandas, x0, y0, gap_col=24, gap_fila=34, pad=16, gap_banda=40, ancho_min_col=None):
    """bandas: lista de (nombre, filas); fila = {columna: spec}. Las columnas comparten ancho en todas las bandas.
    gap_col: separación entre columnas (un número, o una lista con una separación por hueco).
    Devuelve (dict nombre_banda -> Caja, x de cada columna, ancho de cada columna, y final)."""
    cw, gaps, ancho_banda = _anchos(bandas, gap_col, pad)
    ncol = len(cw)
    cx = [x0 + pad]
    for i in range(1, ncol):
        cx.append(cx[-1] + cw[i - 1] + gaps[i - 1])

    def ancho_de(c, span):
        return sum(cw[c:c + span]) + sum(gaps[c:c + span - 1])

    res = {}
    y = y0
    for nombre, filas in bandas:
        for f in filas:
            for c, s in f.items():
                if hasattr(s, 'ajusta'):
                    s.ajusta(ancho_de(c, getattr(s, 'span', 1)))
        alturas = [max(s.h for s in f.values()) for f in filas]
        alto = 30 + pad + sum(alturas) + gap_fila * (len(filas) - 1) + pad
        pk = paquete(doc, nombre, x0, y, ancho_banda, alto)
        yy = y + 30 + pad
        for f, ah in zip(filas, alturas):
            for c, s in f.items():
                if getattr(s, 'estira', False):
                    s.h = ah
                yd = yy + ah - s.h if getattr(s, 'abajo', False) else yy
                s.dibuja(doc, cx[c], yd, ancho_de(c, getattr(s, 'span', 1)), pk.id)
            yy += ah + gap_fila
        res[nombre] = pk
        y += alto + gap_banda
    return res, cx, cw, y


def lado_der(c, yabs):
    return (1, (yabs - c.y) / c.h)


def lado_izq(c, yabs):
    return (0, (yabs - c.y) / c.h)


def en_x(c, xabs):
    """Fracción horizontal de una caja para que la flecha salga o entre justo en xabs."""
    return (xabs - c.x) / c.w


def arista_xy(doc, o, d, st, pts, sal, ent, etiqueta=None, seg=None, frac=0.5, dx=0, dy=0, fs=12):
    P = [o.pt(*sal)] + list(pts) + [d.pt(*ent)]
    e = doc.arista(o, d, st, puntos=pts, salida=sal, entrada=ent)
    if etiqueta:
        k = (len(P) - 2) if seg is None else seg
        doc.etiqueta(e, etiqueta, x=x_en_poligonal(P, k, frac), dx=dx, dy=dy, fs=fs)
    return e


def etiqueta_en_y(doc, e, p0, p1, y, texto, dx=0, fs=12):
    """Etiqueta en la altura y de una arista recta vertical que va de p0 a p1."""
    t = (y - p0[1]) / (p1[1] - p0[1])
    return doc.etiqueta(e, texto, x=round(2 * t - 1, 4), dx=dx, fs=fs)


def paquete_texto(doc, nombre, x, y, w, texto, fs=13):
    """Paquete de referencia: solo un texto que remite a otro diagrama o a otro paquete."""
    lin = n_lineas(texto_plano(texto), w - 30, fs)
    cuerpo = lin * 17 + 18
    pk = paquete(doc, nombre, x, y, w, 30 + cuerpo)
    doc.vertice(texto, x + 10, y + 34, w - 20, cuerpo - 8,
                estilo(text=None, html=1, whiteSpace='wrap', align='left', verticalAlign='middle', fontSize=fs,
                       fontFamily=FUENTE, fontColor=TINTA, spacingLeft=4), padre=pk.id)
    return pk


# ---------------------------------------------------------------------------
#   04a · http y seguridad
# ---------------------------------------------------------------------------
def construir_http_auth():
    doc = Doc(NOMBRE_HTTP)

    # paquete http. En las clases Rutas, (…) son los parámetros (intercambio, usuario) de Manejador.
    rutas_auth = Spec('RutasAutenticacion', [],
                      ['+ registrar(enrutador)', '- registro(…)', '- login(…)', '- yo(…)', '- salud(…)'])
    rutas_adm = Spec('RutasAdmin', [],
                     ['+ registrar(enrutador)', '- listarCitas(…)', '- confirmar(…)', '- rechazar(…)', '- cancelar(…)',
                      '- asistencia(…)', '- horarios(…)', '- guardarHorarios(…)', '- excepciones(…)',
                      '- crearExcepciones(…)', '- borrarExcepcion(…)', '- clientes(…)', '- aprobar(…)',
                      '- bloquear(…)', '- invitaciones(…)', '- generarInvitacion(…)'])
    rutas_cli = Spec('RutasCliente', [],
                     ['+ registrar(enrutador)', '- dias(…)', '- huecos(…)', '- pedirCita(…)', '- misCitas(…)',
                      '- cancelar(…)'])
    servidor = Spec('ServidorApi', ['- servidor: HttpServer', '- hilos: ExecutorService'],
                    ['+ iniciar(host, puerto, enrutador)', '+ parar()', '+ puerto(): int'])
    router = Spec('Enrutador', ['- rutas: List<Ruta>', '- filtro: FiltroAutenticacion'],
                  ['+ registrar(metodo, patron, acceso, manejador)', '+ despachar(intercambio)',
                   '- comprobarAcceso(acceso, intercambio): Usuario', '+ idDeRuta(intercambio): long'])
    manejador = Spec('Manejador', [], ['+ atender(intercambio, usuario)'], tipo='interfaz')
    manejador.abajo = True
    acceso = Spec('Acceso', ['PUBLICO', 'CUALQUIER_SESION', 'CLIENTE', 'ADMIN'], [], tipo='enum', ancho=190)
    json = Spec('ConversorJson', [],
                ['+ leerCuerpo(intercambio): JSONObject', '+ responder(intercambio, estado, json)',
                 '+ responderError(intercambio, error)', '+ citaParaCliente(cita): JSONObject',
                 '+ citaParaAdmin(cita): JSONObject'])
    n_router = NotaSpec('El <b>Enrutador</b> parte la ruta en trozos y busca la que encaja (404 o 405). Si la ruta no '
                        'es pública, el filtro identifica al usuario y luego se comprueban su estado y su rol '
                        '(401 o 403). Cualquier <b>ErrorNegocio</b> que salte acaba en un JSON {error, mensaje}.')

    # paquete seguridad
    contra = Spec('Contrasenas', ['- iteraciones: int'],
                  ['+ hashear(contrasena): String', '+ verificar(contrasena, hashGuardado): boolean'])
    filtro = Spec('FiltroAutenticacion',
                  ['- tokenJwt: TokenJwt', '- baseDatos: BaseDatos', '- usuarios: RepositorioUsuarios'],
                  ['+ autenticar(cabeceraAuthorization): Usuario'])
    jwt = Spec('TokenJwt', ['- clave: byte[]', '- reloj: Reloj', '- duracion: Duration'],
               ['+ emitir(usuarioId): String', '+ validar(token): long'])
    limit = Spec('LimitadorIntentos', ['- maximoFallos: int', '- ventana: Duration',
                                       '- fallosPorClave: Map<String, Deque<Instant>>'],
                 ['+ permitir(clave): boolean', '+ segundosDeEspera(clave): long', '+ registrarFallo(clave)',
                  '+ reiniciar(clave)'])
    n_reloj = NotaSpec('<b>TokenJwt</b> y <b>LimitadorIntentos</b> reciben el <b>Reloj</b> del paquete servicio. '
                       'En los tests se les pasa uno parado y se puede probar la caducidad sin esperar.')

    # clases que se detallan en 04b
    usu_srv = Spec('ServicioUsuarios')
    cita_srv = Spec('ServicioCitas')
    hor_srv = Spec('ServicioHorarios')
    usu_rep = Spec('RepositorioUsuarios', tipo='interfaz')
    bd = Spec('BaseDatos')
    n_filtro = NotaSpec('El filtro no se fía del token. En cada petición vuelve a leer el <b>rol</b> y el '
                        '<b>estado</b> del usuario en la base de datos, así que un cliente bloqueado deja de entrar '
                        'al momento. Detalle en 04b.')
    n_yo = NotaSpec('<b>GET /api/yo</b> y <b>GET /api/salud</b> no pasan por ningún servicio. '
                    '<b>RutasAutenticacion</b> los resuelve sola: el primero devuelve el usuario que ya cargó el filtro '
                    'y el segundo comprueba que la base de datos responde. También es la que aplica el límite de '
                    'intentos, y solo en login y registro. En las clases Rutas, <b>(…)</b> abrevia los parámetros '
                    '(intercambio, usuario) que pide <b>Manejador</b>. Los servicios se detallan en 04b.', span=3)

    # arranque (paquete raíz)
    app = Spec('Aplicacion', [], ['+ main(args)', '+ montar(baseDatos, secretoJwt, reloj): Enrutador'])
    conf = Spec('Configuracion', ['- valores: Map<String, String>'],
                ['+ cargar(): Configuracion', '+ dbHost(): String', '+ dbPuerto(): int', '+ dbNombre(): String',
                 '+ dbUsuario(): String', '+ dbContrasena(): String', '+ secretoJwt(): String',
                 '+ apiHost(): String', '+ apiPuerto(): int'])
    n_montar = NotaSpec('<b>Aplicacion.montar()</b> crea los repositorios, los servicios y las rutas y los conecta a '
                        'mano, sin inyección automática. El test de punta a punta monta la API igual, pero con un '
                        'reloj fijo. <b>Configuracion</b> lee el fichero .env. <b>CrearAdmin</b> (paquete '
                        'herramientas) es un programa aparte que da de alta al barbero.')

    bandas = [
        ('repositorio', [{0: n_filtro, 1: usu_rep, 2: bd}]),
        ('seguridad', [{0: contra, 1: filtro, 2: jwt}, {0: limit, 2: n_reloj}]),
        ('http', [{0: n_router, 1: acceso, 2: json},
                  {0: servidor, 1: router, 2: manejador},
                  {0: rutas_auth, 1: rutas_adm, 2: rutas_cli}]),
        ('servicio', [{0: usu_srv, 1: cita_srv, 2: hor_srv}, {0: n_yo}]),
        ('api (arranque)', [{0: conf, 1: app, 2: n_montar}]),
    ]
    X0 = 44                        # margen a la izquierda para la flecha RutasAutenticacion -> LimitadorIntentos
    bm, cx, cw, yfin = maqueta(doc, bandas, X0, 20, gap_col=[24, 60], gap_fila=30, pad=14, gap_banda=40)
    pk_rep, pk_seg, pk_http, pk_srv = bm['repositorio'], bm['seguridad'], bm['http'], bm['servicio']
    R, F, J, A_ = router.caja, filtro.caja, json.caja, acceso.caja
    assert A_.x > R.x + 0.15 * R.w + 30, 'Acceso tapa la flecha Enrutador -> FiltroAutenticacion'

    # ---- http
    yh = servidor.caja.y + 14
    doc.arista(servidor.caja, R, COMP, salida=lado_der(servidor.caja, yh), entrada=lado_izq(R, yh))
    M = manejador.caja
    ym = M.y + 14
    e = doc.arista(R, M, AGRE, salida=lado_der(R, ym), entrada=lado_izq(M, ym))
    doc.etiqueta(e, '0..*', x=0.2, dy=-13, fs=12)
    # el Enrutador guarda el Acceso de cada ruta
    doc.arista(R, A_, ASOC, salida=(en_x(R, A_.cx), 0), entrada=(0.5, 1))
    # el Enrutador usa ConversorJson para responder los errores
    yj = R.y + 14
    arista_xy(doc, R, J, DEP, [(J.cx, yj)], lado_der(R, yj), (0.5, 1), '«usa»', seg=1, frac=0.5)
    # las clases Rutas registran sus manejadores en el Enrutador
    ymid = (R.b + rutas_adm.caja.y) / 2
    e = doc.arista(rutas_adm.caja, R, DEP, salida=(0.5, 0), entrada=(0.5, 1))
    doc.etiqueta(e, '«registra en»', x=0, fs=12)
    for r, fx in ((rutas_auth, 0.2), (rutas_cli, 0.8)):
        doc.arista(r.caja, R, DEP, puntos=[(r.caja.cx, ymid), (R.x + fx * R.w, ymid)], salida=(0.5, 0), entrada=(fx, 1))

    # ---- http -> seguridad
    doc.arista(R, F, ASOC, salida=(0.15, 0), entrada=(0.15, 1))
    L, RA = limit.caja, rutas_auth.caja
    xm = X0 - 20
    ya, yl = RA.y + 14, L.y + 14
    doc.arista(RA, L, ASOC, puntos=[(xm, ya), (xm, yl)], salida=lado_izq(RA, ya), entrada=lado_izq(L, yl))

    # ---- seguridad
    yf = F.y + 14
    doc.arista(F, jwt.caja, ASOC, salida=lado_der(F, yf), entrada=lado_izq(jwt.caja, yf))
    doc.arista(F, usu_rep.caja, ASOC, salida=(0.5, 0), entrada=(0.5, 1))
    B = bd.caja
    yg = (pk_rep.b + pk_seg.y) / 2
    doc.arista(F, B, ASOC, puntos=[(F.x + 0.9 * F.w, yg), (B.x + 0.2 * B.w, yg)], salida=(0.9, 0), entrada=(0.2, 1))

    # ---- http -> servicio (cada clase Rutas guarda los servicios de sus endpoints)
    gb, gt = pk_http.b, pk_srv.y
    ya, yb, yc = gb + 20, gb + 30, gb + 10
    A, D, Lc = rutas_auth.caja, rutas_adm.caja, rutas_cli.caja
    U, C, H = usu_srv.caja, cita_srv.caja, hor_srv.caja
    doc.arista(A, U, ASOC, salida=(0.6, 1), entrada=(0.6, 0))                                    # Auth -> Usuarios
    doc.arista(D, U, ASOC, puntos=[(D.x + 0.1 * D.w, ya), (U.x + 0.9 * U.w, ya)],
               salida=(0.1, 1), entrada=(0.9, 0))                                                # Admin -> Usuarios
    doc.arista(D, C, ASOC, salida=(0.5, 1), entrada=(0.5, 0))                                    # Admin -> Citas
    doc.arista(D, H, ASOC, puntos=[(D.x + 0.9 * D.w, yb), (H.x + 0.1 * H.w, yb)],
               salida=(0.9, 1), entrada=(0.1, 0))                                                # Admin -> Horarios
    doc.arista(Lc, H, ASOC, salida=(0.5, 1), entrada=(0.5, 0))                                   # Cliente -> Horarios
    doc.arista(Lc, C, ASOC, puntos=[(Lc.x + 0.2 * Lc.w, yc), (C.x + 0.8 * C.w, yc)],
               salida=(0.2, 1), entrada=(0.8, 0))                                                # Cliente -> Citas

    # ---- arranque
    P, K = app.caja, conf.caja
    yp = P.y + 14
    e = doc.arista(P, K, DEP, salida=lado_izq(P, yp), entrada=lado_der(K, yp))
    doc.etiqueta(e, '«usa»', x=0, dy=-13, fs=12)
    return doc


# ---------------------------------------------------------------------------
#   04b · servicio y repositorio
# ---------------------------------------------------------------------------
def construir_negocio():
    doc = Doc(NOMBRE_NEGOCIO)

    # paquete servicio: records que devuelven los servicios (clases anidadas, sin flecha)
    sesion = Spec('Sesion', ['- token: String', '- usuario: Usuario'], [], estereotipo=RECORD,
                  subtitulo='dentro de ServicioUsuarios')
    calendario = Spec('Calendario', ['- horizonteDesde: LocalDate', '- horizonteHasta: LocalDate',
                                     '- dias: List<DiaDisponible>'], [], estereotipo=RECORD,
                      subtitulo='dentro de ServicioHorarios')
    huecos = Spec('HuecosDelDia', ['- fecha: LocalDate', '- estado: EstadoDia', '- huecos: List<Hueco>'], [],
                  estereotipo=RECORD, subtitulo='dentro de ServicioHorarios')

    error = Spec('ErrorNegocio', ['- codigo: String', '- estadoHttp: int', '- cabeceras: Map<String, String>'],
                 ['+ conCabecera(nombre, valor): ErrorNegocio', '+ datosInvalidos(mensaje)', '+ noEncontrado(mensaje)',
                  '+ estadoInvalido(mensaje)'], subtitulo='extends RuntimeException')
    n_sync = NotaSpec('<b>ServicioCitas</b> reserva dentro de un <i>synchronized</i> por fecha y en una sola '
                      'transacción, así que dos clientes no pueden quedarse a la vez con el último hueco del día.',
                      estira=True)
    reloj = Spec('Reloj', ['- reloj: Clock'], ['+ deMadrid(): Reloj', '+ ahora(): LocalDateTime', '+ hoy(): LocalDate',
                                               '+ instante(): Instant'], subtitulo='zona Europe/Madrid')

    usu_srv = Spec('ServicioUsuarios',
                   ['- baseDatos: BaseDatos', '- usuarios: RepositorioUsuarios', '- contrasenas: Contrasenas',
                    '- tokenJwt: TokenJwt', '- reloj: Reloj'],
                   ['+ registrar(codigo, usuario, telefono, contrasena): Sesion',
                    '+ iniciarSesion(usuario, contrasena): Sesion', '+ clientes(estado): List<Usuario>',
                    '+ aprobar(clienteId): Usuario', '+ bloquear(clienteId): Usuario',
                    '+ invitaciones(): List<Invitacion>', '+ generarInvitacion(adminId, nota): Invitacion'])
    cita_srv = Spec('ServicioCitas',
                    ['- baseDatos: BaseDatos', '- citas: RepositorioCitas', '- usuarios: RepositorioUsuarios',
                     '- servicioHorarios: ServicioHorarios', '- reloj: Reloj', '- cerrojos: Map<LocalDate, Object>'],
                    ['+ pedir(clienteId, fecha, hora): Cita', '+ misCitas(clienteId): List<Cita>',
                     '+ cancelarPorCliente(clienteId, citaId): Cita', '+ listar(estados, desde, hasta): List<Cita>',
                     '+ confirmar(citaId, horaFinal): Cita', '+ rechazar(citaId): Cita',
                     '+ cancelarPorAdmin(citaId): Cita', '+ marcarAsistencia(citaId, vino, propina): Cita'])
    hor_srv = Spec('ServicioHorarios',
                   ['- baseDatos: BaseDatos', '- horarios: RepositorioHorarios', '- citas: RepositorioCitas',
                    '- reloj: Reloj'],
                   ['+ estaEnHorizonte(fecha): boolean', '+ tramoDe(conexion, fecha): Optional<Tramo>',
                    '+ diasDelRango(desde, hasta): Calendario', '+ huecos(fecha): HuecosDelDia',
                    '+ horarios(): List<HorarioSemanal>', '+ guardarHorarios(nuevos)', '+ excepciones(desde)',
                    '+ crearExcepciones(fechas, plantilla)', '+ borrarExcepcion(id)'])

    # paquete repositorio (sin la Connection ni el tipo devuelto, para que quepa)
    bd = Spec('BaseDatos', ['- url: String', '- usuario: String', '- contrasena: String'],
              ['+ conexion(): Connection', '+ consultar(trabajo): T', '+ enTransaccion(trabajo): T',
               '+ responde(): boolean', '+ esDuplicado(e): boolean'])
    trabajo = Spec('Trabajo<T>', [], ['+ hacer(conexion): T'], tipo='interfaz', subtitulo='dentro de BaseDatos')
    usu_rep = Spec('RepositorioUsuarios', [],
                   ['+ buscarPorId(id)', '+ buscarPorUsuario(usuario)', '+ insertar(usuario)',
                    '+ cambiarEstado(id, estadoAnterior, estadoNuevo, aprobadoEn)', '+ existeAdmin()',
                    '+ listarClientes(estado)', '+ buscarInvitacionParaUsar(codigo)', '+ insertarInvitacion(invitacion)',
                    '+ marcarUsada(invitacionId, usuarioId)', '+ listarInvitaciones()', '+ buscarInvitacionPorId(id)'],
                   tipo='interfaz')
    cita_rep = Spec('RepositorioCitas', [],
                    ['+ insertar(cita)', '+ buscarPorId(id)', '+ contarActivas(fecha)', '+ contarActivasEntre(desde, hasta)',
                     '+ horasOcupadas(fecha)', '+ listarPorCliente(clienteId, desde)', '+ listar(estados, desde, hasta)',
                     '+ actualizar(cita, estadoAnterior)'], tipo='interfaz')
    hor_rep = Spec('RepositorioHorarios', [],
                   ['+ semanal()', '+ guardarSemanal(horario)', '+ excepcion(fecha)', '+ excepciones(desde, hasta)',
                    '+ guardarExcepcion(excepcion)', '+ borrarExcepcion(id)'], tipo='interfaz')
    usu_jdbc = Spec('RepositorioUsuariosJdbc')
    cita_jdbc = Spec('RepositorioCitasJdbc')
    hor_jdbc = Spec('RepositorioHorariosJdbc')
    n_rep = NotaSpec('Todos los métodos de los repositorios reciben primero la <i>Connection</i> abierta, que aquí se '
                     'omite para que quepa. <b>cambiarEstado</b> y <b>actualizar</b> solo tocan la fila si sigue en el '
                     'estado anterior, y así dos peticiones a la vez no se pisan. Si MySQL falla, <b>BaseDatos</b> '
                     'lanza <b>ErrorBaseDatos</b> y el Enrutador responde con un 500.', estira=True)

    bandas = [
        ('servicio', [{0: sesion, 1: calendario, 2: huecos},
                      {0: error, 1: n_sync, 2: reloj},
                      {0: usu_srv, 1: cita_srv, 2: hor_srv}]),
        ('repositorio', [{0: usu_rep, 1: cita_rep, 2: hor_rep},
                         {0: usu_jdbc, 1: cita_jdbc, 2: hor_jdbc},
                         {0: bd, 1: trabajo, 2: n_rep}]),
    ]
    GAP = dict(gap_col=[50, 20], gap_fila=34, pad=14, gap_banda=40)
    X0 = 20

    # referencias a otros diagramas: seguridad arriba (solo cabecera), modelo abajo
    y_srv = 20 + 30 + GAP['gap_banda']
    bm, cx, cw, yfin = maqueta(doc, bandas, X0, y_srv, **GAP)
    pk_srv, pk_rep = bm['servicio'], bm['repositorio']
    pk_seg = paquete(doc, 'seguridad · Contrasenas y TokenJwt (detalle en 04a)', X0, 20, pk_srv.w, 30)
    pk_mod = paquete_texto(
        doc, 'modelo', X0, yfin, pk_srv.w,
        'Entidades: <b>Usuario</b>, <b>Invitacion</b>, <b>Cita</b>, <b>HorarioSemanal</b> y <b>ExcepcionDia</b>. '
        'Valores que se calculan y no se guardan: <b>Tramo</b>, <b>DiaDisponible</b> y <b>Hueco</b>. '
        'Enumeraciones: <b>Rol</b>, <b>EstadoUsuario</b>, <b>EstadoInvitacion</b>, <b>EstadoCita</b>, '
        '<b>TipoExcepcion</b> y <b>EstadoDia</b>. Las usan todas las capas y el detalle está en el diagrama del modelo.')

    U, C, H = usu_srv.caja, cita_srv.caja, hor_srv.caja
    UR, CR, HR = usu_rep.caja, cita_rep.caja, hor_rep.caja
    K = reloj.caja

    # servicio -> servicio y reloj
    ys = C.y + 14
    doc.arista(C, H, ASOC, salida=lado_der(C, ys), entrada=lado_izq(H, ys))
    fila_reloj = max(error.caja.b, n_sync.caja.b, K.b)
    yk2, yk = fila_reloj + 11, fila_reloj + 23          # dos pasillos en el hueco entre filas
    doc.arista(U, K, ASOC, puntos=[(U.x + 0.85 * U.w, yk2), (K.x + 0.1 * K.w, yk2)], salida=(0.85, 0), entrada=(0.1, 1))
    doc.arista(C, K, ASOC, puntos=[(C.x + 0.85 * C.w, yk), (K.x + 0.25 * K.w, yk)], salida=(0.85, 0), entrada=(0.25, 1))
    doc.arista(H, K, ASOC, salida=(0.8, 0), entrada=(0.8, 1))
    # servicio -> repositorio
    doc.arista(U, UR, ASOC, salida=(0.5, 1), entrada=(0.5, 0))
    doc.arista(C, CR, ASOC, salida=(0.4, 1), entrada=(0.4, 0))
    doc.arista(H, HR, ASOC, salida=(0.7, 1), entrada=(0.7, 0))
    ygap = (pk_srv.b + pk_rep.y) / 2
    arista_xy(doc, H, CR, ASOC, [(H.x + 0.2 * H.w, ygap), (CR.x + 0.8 * CR.w, ygap)], (0.2, 1), (0.8, 0))
    arista_xy(doc, C, UR, ASOC, [(C.x + 0.12 * C.w, ygap), (UR.x + 0.85 * UR.w, ygap)], (0.12, 1), (0.85, 0))
    # realizaciones
    for jd, rp in ((usu_jdbc, usu_rep), (cita_jdbc, cita_rep), (hor_jdbc, hor_rep)):
        doc.arista(jd.caja, rp.caja, REAL, salida=(0.5, 0), entrada=(0.5, 1))
    # BaseDatos ejecuta los Trabajo que le pasan los servicios
    B, T = bd.caja, trabajo.caja
    yb = T.y + 14
    e = doc.arista(B, T, DEP, salida=lado_der(B, yb), entrada=lado_izq(T, yb))
    doc.etiqueta(e, '«usa»', x=0, dy=-13, fs=12)

    # dependencias entre paquetes
    xd = pk_srv.x + pk_srv.w - 70
    e = doc.arista(None, None, DEP, sp=(xd, pk_srv.y), tp=(xd, pk_seg.b))
    doc.etiqueta(e, '«usa»', x=0, dx=34, fs=12, fondo=False)
    xr = pk_rep.x + pk_rep.w / 2
    e = doc.arista(None, None, DEP, sp=(xr, pk_rep.b), tp=(xr, pk_mod.y))
    doc.etiqueta(e, '«usa»', x=0, dx=34, fs=12, fondo=False)
    return doc


# ---------------------------------------------------------------------------
#   04 · modelo
# ---------------------------------------------------------------------------
def construir_modelo():
    doc = Doc(NOMBRE_MODELO)
    # ---- entidades (todas son records: inmutables, los cambios devuelven una copia) ----
    inv = Spec('Invitacion', ['- id: long', '- codigo: String', '- nota: String', '- estado: EstadoInvitacion',
                              '- caducaEn: LocalDateTime', '- creadaPor: long', '- usadaPor: Long',
                              '- usadaPorUsuario: String', '- creadaEn: LocalDateTime'],
               ['+ estaCaducada(ahora): boolean'], estereotipo=RECORD)
    usu = Spec('Usuario', ['- id: long', '- usuario: String', '- telefono: String', '- passwordHash: String',
                           '- rol: Rol', '- estado: EstadoUsuario', '- precioCorte: BigDecimal',
                           '- creadoEn: LocalDateTime', '- aprobadoEn: LocalDateTime'],
               ['+ esAdmin(): boolean', '+ estaAprobado(): boolean'], estereotipo=RECORD)
    cita = Spec('Cita', ['+ DURACION_MINUTOS: int', '- id: long', '- clienteId: long', '- clienteUsuario: String',
                         '- clienteTelefono: String', '- fecha: LocalDate', '- horaPedida: LocalTime',
                         '- horaFinal: LocalTime', '- estado: EstadoCita', '- precio: BigDecimal',
                         '- propina: BigDecimal', '- creadaEn: LocalDateTime', '- decididaEn: LocalDateTime'],
                ['+ horaEfectiva(): LocalTime', '+ yaHaPasado(ahora): boolean',
                 '+ confirmadaA(nuevaHoraFinal, ahora): Cita', '+ conEstado(nuevoEstado, nuevaDecididaEn): Cita',
                 '+ cerrada(resultado, precioCobrado, propinaRecibida): Cita'], estereotipo=RECORD)
    e_inv = Spec('EstadoInvitacion', ['PENDIENTE', 'USADA'], [], tipo='enum')
    e_rol = Spec('Rol', ['ADMIN', 'CLIENTE'], [], tipo='enum')
    e_usu = Spec('EstadoUsuario', ['PENDIENTE', 'APROBADO', 'BLOQUEADO'], [], tipo='enum')
    e_cita = Spec('EstadoCita', ['PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'CANCELADA', 'CADUCADA', 'HECHA', 'NO_ASISTIO'],
                  ['+ esActiva(): boolean'], tipo='enum')
    hor = Spec('HorarioSemanal', ['- id: Integer', '- diaSemana: int', '- horaInicio: LocalTime',
                                  '- horaFin: LocalTime', '- cupo: int', '- activo: boolean'], estereotipo=RECORD)
    exc = Spec('ExcepcionDia', ['+ CUPO_POR_DEFECTO: int', '- id: Integer', '- fecha: LocalDate',
                                '- tipo: TipoExcepcion', '- horaInicio: LocalTime', '- horaFin: LocalTime',
                                '- cupo: Integer', '- nota: String'],
               ['+ cupoEfectivo(): int', '+ paraFecha(otraFecha): ExcepcionDia'], estereotipo=RECORD)
    e_tipo = Spec('TipoExcepcion', ['CERRADO', 'ABIERTO'], [], tipo='enum')
    # valores que calcula ServicioHorarios (no se guardan en la base de datos)
    tramo = Spec('Tramo', ['- horaInicio: LocalTime', '- horaFin: LocalTime', '- cupo: int'],
                 ['+ huecos(): List<LocalTime>', '+ cabe(hora): boolean', '+ estaAlineada(hora): boolean'],
                 estereotipo=RECORD)
    dia = Spec('DiaDisponible', ['- fecha: LocalDate', '- estado: EstadoDia'], estereotipo=RECORD)
    hueco = Spec('Hueco', ['- hora: LocalTime', '- ocupado: boolean'], estereotipo=RECORD)
    e_dia = Spec('EstadoDia', ['LIBRE', 'COMPLETO', 'VACACIONES', 'CERRADO'], [], tipo='enum')

    X0 = 20
    wi, wu, wc = max(inv.w, 190), max(usu.w, 190), max(cita.w, 190)
    X1 = X0 + wi + 80
    X2 = X1 + wu + 62
    y0 = 20
    for s_, x, w_ in ((inv, X0, wi), (usu, X1, wu), (cita, X2, wc)):
        s_.dibuja(doc, x, y0, w_, None)
    fila_a = max(inv.caja.b, usu.caja.b, cita.caja.b)
    # enumeraciones bajo cada entidad
    yb = fila_a + 44
    e_inv.dibuja(doc, X0, yb, e_inv.w, None)
    e_rol.dibuja(doc, X1 - 8, yb, max(e_rol.w, 90), None)
    e_usu.dibuja(doc, e_rol.caja.r + 12, yb, max(e_usu.w, 130), None)
    e_cita.dibuja(doc, X2, yb, e_cita.w, None)
    # nota de las enumeraciones, bajo las pequeñas
    y_nenum = max(e_inv.caja.b, e_rol.caja.b, e_usu.caja.b) + 30
    w_nenum = X2 - 30 - X0
    t_enum = ('Las enumeraciones tienen <b>enBd()</b> y <b>desdeTexto(texto)</b> para pasar del ENUM en minúsculas '
              'de MySQL al valor de Java y al revés. <b>EstadoDia</b> no se guarda, así que en su lugar tiene '
              '<b>enJson()</b>.')
    h_nenum = n_lineas(texto_plano(t_enum), w_nenum - 20) * 16 + 18
    nota(doc, t_enum, X0, y_nenum, w_nenum, h_nenum, fs=13)

    # horario semanal y excepciones
    yc = max(e_cita.caja.b, y_nenum + h_nenum) + 40
    hor.dibuja(doc, X0, yc, wi, None)
    exc.dibuja(doc, X2, yc, wc, None)
    e_tipo.dibuja(doc, X2, exc.caja.b + 40, max(e_tipo.w, 130), None)
    w_nt = X2 - 40 - (hor.caja.r + 40)
    t_tramo = ('Para cada día, <b>ServicioHorarios</b> saca un <b>Tramo</b> de la excepción de esa fecha y, si no la '
               'hay, del horario semanal. Sin ninguna de las dos, el día está cerrado.')
    nota(doc, t_tramo, hor.caja.r + 40, yc, w_nt, n_lineas(texto_plano(t_tramo), w_nt - 36) * 16 + 18, fs=13)

    # valores calculados
    yd = max(hor.caja.b, e_tipo.caja.b) + 40
    pk_w = X2 + wc - X0
    alto = 30 + 14 + max(tramo.h, dia.h, hueco.h, e_dia.h) + 14
    pk_dto = paquete(doc, 'valores que calcula ServicioHorarios (no se guardan)', X0, yd, pk_w, alto)
    tramo.dibuja(doc, X0 + 14, yd + 44, tramo.w, pk_dto.id)
    dia.dibuja(doc, tramo.caja.r + 40, yd + 44, max(dia.w, 150), pk_dto.id)
    e_dia.dibuja(doc, dia.caja.r + 40, yd + 44, e_dia.w, pk_dto.id)
    hueco.dibuja(doc, e_dia.caja.r + 40, yd + 44, max(hueco.w, 150), pk_dto.id)
    assert hueco.caja.r < pk_dto.r - 10, 'los valores calculados no caben en su paquete'
    yy = dia.caja.y + 60
    doc.arista(dia.caja, e_dia.caja, ASOC, salida=lado_der(dia.caja, yy), entrada=lado_izq(e_dia.caja, yy))

    # ---- relaciones -------------------------------------------------------
    ic, uc, cc = inv.caja, usu.caja, cita.caja
    gi, gc = X1 - ic.r, X2 - uc.r                       # separación entre columnas

    def cerca(g, d=15):                                  # parámetro x de una etiqueta a d px del origen / del destino
        return round(-1 + 2 * d / g, 3), round(1 - 2 * d / g, 3)

    # Usuario 1 -- 0..* Cita
    y = uc.y + 120
    e = doc.arista(uc, cc, LINEA, salida=lado_der(uc, y), entrada=lado_izq(cc, y))
    doc.etiqueta(e, '1', x=cerca(gc, 11)[0], dy=-13)
    doc.etiqueta(e, '0..*', x=cerca(gc, 20)[1], dy=-13)
    doc.etiqueta(e, 'pide', x=0, dy=13)
    # Usuario 1 -- 0..* Invitacion (creadaPor)
    y1i = uc.y + 70
    e = doc.arista(ic, uc, LINEA, salida=lado_der(ic, y1i), entrada=lado_izq(uc, y1i))
    doc.etiqueta(e, '0..*', x=cerca(gi, 20)[0], dy=-13)
    doc.etiqueta(e, '1', x=cerca(gi, 11)[1], dy=-13)
    doc.etiqueta(e, 'creadaPor', x=0, dy=13)
    # Usuario 0..1 -- 0..1 Invitacion (usadaPor)
    y2i = uc.y + 150
    e = doc.arista(ic, uc, LINEA, salida=lado_der(ic, y2i), entrada=lado_izq(uc, y2i))
    doc.etiqueta(e, '0..1', x=cerca(gi, 20)[0], dy=-13)
    doc.etiqueta(e, '0..1', x=cerca(gi, 20)[1], dy=-13)
    doc.etiqueta(e, 'usadaPor', x=0, dy=13)
    # enumeraciones (asociación corta, en vertical)
    # cada flecha baja recta: sale y entra a la misma x (la del centro de la enumeración)
    for ent, en in ((inv, e_inv), (cita, e_cita), (exc, e_tipo)):
        doc.arista(ent.caja, en.caja, ASOC, salida=(en_x(ent.caja, en.caja.cx), 1), entrada=(0.5, 0))
    xrol = uc.x + 0.2 * uc.w
    xusu = uc.x + 0.85 * uc.w
    doc.arista(usu.caja, e_rol.caja, ASOC, salida=(0.2, 1), entrada=(en_x(e_rol.caja, xrol), 0))
    doc.arista(usu.caja, e_usu.caja, ASOC, salida=(0.85, 1), entrada=(en_x(e_usu.caja, xusu), 0))
    return doc


def construir():
    return [(construir_http_auth(), NOMBRE_HTTP),
            (construir_negocio(), NOMBRE_NEGOCIO),
            (construir_modelo(), NOMBRE_MODELO)]
