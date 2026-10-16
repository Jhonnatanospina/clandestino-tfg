# -*- coding: utf-8 -*-
"""06 · Diagramas de secuencia de UC04 Solicitar cita, en piezas que encajan:

   06a-1  app Android, envio: del toque del boton a la llamada HTTP
   06a-2  app Android, respuesta: de la respuesta HTTP a la pantalla (201, 409 y otros errores)
   06b    API, acceso: Enrutador (ruta, estado y rol) y filtro de autenticacion (cabecera, token y usuario)
   06c-1  API, reserva (1): cuerpo, horizonte, bloqueo por fecha, transaccion y las comprobaciones de RN-09
   06c-2  API, reserva (2): insercion, commit o rollback y respuesta (201 desde RutasCliente, errores
          desde el Enrutador)

El orden de comprobaciones sigue docs/diseno/reglas-negocio.md RN-09 y docs/diseno/api-contrato.md.
Los nombres de clases y metodos son los de 04-clases-api y 05-clases-app.

Legibilidad: letra de 15 px y lienzos de ~600-800 unidades de ancho -> a 15 cm sale a 8-10 pt.
El generador compartido secuencia.py no se toca: aqui se extiende con la clase SecL (letra mayor,
posicion automatica de las lineas de vida y cursor vertical).
"""
from uml_dio import *
from secuencia import *
import re as _re

FS = 15                 # letra de mensajes y notas
HFS = 15                # letra de cabeceras y marcos
SUBPX = 14              # letra del subtitulo de las cabeceras
SUB = '<br><font style="font-size:%dpx;font-weight:normal">%%s</font>' % SUBPX
GRIS_TXT = '#3A3A3E'    # texto auxiliar (contraste alto sobre blanco)


def plano(t):
    return re_texto(t)


