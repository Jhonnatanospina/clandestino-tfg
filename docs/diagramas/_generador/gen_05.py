# -*- coding: utf-8 -*-
"""05 · Diagramas de clases de la app Android.
   05a  ui (acceso + cliente) y modelovista del cliente
   05b  ui (admin) y modelovista del administrador
   05c  clases base, datos (repositorio, remoto), utilidades y modelo
Los miembros salen del código compilado (javap). Se omiten los getters, los onCreate/onViewCreated
y las vistas que solo guarda cada pantalla (botones, textos...), que no aportan al diseño.
"""
from uml_dio import *
from gen_04 import Spec, NotaSpec, maqueta, lado_der, lado_izq, arista_xy

PAD = 16
CAB = 30


# ---------------------------------------------------------------------------
#   Figuras "pantallas -> ViewModel"
# ---------------------------------------------------------------------------
def figura_pares(nombre, paquetes, nota_vm):
    """paquetes: lista de dict(nombre, host=Spec|None, filas=[([Spec UI...], Spec VM|None), ...])."""
    doc = Doc(nombre)
    GAP_FILA, GAP_UI, GAP_PK, ZONA_FLECHAS = 18, 12, 26, 64
    X0 = 20
    todos_ui = [s for p in paquetes for f in p['filas'] for s in f[0]] + [p['host'] for p in paquetes if p['host']]
    wui = max(s.w for s in todos_ui)
    wvm = max(f[1].w for p in paquetes for f in p['filas'] if f[1])
    pk_w = wui + 2 * PAD + 14          # +14: sitio para el tronco de la agregacion
    xui = X0 + PAD + 14
    xvm_pk = X0 + pk_w + ZONA_FLECHAS
    xvm = xvm_pk + PAD

    # ---- posiciones ---------------------------------------------------------------------
    y = 20
    lay = []     # (paquete, y_pk, alto_pk, [(y_fila, alto_fila, ys_ui, y_vm)], y_host)
    for p in paquetes:
        ypk = y
        yy = ypk + CAB + PAD
        y_host = None
        if p['host']:
            y_host = yy
            yy += p['host'].h + GAP_FILA + 6
        filas_pos = []
        for uis, vm in p['filas']:
            hu = sum(s.h for s in uis) + GAP_UI * (len(uis) - 1)
            hv = vm.h if vm else 0
            hf = max(hu, hv)
            ys = []
            yc = yy + (hf - hu) / 2
            for s in uis:
                ys.append(yc)
                yc += s.h + GAP_UI
            yv = yy + (hf - hv) / 2 if vm else None
            filas_pos.append((yy, hf, ys, yv))
            yy += hf + GAP_FILA
        alto = yy - GAP_FILA + PAD - ypk
        lay.append((p, ypk, alto, filas_pos, y_host))
        y = ypk + alto + GAP_PK

    # ---- contenedores ----------------------------------------------------------------------
    y_vm_pk = lay[0][1]
    nota_vm.w = wvm
    nota_vm.ajusta(wvm)
    # huecos libres de la columna de ViewModels, para colocar la nota
    vms_y = [(yv, yv + f[1].h) for p, _, _, fp, _ in lay for f, (_, _, _, yv) in zip(p['filas'], fp) if f[1]]
    huecos = [(y_vm_pk + CAB + PAD, vms_y[0][0])] + [(vms_y[i][1], vms_y[i + 1][0]) for i in range(len(vms_y) - 1)]
    y_nota = None
    for g0, g1 in huecos:
        if g1 - g0 >= nota_vm.h + 28:
            y_nota = g0 + 14 + (g1 - g0 - nota_vm.h - 28) / 2
            break
    fin_vm = vms_y[-1][1]
    if y_nota is None:
        y_nota = fin_vm + 18
        fin_vm = y_nota + nota_vm.h
    alto_vm_pk = fin_vm + PAD - y_vm_pk
    # los dos lados terminan a la misma altura
    fin_ui = lay[-1][1] + lay[-1][2]
    alto_vm_pk = max(alto_vm_pk, fin_ui - y_vm_pk)
    vm_pk = paquete(doc, 'modelovista', xvm_pk, y_vm_pk, wvm + 2 * PAD, alto_vm_pk)
    pks = {}
    for p, ypk, alto, _, _ in lay:
        if p is lay[-1][0]:
            alto = max(alto, y_vm_pk + alto_vm_pk - ypk)
        pks[p['nombre']] = paquete(doc, p['nombre'], X0, ypk, pk_w, alto)

    # ---- cajas ------------------------------------------------------------------------------
    for p, ypk, alto, filas_pos, y_host in lay:
        pk = pks[p['nombre']]
        if p['host']:
            p['host'].dibuja(doc, xui, y_host, wui, pk.id)
        for (uis, vm), (yy, hf, ys, yv) in zip(p['filas'], filas_pos):
            for s, yc in zip(uis, ys):
                s.dibuja(doc, xui, yc, wui, pk.id)
            if vm:
                vm.dibuja(doc, xvm, yv, wvm, vm_pk.id)
    nota_vm.dibuja(doc, xvm, y_nota, wvm, vm_pk.id)

    # ---- aristas -------------------------------------------------------------------------------
    for p, ypk, alto, filas_pos, y_host in lay:
        fr_boxes = [s for uis, _ in p['filas'] for s in uis if s.contenida]
        if p['host']:
            h_ = p['host'].caja
            xt = h_.x - 9
            for s in fr_boxes:
                yc = s.caja.y + 14
                doc.arista(h_, s.caja, AGRE, puntos=[(xt, h_.y + 14), (xt, yc)], salida=(0, 14 / h_.h),
                           entrada=(0, 14 / s.caja.h))
        for uis, vm in p['filas']:
            if not vm:
                continue
            v = vm.caja
            # puntos de entrada: lo mas cerca posible del centro de cada pantalla, separados 26 px
            deseo = [min(max(s.caja.cy, v.y + 16), v.b - 16) for s in uis]
            ent = []
            for d in deseo:
                if ent and d < ent[-1] + 26:
                    d = ent[-1] + 26
                ent.append(d)
            for s, yt in zip(uis, ent):
                c = s.caja
                if abs(yt - c.cy) < 8:       # casi recta: se endereza para que no salga un escalón de 2 px
                    yt = c.cy
                fy = (yt - v.y) / v.h
                if abs(yt - c.cy) < 1:
                    doc.arista(c, v, ASOC, salida=(1, 0.5), entrada=(0, fy))
                else:
                    xm = c.r + ZONA_FLECHAS * 0.45
                    doc.arista(c, v, ASOC, puntos=[(xm, c.cy), (xm, yt)], salida=(1, 0.5), entrada=(0, fy))
    return doc