class SecL(Secuencia):
    """Secuencia con letra configurable, ancho de las lineas de vida calculado y cursor vertical."""

    def __init__(self, doc, fs=FS, hfs=HFS, alto_cab=50, y_cab=20, hueco_barra=110):
        super().__init__(doc, y_cab=y_cab, alto_cab=alto_cab)
        self.fs, self.hfs, self.hueco_barra = fs, hfs, hueco_barra
        self.lh = int(round(fs * 1.2))
        self.y = y_cab + alto_cab + 8
        self.st = {
            'call': MSG.replace('fontSize=13', 'fontSize=%d' % fs),
            'ret': RET.replace('fontSize=13', 'fontSize=%d' % fs),
            'async': ASY.replace('fontSize=13', 'fontSize=%d' % fs),
        }

    # ------------------------------------------------------------------ declaracion
    def parte(self, clave, titulo, w=None, actor=False, x=0):
        if w is None:
            w = 64 if actor else 0
            for l in titulo.split('<br>'):
                sub = 'font-size' in l
                w = max(w, tw(plano(l), SUBPX if sub else self.hfs, not sub) + 26)
            w = redondea(w, 2)
        super().parte(clave, titulo, x, w, actor)

    def texto(self, x, y, w, hh, html_texto, fs=None, align='left', negrita=False, color=TINTA, rot=0, fondo=False,
              cursiva=False):
        self.textos.append(dict(x=x, y=y, w=w, h=hh, t=html_texto, fs=fs or self.fs, align=align, color=color,
                                negrita=negrita, rot=rot, fondo=fondo, cursiva=cursiva))

    def nota(self, x, y, w, hh, html_texto, fs=None):
        super().nota(x, y, w, hh, html_texto, fs or self.fs)

    # ------------------------------------------------------------------ cursor vertical
    def m(self, o, d, texto, tipo='call', lineas=None):
        """Mensaje en la posicion actual del cursor; deja sitio encima para la etiqueta."""
        lin = lineas or texto.count('<br>') + 1
        self.y += lin * self.lh + 12
        self.msg(o, d, self.y, texto, tipo=tipo, lineas=lin)
        return self.y

    def a(self, p, texto, tipo='call', lazo=30, cruza=0):
        """Autollamada: devuelve (y1, y2). `cruza` = lineas de vida vecinas (sin barras a esa altura)
        que la etiqueta puede tapar con su fondo blanco."""
        lin = texto.count('<br>') + 1
        y1 = self.y + 16
        y2 = y1 + max(30, lin * self.lh + 8)
        self.auto(p, y1, texto, y2=y2, ancho_lazo=lazo, tipo=tipo)
        self.mensajes[-1]['cruza'] = cruza
        self.y = y2
        return y1, y2

    def gap(self, d):
        self.y += d
        return self.y

    # ------------------------------------------------------------------ posiciones horizontales
    def posiciona(self, margen=16, extra=None):
        """Calcula la x de cada linea de vida a partir de las cabeceras y de lo que ocupan las etiquetas."""
        ks = list(self.partes)
        n = len(ks)
        idx = {k: i for i, k in enumerate(ks)}
        paso = [0.0] * (n - 1)
        for i in range(n - 1):
            a, b = self.partes[ks[i]], self.partes[ks[i + 1]]
            paso[i] = (a['w'] + b['w']) / 2 + 10
        anchos = lambda t: max(tw(plano(l), self.fs, '<b>' in l) for l in t.split('<br>'))
        for m in self.mensajes:
            if m['k'] == 'auto':
                i = idx[m['p']]
                j = i + 1 + m.get('cruza', 0)          # linea de vida cuya barra no se puede tapar
                need = BARRA_W / 2 + m['L'] + 8 + anchos(m['texto']) + 10 + BARRA_W / 2
                if j <= n - 1 and sum(paso[i:j]) < need:
                    falta = (need - sum(paso[i:j])) / (j - i)
                    for q in range(i, j):
                        paso[q] += falta
        for m in self.mensajes:
            if m['k'] == 'msg':
                i, j = sorted((idx[m['o']], idx[m['d']]))
                need = anchos(m['texto']) + BARRA_W + 16
                if j == i + 1:
                    paso[i] = max(paso[i], need)
        for m in self.mensajes:
            if m['k'] == 'msg':
                i, j = sorted((idx[m['o']], idx[m['d']]))
                need = anchos(m['texto']) + BARRA_W + 16
                if j > i + 1 and sum(paso[i:j]) < need:
                    falta = (need - sum(paso[i:j])) / (j - i)
                    for q in range(i, j):
                        paso[q] += falta
        for (k1, k2), v in (extra or {}).items():
            i, j = sorted((idx[k1], idx[k2]))
            if sum(paso[i:j]) < v:
                falta = (v - sum(paso[i:j])) / (j - i)
                for q in range(i, j):
                    paso[q] += falta
        x = margen + self.partes[ks[0]]['w'] / 2
        self.partes[ks[0]]['x'] = x
        for i in range(n - 1):
            x += paso[i]
            self.partes[ks[i + 1]]['x'] = x
        self.x_min = margen
        self.x_max = x + self.partes[ks[-1]]['w'] / 2
        for m in self.mensajes:
            if m['k'] == 'auto':
                fin = self.partes[m['p']]['x'] + BARRA_W / 2 + m['L'] + 10 + anchos(m['texto']) + 6
                self.x_max = max(self.x_max, fin)
        return paso

    def X(self, k):
        return self.partes[k]['x']

    # ------------------------------------------------------------------ dibujo (copia de Secuencia.dibuja con letra parametrica)
    def _barras_auto(self, hueco=None):
        super()._barras_auto(hueco or self.hueco_barra)

    def dibuja(self, y_fin):
        doc = self.doc
        fs, hfs = self.fs, self.hfs
        for m in self.marcos:
            st = estilo(shape='umlFrame', whiteSpace='wrap', html=1, pointerEvents=0, recursiveResize=0, container=0,
                        collapsible=0, width=m['tab'], height=28, fillColor='none', strokeColor=BORDE, strokeWidth=1.5,
                        fontColor=TINTA, fontSize=hfs, fontStyle=1, fontFamily=FUENTE)
            doc.vertice(m['op'], m['x1'], m['y1'], m['x2'] - m['x1'], m['y2'] - m['y1'], st)
            gx = m['gx'] if m['gx'] is not None else m['x1'] + m['tab'] + 10
            if m['guarda']:
                self.guardas.append((gx, m['y1'] + 3, m['guarda']))
            for ys, g in m['seps']:
                doc.arista(None, None, 'html=1;dashed=1;dashPattern=8 4;endArrow=none;strokeColor=%s;strokeWidth=1.5;' % BORDE,
                           sp=(m['x1'], ys), tp=(m['x2'], ys))
                if g:
                    gx2 = m['gx'] if m['gx'] is not None else m['x1'] + 10
                    self.guardas.append((gx2, ys + 3, g))
        for k, p in self.partes.items():
            x = p['x']
            if p['actor']:
                st = estilo(shape='umlActor', verticalLabelPosition='bottom', verticalAlign='top', html=1, outlineConnect=0,
                            fillColor=BLANCO, strokeColor=TINTA, strokeWidth=2, fontColor=TINTA, fontSize=hfs,
                            fontStyle=1, fontFamily=FUENTE)
                doc.vertice(p['titulo'], x - 15, self.y_cab - 4, 30, 54, st)
                doc.arista(None, None, 'html=1;dashed=1;dashPattern=8 6;endArrow=none;strokeColor=%s;strokeWidth=1.5;' % BORDE,
                           sp=(x, self.y_cab + 80), tp=(x, y_fin))
            else:
                st = estilo(shape='umlLifeline', perimeter='lifelinePerimeter', whiteSpace='wrap', html=1, container=1,
                            dropTarget=0, collapsible=0, recursiveResize=0, outlineConnect=0, portConstraint='eastwest',
                            size=self.alto_cab, fillColor=ROJO, strokeColor=BORDE, strokeWidth=1.5, fontColor=BLANCO,
                            fontSize=hfs, fontStyle=1, fontFamily=FUENTE)
                doc.vertice(p['titulo'], x - p['w'] / 2, self.y_cab, p['w'], y_fin - self.y_cab, st)
        self._barras_auto()
        for k, lista in self.barras.items():
            x = self.partes[k]['x']
            for b in lista:
                relleno = b[3] if len(b) > 3 else N2
                c = doc.vertice('', x - BARRA_W / 2, b[0], BARRA_W, b[1] - b[0],
                                estilo(html=1, points='[]', perimeter='orthogonalPerimeter', outlineConnect=0,
                                       fillColor=relleno, strokeColor=BORDE, strokeWidth=1.5))
                b[2] = c
        for gx, gy, g in self.guardas:
            self._txt(gx, gy, tw(plano(g), hfs, True) + 20, self.lh + 6, g, hfs, negrita=True, fondo=True, nowrap=True)
        for m in self.mensajes:
            if m['k'] == 'msg':
                self._dibuja_msg(m)
            else:
                self._dibuja_auto(m)
        for t in self.textos:
            if t.get('nota'):
                nota(doc, t['t'], t['x'], t['y'], t['w'], t['h'], fs=t['fs'])
            else:
                self._txt(t['x'], t['y'], t['w'], t['h'], t['t'], t['fs'], t['align'], t['negrita'], t['color'],
                          t.get('rot', 0), t.get('fondo', False), t.get('cursiva', False))

    def _txt(self, x, y, w, hh, t, fs=13, align='left', negrita=False, color=TINTA, rot=0, fondo=False, cursiva=False,
             nowrap=False):
        st = estilo(text=None, html=1, whiteSpace='nowrap' if nowrap else 'wrap', align=align, verticalAlign='middle', fontSize=fs,
                    fontFamily=FUENTE, fontColor=color, fontStyle=(1 if negrita else 0) + (2 if cursiva else 0),
                    spacingLeft=2, spacingRight=2)
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
        e = doc.arista(bo[2], bd[2], self.st[m['tipo']], salida=(1 if der else 0, fo), entrada=(0 if der else 1, fd))
        lin = m['lineas'] or (m['texto'].count('<br>') + 1)
        dy = m['dy'] if m['dy'] is not None else -(lin * self.lh / 2 + 5)
        doc.etiqueta(e, m['texto'], x=0, dx=m['dx'], dy=dy, fs=self.fs)
        ancho = max(tw(plano(l), self.fs, '<b>' in l) for l in m['texto'].split('<br>'))
        hueco = abs(xd - xo) - BARRA_W
        if ancho > hueco + 4:
            self.avisos.append('mensaje "%s" mide %d y el hueco es %d' % (plano(m['texto']), ancho, hueco))

    def _dibuja_auto(self, m):
        doc = self.doc
        b1 = self._barra_en(m['p'], m['y1'])
        b2 = self._barra_en(m['p'], m['y2'])
        x = self.partes[m['p']]['x'] + BARRA_W / 2
        f1 = (m['y1'] - b1[0]) / (b1[1] - b1[0])
        f2 = (m['y2'] - b2[0]) / (b2[1] - b2[0])
        pts = [(x + m['L'], m['y1']), (x + m['L'], m['y2'])]
        e = doc.arista(b1[2], b2[2], self.st[m['tipo']], puntos=pts, salida=(1, f1), entrada=(1, f2))
        ancho = max(tw(plano(l), self.fs, '<b>' in l) for l in m['texto'].split('<br>'))
        doc.etiqueta(e, m['texto'], x=0, dx=ancho / 2 + 10, dy=0, fs=self.fs, fondo=True)
        # aviso si la etiqueta invade la linea de vida siguiente
        ks = list(self.partes)
        j = ks.index(m['p']) + 1 + m.get('cruza', 0)
        if j <= len(ks) - 1:
            libre = self.partes[ks[j]]['x'] - BARRA_W / 2 - 4 - (x + m['L'] + 10)
            if ancho > libre:
                self.avisos.append('autollamada "%s" mide %d y caben %d' % (plano(m['texto']), ancho, libre))


def etiqueta_hilo(s, x_barra, y1, y2):
    """Texto rojo girado junto a la barra del hilo secundario; se ancla arriba para no tocar la flecha final."""
    largo = 126
    cx = x_barra - 22
    cy = y1 + largo / 2 + 8 if (y2 - y1) >= largo + 24 else (y1 + y2) / 2
    s.texto(cx - largo / 2, cy - 12, largo, 24, 'hilo secundario', fs=14, align='center', negrita=True, color=ROJO,
            rot=270)


def cierra(s, y_fin, nombre):
    s.dibuja(y_fin)
    for a in s.avisos:
        print('AVISO %s: %s' % (nombre, a))
    print('%s: ancho %d, alto %d (a 15 cm: %.1f pt de letra, %.1f cm de alto)' % (
        nombre, s.x_max, y_fin, FS * 425.0 / s.x_max, y_fin / s.x_max * 15))


# =====================================================================================
#   06a-1  Lado Android: envio
# =====================================================================================
def construir_app_envio():
    nombre = '06a-secuencia-pedir-cita-app-1-envio'
    doc = Doc(nombre)
    s = SecL(doc)
    s.parte('cli', 'Cliente', actor=True)
    s.parte('frag', 'Fragmento<br>PedirCita')
    s.parte('vm', 'ModeloVista<br>PedirCita')
    s.parte('rep', 'Repositorio<br>Citas')
    s.parte('api', 'ClienteApi')
    s.parte('srv', 'API REST' + SUB % '(acceso y reserva)')

    s.gap(14)                                      # la etiqueta del actor ocupa sitio bajo la cabecera
    s.m('cli', 'frag', 'pulsa<br>«Pedir cita»')
    s.m('frag', 'vm', 'pedir(fecha, hora)')
    s.a('vm', 'accion(): operacion ←<br>Resultado.cargando()<br>(el botón se desactiva)', cruza=1)
    s.m('vm', 'rep', 'pedir(fecha,<br>hora, callback)')
    y_env = s.m('rep', 'api', 'enviar(POST,<br>/api/citas, cuerpo,<br>Cita::desdeJson,<br>callback)')
    y_t1, y_t2 = s.a('api', 'new Thread(…)<br>.start()', lazo=26, cruza=1)
    s.gap(6)
    y_post = s.m('api', 'srv', 'POST /api/citas<br>Bearer ‹JWT›')
    y_fin = s.gap(46)

    s.posiciona()
    s.barras['api'] = [[y_env - 10, y_t1 - 10, None], [y_t1 - 8, y_post + 14, None, ROJO]]
    s.barras['srv'] = [[y_post - 10, y_post + 30, None]]
    etiqueta_hilo(s, s.X('api'), y_t1 - 8, y_post + 14)
    cierra(s, y_fin + 6, nombre)
    return doc, nombre