def ui(nombre, sub, metodos=(), attrs=(), contenida=True):
    """Pantalla. contenida=False: no la aloja la actividad del panel (p. ej. un diálogo), así que no lleva rombo."""
    s = Spec(nombre, list(attrs), list(metodos), subtitulo=sub)
    s.contenida = contenida
    return s


def vm(nombre, repo, attrs, metodos):
    return Spec(nombre, ['- repo: ' + repo] + list(attrs), list(metodos), subtitulo='extends ModeloVistaBase')


def host(nombre):
    return ui(nombre, 'extends ActividadPanel', ['# menu(): int', '# crearFragmento(id): Fragment'])


def lista(tipo):
    return 'MutableLiveData<Resultado<List<%s>>>' % tipo


# ---------------------------------------------------------------------------
#   05a  Cliente
# ---------------------------------------------------------------------------
def construir_cliente():
    login = ui('ActividadInicioSesion', 'A1 · Iniciar sesión', ['- onEntrar()', '- pintar(resultado)'])
    registro = ui('ActividadRegistro', 'A2 · Registro con código',
                  ['- validarEnVivo()', '- onRegistrar()', '- pintar(resultado)'])
    espera = ui('ActividadEspera', 'A3 · Espera o bloqueado', ['- onRefrescar()', '- pintar(resultado)'])
    c1 = ui('FragmentoMisCitas', 'C1 · Mis citas', ['- pintar(resultado)', '- preguntarCancelar(cita)'],
            ['- adaptador: AdaptadorMisCitas'])
    c2 = ui('FragmentoPedirCita', 'C2 · Pedir cita',
            ['+ onSaveInstanceState(estado)', '- elegirDia(fecha)', '- pintarDias(resultado)',
             '- pintarHuecos(resultado)'],
            ['- calendario: AdaptadorCalendario', '- diaElegido: LocalDate', '- horaElegida: LocalTime',
             '- pidiendo: boolean'])
    c3 = ui('FragmentoAjustes', 'C3 · Ajustes', ['- pintarPerfil(resultado)', '- pintarNombre(nombre)'])

    v_auth = vm('ModeloVistaAcceso', 'RepositorioAcceso', ['- sesion: MutableLiveData<Resultado<Usuario>>'],
                ['+ haySesion(): boolean', '+ iniciarSesion(usuario, contrasena)',
                 '+ registrarse(codigo, usuario, telefono, contrasena)', '+ consultarEstado()'])
    v_mis = vm('ModeloVistaMisCitas', 'RepositorioCitas', ['- citas: ' + lista('Cita')],
               ['+ cargar()', '+ cancelar(id)'])
    v_ped = vm('ModeloVistaPedirCita', 'RepositorioCitas', ['- dias: ' + lista('Dia'), '- huecos: ' + lista('Hueco')],
               ['+ cargarDias()', '+ cargarHuecos(fecha)', '+ olvidarHuecos()', '+ pedir(fecha, hora)'])
    v_aju = vm('ModeloVistaAjustes', 'RepositorioAcceso',
               ['- perfil: MutableLiveData<Resultado<Usuario>>', '- temaOscuro: MutableLiveData<Boolean>'],
               ['+ cargarPerfil()', '+ cambiarTema(oscuro)'])
    paquetes = [
        dict(nombre='ui.acceso', host=None, filas=[([login, registro, espera], v_auth)]),
        dict(nombre='ui.cliente', host=host('ActividadCliente'), filas=[([c1], v_mis), ([c2], v_ped), ([c3], v_aju)]),
    ]
    nota = NotaSpec('Las pantallas C1, C2 y C3 heredan de <b>FragmentoPanel</b> y <b>ActividadCliente</b> de '
                    '<b>ActividadPanel</b>, que pone la barra de pestañas (las bases están en 05d). Cada modelo de '
                    'vista guarda sus datos en un <b>Resultado</b> (cargando, éxito o error) y la pantalla solo lo '
                    'observa. El tema oscuro es un valor local y va sin <b>Resultado</b>. Cerrar sesión lo resuelve '
                    '<b>FragmentoPanel</b>, porque se puede hacer desde cualquier pestaña.')
    return figura_pares('05a-clases-app-cliente', paquetes, nota)