# =====================================================================================
#   06a-2  Lado Android: respuesta
# =====================================================================================
def construir_app_respuesta():
    nombre = '06a-secuencia-pedir-cita-app-2-respuesta'
    doc = Doc(nombre)
    s = SecL(doc)
    # El repositorio no aparece: pasa el callback tal cual y ClienteApi responde directamente
    # al que creó accion() en el modelo de vista.
    s.parte('frag', 'Fragmento<br>PedirCita')
    s.parte('vm', 'ModeloVista<br>PedirCita')
    s.parte('api', 'ClienteApi')
    s.parte('srv', 'API REST' + SUB % '(acceso y reserva)')

    s.gap(24)                                    # sitio para la leyenda del hilo secundario
    y_alt0 = s.gap(8)
    s.gap(24)                                    # sitio para la pestana y la guarda del marco

    # rama 1: 201
    y_r1 = s.m('srv', 'api', '201 Created<br>+ JSON de la cita', tipo='ret')
    s.a('api', 'Cita::desdeJson', cruza=1)
    y_e1 = s.m('api', 'vm', 'onExito(cita)<br>con principal.post()')
    s.a('vm', 'operacion ← éxito(«Cita<br>pedida…») y recarga<br>días y huecos', cruza=1)
    s.m('vm', 'frag', 'operacion cambia', tipo='async')
    s.a('frag', 'Snackbar y<br>irAPestana(Mis citas)', cruza=1)
    y_sep1 = s.gap(14)

    # rama 2: 409
    s.gap(24)
    y_r2 = s.m('srv', 'api', '409 Conflict<br>{error: hueco_ocupado,<br>mensaje}', tipo='ret')
    s.a('api', 'leerError(409, …)<br>→ ErrorApi', cruza=1)
    y_e2 = s.m('api', 'vm', 'onError(error)<br>con principal.post()')
    s.a('vm', 'operacion ← error(error)<br>y, por ser 409, recarga<br>días y huecos', cruza=1)
    s.m('vm', 'frag', 'operacion cambia', tipo='async')
    s.a('frag', 'Snackbar con el<br>mensaje de la API', cruza=1)
    y_sep2 = s.gap(14)

    # rama 3: otros errores (400, 401, 403) y la API caída
    s.gap(24)
    y_r3 = s.m('srv', 'api', '400 · 401 · 403<br>{error, mensaje}', tipo='ret')
    s.a('api', 'leerError(…) o, sin<br>conexión, sin_conexion', cruza=1)
    y_e3 = s.m('api', 'vm', 'onError(error)<br>con principal.post()')
    s.a('vm', 'operacion ← error(error)', cruza=1)
    s.m('vm', 'frag', 'operacion cambia', tipo='async')
    s.a('frag', 'mostrarError(): con 401<br>vuelve al login, con<br>no_aprobado a la espera<br>y si no, Snackbar', cruza=1)
    y_alt1 = s.gap(14)
    y_fin = s.gap(20)

    s.posiciona()
    X = s.X
    s.barras['srv'] = [[y_r1 - 14, y_r1 + 10, None], [y_r2 - 14, y_r2 + 10, None], [y_r3 - 14, y_r3 + 10, None]]
    s.barras['api'] = [[y_r1 - 14, y_e1 + 12, None, ROJO], [y_r2 - 14, y_e2 + 12, None, ROJO],
                       [y_r3 - 14, y_e3 + 12, None, ROJO]]
    s.texto(X('frag') + 14, 76, 170, 24, '<font color="%s">■</font> hilo secundario' % ROJO, fs=15, align='left')

    x_der = max(X('srv') + 56, s.x_max + 8)
    gx = X('frag') - 60 + 54
    m = s.marco(X('frag') - 60, y_alt0, x_der, y_alt1, 'alt', '[201 Created · hueco libre]',
                ancho_tab=46, gx=gx)
    m['seps'].append((y_sep1, '[409 Conflict · hueco_ocupado]'))
    m['seps'].append((y_sep2, '[otro error: 400, 401, 403 o sin conexión]'))
    s.x_max = x_der
    cierra(s, y_fin, nombre)
    return doc, nombre