# ---------------------------------------------------------------------------
#   05b  Administrador
# ---------------------------------------------------------------------------
def construir_admin():
    d1 = ui('FragmentoSolicitudes', 'D1 · Solicitudes', ['- pintar(resultado)', '- preguntarRechazar(cita)'],
            ['- adaptador: AdaptadorSolicitudes'])
    d2 = ui('FragmentoAgenda', 'D2 · Agenda',
            ['- cargarSemana()', '- cambiarSemana(semanas)', '- elegirDia(fecha)', '- pintar(resultado)',
             '- preguntarCancelar(cita)'],
            ['- adaptador: AdaptadorAgenda', '- diaElegido: LocalDate', '- citasSemana: List<Cita>'])
    d3 = ui('FragmentoHorarios', 'D3 · Horarios y vacaciones',
            ['- crearFilas()', '- elegirHora(diaSemana, esInicio)', '- elegirCupo(diaSemana)', '- guardarSemana()',
             '- pintarSemana(resultado)', '- pintarExcepciones(resultado)', '- preguntarBorrar(excepcion)'],
            ['- adaptador: AdaptadorExcepciones'])
    d3b = ui('DialogoExcepcion', 'D3 · Vacaciones o día extra',
             ['- elegirFechas()', '- elegirHora(esInicio)', '- guardar()', '- problema(): String'],
             ['- esDiaExtra: boolean', '- desde: LocalDate', '- hasta: LocalDate', '- inicio: LocalTime',
              '- fin: LocalTime', '- cupo: int'], contenida=False)
    d4 = ui('FragmentoClientes', 'D4 · Clientes e invitaciones',
            ['- generar()', '- copiarCodigo()', '- pintarCodigo(invitacion)', '- pintarInvitaciones(resultado)',
             '- pintarClientes(resultado)', '- preguntarBloquear(cliente)'],
            ['- adaptadorInvitaciones: AdaptadorInvitaciones', '- adaptadorClientes: AdaptadorClientes'])
    v_cit = vm('ModeloVistaCitasAdmin', 'RepositorioAgenda',
               ['- citas: ' + lista('Cita'), '- estado: String', '- desde: LocalDate', '- hasta: LocalDate'],
               ['+ cargar(estado, desde, hasta)', '+ recargar()', '+ confirmar(id, horaFinal)', '+ rechazar(id)',
                '+ marcarAsistencia(id, vino)', '+ cancelar(id)'])
    v_hor = vm('ModeloVistaHorarios', 'RepositorioHorarios',
               ['- horarios: ' + lista('HorarioSemanal'), '- excepciones: ' + lista('ExcepcionDia')],
               ['+ cargar()', '+ cambiarDia(cambiado)', '+ semanaEnPantalla(): List<HorarioSemanal>',
                '+ guardarSemana()', '+ cerrarDias(desde, hasta, nota)', '+ abrirDiaExtra(fecha, inicio, fin, cupo, nota)',
                '+ borrarExcepcion(id)'])
    v_cli = vm('ModeloVistaClientes', 'RepositorioClientes',
               ['- clientes: ' + lista('Usuario'), '- invitaciones: ' + lista('Invitacion'),
                '- codigoNuevo: MutableLiveData<Invitacion>'],
               ['+ cargar()', '+ aprobar(id)', '+ bloquear(id)', '+ generarInvitacion(nota)'])
    paquetes = [
        dict(nombre='ui.admin', host=host('ActividadAdmin'),
             filas=[([d1, d2], v_cit), ([d3, d3b], v_hor), ([d4], v_cli)]),
    ]
    nota = NotaSpec('Las cuatro pestañas heredan de <b>FragmentoPanel</b> y <b>ActividadAdmin</b> de '
                    '<b>ActividadPanel</b> (bases en 05d). <b>DialogoExcepcion</b> es un <i>DialogFragment</i> que '
                    'abre FragmentoHorarios y comparte con él el modelo de vista. Las horas y el cupo se eligen con '
                    'los selectores de la clase <b>Selectores</b>. Cada lista se pinta con su adaptador de '
                    '<i>RecyclerView</i>, y las acciones de cada fila (confirmar, cancelar, bloquear...) vuelven al '
                    'fragmento por una interfaz del propio adaptador.')
    return figura_pares('05b-clases-app-admin', paquetes, nota)


# ---------------------------------------------------------------------------
#   05d  Clases base: aplicación, ui, modelovista y utilidades de pantalla
# ---------------------------------------------------------------------------
def construir_base():
    doc = Doc('05d-clases-app-base')

    # paquete raíz
    app = Spec('AplicacionClandestino', ['- sesion: SesionAlmacen', '- api: ClienteApi'],
               ['+ sesion(): SesionAlmacen', '+ api(): ClienteApi', '+ temaOscuro(): boolean',
                '+ guardarTemaOscuro(boolean)'], subtitulo='extends Application')
    n_app = NotaSpec('Crea una sola vez <b>SesionAlmacen</b> y <b>ClienteApi</b>, y los modelos de vista las piden '
                     'con <b>app()</b>. Así toda la app comparte el mismo token sin usar ninguna librería de '
                     'inyección de dependencias. También guarda si el tema oscuro está activado.', span=2, estira=True)

    # ui (clases base)
    act = Spec('ActividadPanel', ['- navegacion: BottomNavigationView'],
               ['# menu(): int', '# crearFragmento(id): Fragment', '# mostrar(id)', '+ irAPestana(id)',
                '+ usuario(): String'], abstracta=True, subtitulo='extends AppCompatActivity')
    frag = Spec('FragmentoPanel', [],
                ['# panel(): ActividadPanel', '# saludo(): String', '# ponerCabecera(vista, ceja, titulo)',
                 '# mostrarBoton(…): View', '# avisarOperaciones(modelo)', '# preguntarCerrarSesion()'],
                abstracta=True, subtitulo='extends Fragment')
    pant = Spec('Pantallas', [],
                ['+ abrirSegunCuenta(actividad, usuario)', '+ cerrarSesion(actividad)', '+ mostrarError(actividad, error)',
                 '+ avisarSiFalla(actividad, resultado)', '+ confirmar(…)', '+ pintarChip(…)'],
                subtitulo='final, métodos estáticos')

    # modelovista
    base = Spec('ModeloVistaBase', ['- operacion: MutableLiveData<Resultado<String>>'],
                ['# app(): AplicacionClandestino', '+ getOperacion(): LiveData<Resultado<String>>',
                 '# avisar(estado)', '# accion(mensajeExito, recargar): Callback<T>'],
                abstracta=True, subtitulo='extends AndroidViewModel')
    n_vm = NotaSpec('Las acciones que no traen datos (confirmar, cancelar, guardar...) usan el Callback que devuelve '
                    '<b>accion()</b>. Mientras esperan, <b>operacion</b> está cargando y la pantalla desactiva los '
                    'botones. Al terminar deja el mensaje para el aviso y vuelve a cargar la lista, también con un '
                    '409, porque entonces alguien ha cambiado algo mientras tanto. Los siete modelos de vista que '
                    'heredan de esta clase están en 05a y 05b.', span=2, estira=True)

    # utilidades que usan las pantallas y los modelos de vista
    res = Spec('Resultado<T>', ['- estado: Estado {CARGANDO, EXITO, ERROR}', '- datos: T', '- error: ErrorApi',
                                '- avisado: boolean'],
               ['+ cargando(datos): Resultado<T>', '+ exito(datos): Resultado<T>', '+ error(error): Resultado<T>',
                '+ en(liveData): Callback<T>', '+ estaCargando(): boolean', '+ esCorrecto(): boolean',
                '+ haFallado(): boolean', '+ avisarUnaVez(): boolean'], subtitulo='final')
    fmt = Spec('Formatos', ['+ ZONA: ZoneId'],
               ['+ hoy(): LocalDate', '+ ahora(): LocalDateTime', '+ hora(hora): String', '+ diaCorto(fecha): String',
                '+ diaLargo(fecha): String', '+ rango(desde, hasta): String', '+ semana(lunes): String',
                '+ telefono(numero): String'], subtitulo='final, métodos estáticos')
    val = Spec('Validaciones', [], ['+ usuarioValido(texto): boolean', '+ telefonoValido(texto): boolean',
                                    '+ contrasenaValida(texto): boolean', '+ codigoValido(texto): boolean'],
               subtitulo='final, métodos estáticos')
    n_util = NotaSpec('<b>Resultado.en()</b> crea el Callback que vuelca la respuesta de un repositorio en un '
                      'LiveData. <b>avisarUnaVez()</b> evita que un aviso se repita al girar el móvil. '
                      '<b>Formatos</b> pasa fechas y horas a texto en español con la hora de Madrid, aunque el móvil '
                      'esté en otra zona. <b>Validaciones</b> marca en el registro los campos mal escritos mientras se '
                      'teclea, y la API lo vuelve a comprobar todo al recibir la petición.', span=2, estira=True)

    bandas = [
        ('com.clandestino.app', [{0: app, 1: n_app}]),
        ('ui (clases base)', [{0: act, 1: frag, 2: pant}]),
        ('modelovista', [{0: base, 1: n_vm}]),
        ('utilidades', [{0: res, 1: fmt, 2: val}, {1: n_util}]),
    ]
    bm, cx, cw, yfin = maqueta(doc, bandas, 20, 20, gap_col=46, gap_fila=30, pad=14, gap_banda=40)
    xd = bm['ui (clases base)'].r - 70
    dep(doc, bm['ui (clases base)'], bm['modelovista'], xd)
    dep(doc, bm['modelovista'], bm['utilidades'], xd)
    return doc