# =====================================================================================
#   06b  API: acceso (Enrutador + filtro)
# =====================================================================================
def construir_acceso():
    nombre = '06b-secuencia-pedir-cita-api-acceso'
    doc = Doc(nombre)
    s = SecL(doc)
    s.parte('api', 'ClienteApi' + SUB % '(app Android)')
    s.parte('rtr', 'Enrutador')
    s.parte('flt', 'Filtro<br>Autenticacion')
    s.parte('rep', 'Repositorio<br>Usuarios')
    s.parte('db', 'MySQL')

    y_post = s.m('api', 'rtr', 'POST /api/citas<br>Bearer ‹JWT›')
    s.a('rtr', 'elegirRuta()<br>(si no hay, 404 o 405)', cruza=1)
    s.a('rtr', 'comprobarAcceso(CLIENTE)', cruza=1)
    y_aut = s.m('rtr', 'flt', 'autenticar(cabecera)')
    s.a('flt', 'hay cabecera Bearer<br>(si no, 401 no_autenticado)', cruza=1)
    s.a('flt', 'tokenJwt.validar(token):<br>firma y caducidad<br>(si no, 401 token_invalido)', cruza=1)
    y_b1 = s.m('flt', 'rep', 'buscarPorId(id)')
    s.m('rep', 'db', 'SELECT … FROM<br>usuarios<br>WHERE id = ?')
    y_b2 = s.m('rep', 'flt', 'Optional&lt;Usuario&gt;', tipo='ret')
    s.a('flt', 'el usuario existe<br>(si no, 401 token_invalido)', cruza=1)
    y_ret = s.m('flt', 'rtr', 'Usuario', tipo='ret')
    s.a('rtr', 'estaAprobado()<br>(si no, 403 no_aprobado)', cruza=1)
    s.a('rtr', 'rol = CLIENTE<br>(si no, 403 sin_permiso)', cruza=1)
    y_end = s.gap(16)
    y_fin = y_end + 30

    s.posiciona()
    s.barras['api'] = [[y_post - 10, y_end, None]]
    s.barras['rtr'] = [[y_post - 10, y_end, None]]
    s.barras['flt'] = [[y_aut - 10, y_ret + 12, None]]
    s.barras['rep'] = [[y_b1 - 10, y_b2 + 10, None]]
    # la nota va debajo de las lineas de vida (no las cruza) y arranca en la del Enrutador
    n_x = s.X('rtr') - 20
    n_w = max(380, s.X('rep') + 70 - n_x)
    s.nota(n_x, y_fin + 8, n_w, 5 * s.lh + 14,
           'Si todo va bien, el Enrutador llama a <b>RutasCliente.pedirCita(intercambio, usuario)</b> y sigue '
           'en 06c. Si algo falla, recoge el <b>ErrorNegocio</b> y responde con su código HTTP y el JSON '
           '<b>{error, mensaje}</b>.')
    cierra(s, y_fin, nombre)
    return doc, nombre