def dep(doc, origen, destino, x):
    """Dependencia «usa» entre dos paquetes seguidos, en vertical por el hueco que los separa."""
    e = doc.arista(None, None, DEP, sp=(x, origen.b), tp=(x, destino.y))
    doc.etiqueta(e, '«usa»', x=0, dx=34, fs=12, fondo=False)


# ---------------------------------------------------------------------------
#   05c  Datos: repositorio, remoto, sesión y modelo
# ---------------------------------------------------------------------------
def construir_datos():
    doc = Doc('05c-clases-app-datos')

    # datos.repositorio
    r_auth = Spec('RepositorioAcceso', ['- api: ClienteApi', '- sesion: SesionAlmacen'],
                  ['+ haySesion(): boolean', '+ iniciarSesion(usuario, contrasena, cb)',
                   '+ registrarse(codigo, usuario, telefono, contrasena, cb)', '+ perfil(cb)'])
    repo = lambda n, m: Spec(n, ['- api: ClienteApi'], m)
    r_cit = repo('RepositorioCitas', ['+ dias(cb)', '+ huecos(fecha, cb)', '+ pedir(fecha, hora, cb)', '+ misCitas(cb)',
                                      '+ cancelar(id, cb)'])
    r_age = repo('RepositorioAgenda', ['+ citas(estado, desde, hasta, cb)', '+ confirmar(id, horaFinal, cb)',
                                       '+ rechazar(id, cb)', '+ cancelar(id, cb)', '+ marcarAsistencia(id, vino, cb)'])
    r_hor = repo('RepositorioHorarios', ['+ semanal(cb)', '+ guardarSemanal(horarios, cb)', '+ excepciones(cb)',
                                         '+ cerrarDias(desde, hasta, nota, cb)',
                                         '+ abrirDiaExtra(fecha, inicio, fin, cupo, nota, cb)', '+ borrarExcepcion(id, cb)'])
    r_cli = repo('RepositorioClientes', ['+ clientes(cb)', '+ aprobar(id, cb)', '+ bloquear(id, cb)',
                                         '+ invitaciones(cb)', '+ generarInvitacion(nota, cb)'])
    n_rep = NotaSpec('El último parámetro, <b>cb</b>, es el <b>Callback&lt;T&gt;</b> que recibe la respuesta. Cada '
                     'método le pasa a <b>ClienteApi</b> un <b>Cuerpo</b> que monta el JSON y un <b>Conversor</b> que '
                     'lo lee, casi siempre como lambda (<i>Cita::desdeJson</i>). No tienen interfaz porque la API es '
                     'la única fuente de datos.', estira=True)

    # datos.remoto
    ex = Spec('ErrorApi', ['- estadoHttp: int', '- codigo: String'],
              ['+ sesionNoValida(): boolean', '+ cuentaNoAprobada(): boolean'], subtitulo='extends Exception')
    api = Spec('ClienteApi', ['- urlBase: String', '- sesion: SesionAlmacen', '- principal: Handler'],
               ['+ enviar(metodo, ruta, cuerpo, conversor, cb)', '+ lista(campo, conversor): Conversor<List<T>>',
                '- ejecutar(metodo, ruta, json): JSONObject', '- abrirConexion(metodo, ruta)',
                '- leerError(estado, entrada): ErrorApi'])
    cb = Spec('Callback<T>', [], ['+ onExito(datos: T)', '+ onError(error: ErrorApi)'], tipo='interfaz')
    cuerpo = Spec('Cuerpo', [], ['+ crear(): JSONObject'], tipo='interfaz', subtitulo='dentro de ClienteApi')
    conv = Spec('Conversor<T>', [], ['+ convertir(json): T'], tipo='interfaz', subtitulo='dentro de ClienteApi')

    # utilidades (solo la sesión; el resto está en 05d)
    ses = Spec('SesionAlmacen', ['- preferencias: SharedPreferences', '- tokenEnMemoria: String'],
               ['+ guardarToken(token)', '+ leerToken(): String', '+ borrar()', '- claveDelKeystore(): SecretKey'])
    n_ses = NotaSpec('El JWT se guarda cifrado con <b>AES-GCM</b> y una clave del <b>Android Keystore</b>, que no sale '
                     'del móvil. En las preferencias solo quedan el IV y el texto cifrado.', estira=True)
    n_util = NotaSpec('<b>Resultado</b>, <b>Formatos</b> y <b>Validaciones</b> también son de este paquete y se '
                      'detallan en 05d.', estira=True)

    # modelo
    dia = Spec('Dia', ['- fecha: LocalDate', '- estado: EstadoDia'], ['+ desdeJson(json): Dia', '+ estaLibre(): boolean'])
    hueco = Spec('Hueco', ['- hora: LocalTime', '- ocupado: boolean'],
                 ['+ desdeJson(json): Hueco', '+ estaOcupado(): boolean'])
    n_mod = NotaSpec('<b>Usuario</b>, <b>Cita</b>, <b>Invitacion</b>, <b>HorarioSemanal</b> y <b>ExcepcionDia</b> '
                     'copian los campos que manda la API y se crean con <b>desdeJson(json)</b>. <b>EstadoCita</b> y '
                     '<b>EstadoUsuario</b> traen la etiqueta en español que se pinta en los chips. <b>LecturaJson</b> '
                     'lee los campos que pueden venir vacíos.', estira=True)

    bandas = [
        ('datos.repositorio', [{0: r_auth, 1: r_cit, 2: r_age}, {0: r_hor, 1: r_cli, 2: n_rep}]),
        ('datos.remoto', [{0: ex, 1: api, 2: cb}, {0: cuerpo, 2: conv}]),
        ('utilidades', [{0: n_ses, 1: ses, 2: n_util}]),
        ('modelo', [{0: dia, 1: hueco, 2: n_mod}]),
    ]
    X0 = 44                     # margen izquierdo para la dependencia repositorio -> modelo
    y_rep = 20 + 30 + 40        # debajo del paquete de referencia modelovista
    bm, cx, cw, yfin = maqueta(doc, bandas, X0, y_rep, gap_col=46, gap_fila=30, pad=14, gap_banda=40)
    rep, rem, mod = bm['datos.repositorio'], bm['datos.remoto'], bm['modelo']
    pk_vm = paquete(doc, 'modelovista · detalle en 05a, 05b y 05d', X0, 20, rep.w, 30)

    # ---- aristas dentro de datos.remoto
    A = api.caja
    yh = A.y + 14
    e = doc.arista(A, ex.caja, DEP, salida=lado_izq(A, yh), entrada=lado_der(ex.caja, yh))
    doc.etiqueta(e, '«lanza»', x=0, dy=-13, fs=12)
    e = doc.arista(A, cb.caja, DEP, salida=lado_der(A, yh), entrada=lado_izq(cb.caja, yh))
    doc.etiqueta(e, '«usa»', x=0, dy=-13, fs=12)
    # ClienteApi lee el token de SesionAlmacen (baja recta por la columna central, que está libre)
    doc.arista(A, ses.caja, ASOC, salida=(0.5, 1), entrada=(0.5, 0))

    # ---- dependencias entre paquetes
    xd = rep.r - 70
    dep(doc, pk_vm, rep, xd)
    dep(doc, rep, rem, xd)
    # repositorio -> modelo por el margen izquierdo
    xm = X0 - 22
    y1, y2 = rep.y + 60, mod.y + 50
    e = doc.arista(None, None, DEP, sp=(rep.x, y1), tp=(mod.x, y2), puntos=[(xm, y1), (xm, y2)])
    doc.etiqueta(e, '«usa»', x=0, dx=-2, fs=12)
    return doc


def construir():
    return [(construir_cliente(), '05a-clases-app-cliente'),
            (construir_admin(), '05b-clases-app-admin'),
            (construir_datos(), '05c-clases-app-datos'),
            (construir_base(), '05d-clases-app-base')]