# =====================================================================================
#   06c-1  API: reserva (1) · horizonte, bloqueo y comprobaciones
# =====================================================================================
def construir_reserva_validacion():
    nombre = '06c-secuencia-pedir-cita-api-reserva-1-validacion'
    doc = Doc(nombre)
    s = SecL(doc, hueco_barra=22)
    s.parte('rtr', 'RutasCliente')
    s.parte('srv', 'ServicioCitas')
    s.parte('hor', 'Servicio<br>Horarios')
    s.parte('rep', 'Repositorios' + SUB % '(JDBC)')
    s.parte('db', 'MySQL')

    s.gap(38)                                      # sitio para el texto «usuario ya autenticado»
    y_cu1, y_cu2 = s.a('rtr', 'lee fecha y hora<br>del cuerpo (si no, 400<br>json_invalido o<br>datos_invalidos)')
    y_call = s.m('rtr', 'srv', 'pedir(clienteId,<br>fecha, hora)')
    # RN-09 paso 6, antes del bloqueo
    y_hz = s.m('srv', 'hor', 'estaEnHorizonte(fecha)')
    s.a('srv', 'si no, 400<br>fecha_fuera_de_horizonte', cruza=1)
    y_crit0 = s.gap(14)
    s.gap(30)
    y_tx0 = s.y
    s.gap(24)
    s.m('srv', 'db', 'BEGIN')
    y_h1 = s.m('srv', 'hor', 'tramoDe(conexion, fecha)')
    y_h2 = s.m('hor', 'rep', 'excepcion(fecha)<br>y semanal()')
    y_h3 = s.m('rep', 'db', 'SELECT … FROM<br>excepciones_dia y<br>horarios_semanales')
    s.a('srv', 'valida, en orden: día abierto,<br>hora en el tramo, hora alineada<br>y hora aún no pasada', cruza=1)
    y_c1 = s.m('srv', 'rep', 'citas.contarActivas(fecha)')
    y_c2 = s.m('rep', 'db', 'SELECT COUNT(*) FROM citas<br>WHERE fecha = ? AND estado<br>IN (pendiente, confirmada)')
    y_o1 = s.m('srv', 'rep', 'citas.horasOcupadas(fecha)')
    y_o2 = s.m('rep', 'db', 'SELECT hora_ocupada FROM citas<br>WHERE fecha = ?')
    s.a('srv', 'cupo libre (n < cupo)<br>y hueco libre', cruza=1)
    # alt abreviado de errores de validacion
    y_alt0 = s.gap(22)
    s.gap(100)                                     # guarda y lista de codigos
    s.m('srv', 'db', 'ROLLBACK')
    s.a('srv', 'lanza ErrorNegocio(código)', cruza=1)
    y_sep = s.gap(18)
    y_ok = s.gap(24)                                # texto de la rama «todas se cumplen»
    y_alt1 = s.gap(8)
    y_tx1 = s.gap(14)
    y_crit1 = s.gap(14)
    y_end = s.gap(10)
    y_fin = y_end + 20

    s.posiciona(extra={('rtr', 'srv'): 164})
    X = s.X
    s.barras['rtr'] = [[y_cu1 - 10, y_end, None]]
    s.barras['srv'] = [[y_call - 10, y_end, None]]
    s.barras['hor'] = [[y_hz - 10, y_hz + 10, None], [y_h1 - 10, y_h2 + 10, None]]
    s.barras['rep'] = [[y_h2 - 10, y_h3 + 10, None], [y_c1 - 10, y_c2 + 10, None], [y_o1 - 10, y_o2 + 10, None]]
    gx = X('srv') + 14
    s.marco(X('srv') - 150, y_crit0, X('db') + 60, y_crit1, 'critical', '[synchronized (cerrojo(fecha))]',
            ancho_tab=78, gx=gx)
    s.marco(X('srv') - 132, y_tx0, X('db') + 46, y_tx1, 'transacción', '', ancho_tab=108, gx=gx)
    a = s.marco(X('srv') - 114, y_alt0, X('db') + 32, y_alt1, 'alt', '[falla una comprobación]', ancho_tab=46, gx=gx)
    a['seps'].append((y_sep, '[todas se cumplen: sigue la inserción y la respuesta]'))
    s.texto(X('srv') + 14, y_alt0 + 30, 340, 3 * s.lh + 6,
            '409 dia_cerrado, 400 hora_fuera_de_tramo,<br>400 hora_invalida, 400 hora_pasada,<br>'
            '409 dia_lleno o 409 hueco_ocupado',
            align='left', fondo=True)
    s.texto(X('rtr') - 50, 74, 100, 2 * s.lh + 4, 'usuario ya<br>autenticado', align='center', color=GRIS_TXT,
            fondo=True, cursiva=True)
    cierra(s, y_fin, nombre)
    return doc, nombre


# =====================================================================================
#   06c-2  API: reserva (2) · insercion y respuesta
# =====================================================================================
def construir_reserva_insercion():
    nombre = '06c-secuencia-pedir-cita-api-reserva-2-insercion'
    doc = Doc(nombre)
    s = SecL(doc, hueco_barra=60)
    s.parte('api', 'ClienteApi' + SUB % '(app Android)')
    s.parte('rtr', 'Enrutador')
    s.parte('rut', 'RutasCliente')
    s.parte('srv', 'ServicioCitas')
    s.parte('rep', 'Repositorios' + SUB % '(JDBC)')
    s.parte('db', 'MySQL')

    y_call = s.gap(10)
    y_crit0 = s.gap(12)
    s.gap(30)
    y_tx0 = s.y
    s.gap(24)
    y_i1 = s.m('srv', 'rep', 'citas.insertar<br>(cita)')
    y_i2 = s.m('rep', 'db', "INSERT INTO citas (…,<br>estado = 'pendiente')")
    y_alt0 = s.gap(22)
    s.gap(34)
    y_k1 = s.m('rep', 'srv', 'id', tipo='ret')
    y_k2 = s.m('srv', 'rep', 'citas.buscarPorId(id)')
    y_k3 = s.m('rep', 'srv', 'Cita', tipo='ret')
    s.gap(8)
    s.m('srv', 'db', 'COMMIT')
    y_sep = s.gap(18)
    s.gap(16)
    s.gap(10)
    y_e1 = s.m('db', 'rep', 'error 1062<br>(uq_citas_hueco_activo)', tipo='ret')
    y_e0 = s.m('rep', 'srv', 'SQLException', tipo='ret')
    s.a('srv', 'esDuplicado(e): lanza<br>hueco_ocupado', cruza=1)
    s.gap(8)
    y_e2 = s.m('srv', 'db', 'ROLLBACK')
    y_alt1 = s.gap(14)
    y_tx1 = s.gap(14)
    y_crit1 = s.gap(14)

    # respuesta HTTP: el 201 lo escribe RutasCliente y los errores el Enrutador, que es quien
    # recoge el ErrorNegocio
    y_r0 = s.gap(18)
    s.gap(34)
    s.m('srv', 'rut', 'Cita (pendiente)', tipo='ret')
    y_ok = s.m('rut', 'api', '201 Created<br>+ JSON de la cita', tipo='ret')
    y_rsep1 = s.gap(18)
    s.gap(16)
    s.m('srv', 'rtr', 'ErrorNegocio<br>(código)', tipo='ret')
    s.m('rtr', 'api', '400 o 409<br>{error, mensaje}', tipo='ret')
    y_rsep2 = s.gap(18)
    s.gap(16)
    s.m('srv', 'rtr', 'ErrorNegocio<br>hueco_ocupado', tipo='ret')
    y_u = s.m('rtr', 'api', '409 Conflict<br>{error:<br>hueco_ocupado}', tipo='ret')
    y_srv_fin = s.gap(8)
    y_r1 = s.gap(18)
    y_fin = y_r1 + 20

    s.posiciona(extra={('rut', 'srv'): 164})
    X = s.X
    s.barras['rtr'] = [[y_call - 4, y_r1 - 20, None]]
    s.barras['rut'] = [[y_call - 4, y_ok + 10, None]]
    s.barras['srv'] = [[y_call - 4, y_srv_fin, None]]
    s.barras['rep'] = [[y_i1 - 10, y_i2 + 10, None], [y_k1 - 10, y_k3 + 10, None], [y_e1 - 10, y_e0 + 10, None]]
    gx = X('srv') + 14
    s.marco(X('srv') - 150, y_crit0, X('db') + 60, y_crit1, 'critical',
            '[synchronized (cerrojo(fecha))] · continuación', ancho_tab=78, gx=gx)
    s.marco(X('srv') - 132, y_tx0, X('db') + 46, y_tx1, 'transacción', '', ancho_tab=108, gx=gx)
    a = s.marco(X('srv') - 114, y_alt0, X('db') + 32, y_alt1, 'alt', '[fila insertada]', ancho_tab=46, gx=gx)
    a['seps'].append((y_sep, '[error 1062: el hueco ya estaba ocupado]'))
    r = s.marco(X('api') - 60, y_r0, X('db') + 60, y_r1, 'alt', '[cita creada]', ancho_tab=46, gx=gx)
    r['seps'].append((y_rsep1, '[falla una comprobación previa]'))
    r['seps'].append((y_rsep2, '[error 1062: hueco ocupado]'))
    cierra(s, y_fin, nombre)
    return doc, nombre


def construir():
    return [construir_app_envio(), construir_app_respuesta(), construir_acceso(),
            construir_reserva_validacion(), construir_reserva_insercion()]
