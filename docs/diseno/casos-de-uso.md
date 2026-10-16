# Casos de uso

Borrador para revisión · 1 de octubre de 2026 · Fuentes: `docs/PLAN-TFG-Clandestino-Android.md` (§4 y §7), `api-contrato.md`, `reglas-negocio.md` y `requisitos.md`.

Son los diez casos del diagrama, todos P1, con los nombres del plan §7. Lo que es P2 o P3 (cambiar @usuario, cita manual, eliminar cliente, resumen del mes...) queda fuera del diagrama y de esta tabla: está en `requisitos.md` §4.

Cada caso es una tabla de dos columnas pensada para pasar a Word con letra de 10 pt y salir en unas cuatro páginas en total. Las pantallas (A1 a D4) son las del plan §7. Los códigos de error son los de `api-contrato.md` §5; el número entre paréntesis es el código HTTP.

## 1. Actores y relaciones para el diagrama

| Actor | Quién es | Casos de uso |
|---|---|---|
| Visitante | Persona invitada que todavía no tiene cuenta. | UC01 |
| Cliente | Usuario con rol `cliente`. Para usar UC03, UC04 y UC05 necesita estado `aprobado`. | UC02 (como Usuario registrado), UC03, UC04, UC05 |
| Administrador | El barbero: único usuario con rol `admin`. | UC02 (como Usuario registrado), UC06, UC07, UC08, UC09, UC10 |

Para dibujarlo en draw.io:

- Límite del sistema: «Clandestino». Actores a la izquierda (Visitante, Cliente) y a la derecha (Administrador).
- «Usuario registrado» es una generalización de Cliente y Administrador: así UC02 se conecta una sola vez.
- UC04 «include» UC03: para pedir una cita siempre se consulta antes la disponibilidad.
- Ningún otro «include» ni «extend»: «iniciar sesión» no se repite como inclusión en cada caso, para no cruzar líneas.
- Los diez casos se numeran como en las tablas siguientes y sin «Figura N» dentro del dibujo.

## 2. Descripción de los casos de uso

| UC01 | Registrarse con código |
|---|---|
| **Actor** | Visitante |
| **Precondición** | Tiene un código de invitación vigente que le ha dado el administrador. |
| **Flujo básico** | 1. Abre A2, donde ve el aviso de privacidad (responsable, finalidad y derechos), y escribe código, @usuario, teléfono y contraseña.<br>2. La app valida el formato mientras escribe.<br>3. Envía `POST /api/auth/registro`.<br>4. La API comprueba la invitación y crea el usuario `cliente` en estado `pendiente`.<br>5. La API devuelve el token y los datos del usuario.<br>6. La app guarda la sesión y muestra A3 (espera). |
| **Alternativos y errores** | 4a. Formato incorrecto: `datos_invalidos` (400).<br>4b. Código inexistente: `invitacion_invalida` (400); ya usado: `invitacion_usada` (409); pasado de fecha: `invitacion_caducada` (409).<br>4c. @usuario o teléfono ya registrados: `duplicado` (409); la invitación no se consume.<br>4d. Demasiados intentos: `demasiados_intentos` (429). |
| **Postcondición** | Existe un `cliente` `pendiente`. La invitación queda `usada` con `usada_por`. Hay sesión iniciada. |

| UC02 | Iniciar sesión |
|---|---|
| **Actor** | Cliente o Administrador (usuario registrado) |
| **Precondición** | Tiene cuenta, en cualquier estado. |
| **Flujo básico** | 1. Abre A1 (se omite si hay un token guardado que `GET /api/yo` acepta).<br>2. Escribe @usuario y contraseña.<br>3. Envía `POST /api/auth/login`.<br>4. La API verifica el hash y devuelve un token.<br>5. La app lo guarda cifrado y llama a `GET /api/yo`.<br>6. Navega según rol y estado: administrador a D1; cliente `aprobado` a C1; `pendiente` o `bloqueado` a A3, que muestra el texto de espera o el de bloqueo según el estado. |
| **Alternativos y errores** | 1a. Token guardado rechazado: `token_invalido` (401); la app lo borra y muestra A1.<br>3a. Campos vacíos: `datos_invalidos` (400).<br>3b. Usuario o contraseña incorrectos: `credenciales_invalidas` (401), con el mismo mensaje en ambos casos.<br>3c. Demasiados intentos: `demasiados_intentos` (429). |
| **Postcondición** | Sesión activa. «Cerrar sesión» (C3) borra el token del dispositivo y no llama a la API. |

| UC03 | Consultar disponibilidad |
|---|---|
| **Actor** | Cliente |
| **Precondición** | Sesión iniciada y estado `aprobado`. |
| **Flujo básico** | 1. En C2 la app pide `GET /api/dias`.<br>2. Muestra cada día del horizonte como Libre, Completo o Vacaciones; los demás salen deshabilitados.<br>3. El cliente elige un día Libre.<br>4. La app pide `GET /api/huecos?fecha`.<br>5. Muestra las horas del día; las ocupadas salen deshabilitadas. |
| **Alternativos y errores** | 1a. Cliente `pendiente` o `bloqueado`: `no_aprobado` (403); la app muestra A3.<br>1b. Sesión caducada: `token_invalido` (401); la app vuelve a A1.<br>4a. Fecha mal formada: `datos_invalidos` (400); fuera del horizonte: `fecha_fuera_de_horizonte` (400).<br>*. Sin conexión: la app muestra un mensaje y permite reintentar. |
| **Postcondición** | Los datos no cambian. El cliente conoce los días y las horas libres. |

| UC04 | Solicitar cita |
|---|---|
| **Actor** | Cliente |
| **Precondición** | Estado `aprobado` y ha elegido un hueco libre (UC03). |
| **Flujo básico** | 1. En C2 pulsa «Pedir cita · día · hora».<br>2. La app envía `POST /api/citas` con `fecha` y `hora_pedida`.<br>3. La API valida horizonte, día abierto, tramo, alineación, hora pasada, cupo y hueco, en ese orden.<br>4. Inserta la cita en estado `pendiente`.<br>5. Responde 201 con la cita.<br>6. La app informa de que la solicitud está enviada y muestra la cita pendiente en C1. |
| **Alternativos y errores** | 3a. `fecha_fuera_de_horizonte`, `hora_fuera_de_tramo`, `hora_invalida` o `hora_pasada` (400).<br>3b. `dia_cerrado`, `dia_lleno` o `hueco_ocupado` (409): la app recarga los huecos.<br>3c. Formato incorrecto: `datos_invalidos` (400).<br>3d. Cliente no aprobado: `no_aprobado` (403). |
| **Postcondición** | Hay una cita nueva `pendiente` que ocupa hueco y cupo hasta que se decida. |

| UC05 | Gestionar mis citas (ver y cancelar) |
|---|---|
| **Actor** | Cliente |
| **Precondición** | Estado `aprobado`. |
| **Flujo básico** | 1. En C1 la app pide `GET /api/mis-citas`.<br>2. Muestra una tarjeta por cita con día, hora y estado en texto, sin precio ni propina (RN-18).<br>3. En una cita `pendiente` o `confirmada`, el cliente pulsa Cancelar.<br>4. La app pide confirmación en un diálogo.<br>5. Envía `POST /api/citas/{id}/cancelar`.<br>6. La cita pasa a `cancelada` y la lista se refresca. |
| **Alternativos y errores** | 5a. Cita ajena o inexistente: `no_encontrado` (404).<br>5b. Cita que ya no está activa: `estado_invalido` (409).<br>5c. Cita cuya hora ya ha pasado: `cita_pasada` (409).<br>5d. `no_aprobado` (403) o `token_invalido` (401): igual que en UC03. |
| **Postcondición** | La cita queda `cancelada` y su hueco y su cupo se liberan al instante. |

| UC06 | Generar invitación |
|---|---|
| **Actor** | Administrador |
| **Precondición** | Sesión de administrador. |
| **Flujo básico** | 1. En D4 (Invitaciones) escribe una nota opcional y pulsa «Generar código».<br>2. La app envía `POST /api/admin/invitaciones`.<br>3. La API crea un código aleatorio de 12 caracteres, válido 7 días y de un solo uso.<br>4. La app muestra el código para que el administrador se lo entregue al cliente.<br>5. D4 lista las invitaciones con `GET /api/admin/invitaciones`: cada fila muestra el código, la nota y el estado (Pendiente, Usada o Caducada). La recién creada aparece como Pendiente. «Caducada» no es un estado guardado: es una invitación `pendiente` con `caducada` verdadero (RN-22). |
| **Alternativos y errores** | 2a. Nota de más de 60 caracteres: `datos_invalidos` (400).<br>2b. Sesión de cliente: `sin_permiso` (403).<br>2c. Sesión caducada: `token_invalido` (401). |
| **Postcondición** | Existe una invitación `pendiente` con `creada_por` igual al administrador y `caduca_en` a 7 días. |

| UC07 | Aprobar o bloquear cliente |
|---|---|
| **Actor** | Administrador |
| **Precondición** | Sesión de administrador y al menos un cliente registrado. |
| **Flujo básico** | 1. En D4 (Clientes) la app pide `GET /api/admin/clientes`.<br>2. El administrador elige un cliente y pulsa Aprobar o Bloquear.<br>3. La app envía `POST /api/admin/clientes/{id}/aprobar` o `POST /api/admin/clientes/{id}/bloquear`.<br>4. La API cambia el estado (y guarda `aprobado_en` al aprobar).<br>5. La lista se actualiza. |
| **Alternativos y errores** | 3a. Cliente inexistente: `no_encontrado` (404).<br>3b. Ya estaba en ese estado: `estado_invalido` (409).<br>3c. Sesión de cliente: `sin_permiso` (403). |
| **Postcondición** | El estado queda cambiado y rige desde la siguiente petición del cliente. Sus citas existentes no se modifican. |

| UC08 | Gestionar solicitudes (confirmar o rechazar) |
|---|---|
| **Actor** | Administrador |
| **Precondición** | Existen citas `pendiente`. |
| **Flujo básico** | 1. En D1 la app pide `GET /api/admin/citas?estado=pendiente`.<br>2. Cada tarjeta muestra @usuario, día y hora pedida.<br>3. El administrador acepta la hora pedida o elige otra hora final.<br>4. Confirma con `POST /api/admin/citas/{id}/confirmar`, o rechaza (con diálogo) con `POST /api/admin/citas/{id}/rechazar`.<br>5. La cita pasa a `confirmada` o `rechazada` y sale de la bandeja. |
| **Alternativos y errores** | 4a. Otra cita activa a esa hora: `hueco_ocupado` (409).<br>4b. La cita ya no está `pendiente`: `estado_invalido` (409); su hora ya ha pasado: `cita_pasada` (409).<br>4c. Hora final con formato o rango incorrectos: `datos_invalidos` (400).<br>4d. Cita inexistente: `no_encontrado` (404). |
| **Postcondición** | La cita queda `confirmada` con `hora_final` y `decidida_en`, o `rechazada` con `decidida_en` (libera hueco y cupo). |

| UC09 | Gestionar la agenda (asistencia y cancelación) |
|---|---|
| **Actor** | Administrador |
| **Precondición** | Existen citas `confirmada`. |
| **Flujo básico** | 1. En D2 (semana o mes) la app pide `GET /api/admin/citas?estado=confirmada&desde&hasta`: la agenda solo carga citas `confirmada`.<br>2. El administrador elige una cita.<br>3. Si su hora ya ha pasado, pulsa «Asistió» o «No vino»: `POST /api/admin/citas/{id}/asistencia` con `resultado`.<br>4. Si es futura, pulsa Cancelar y confirma en el diálogo: `POST /api/admin/citas/{id}/cancelar`.<br>5. La cita deja de ser `confirmada` y sale de la agenda al recargarla. |
| **Alternativos y errores** | 3a. Asistencia antes de la hora de la cita: `cita_futura` (409).<br>3b. `resultado` desconocido: `datos_invalidos` (400).<br>4a. Cancelar una cita que ya ha pasado: `cita_pasada` (409).<br>*. La cita no está `confirmada`: `estado_invalido` (409). Cita inexistente: `no_encontrado` (404). |
| **Postcondición** | La cita queda `hecha` (con `precio` igual al `precio_corte` del cliente), `no_asistio` o `cancelada` (esta libera hueco y cupo). |

| UC10 | Configurar horarios y vacaciones |
|---|---|
| **Actor** | Administrador |
| **Precondición** | Sesión de administrador. |
| **Flujo básico** | 1. En D3 la app pide `GET /api/admin/horarios` y `GET /api/admin/excepciones`.<br>2. Por cada día de la semana fija activo, hora de inicio, hora de fin y cupo, y guarda con `PUT /api/admin/horarios`.<br>3. Para vacaciones marca uno o varios días y guarda una excepción `cerrado` (o `abierto`, con tramo y cupo) con `POST /api/admin/excepciones`.<br>4. Para quitar una excepción: `DELETE /api/admin/excepciones/{id}`.<br>5. Los cambios rigen desde ese momento en UC03 y UC04. |
| **Alternativos y errores** | 2a. Inicio igual o posterior al fin, `dia_semana` fuera de 1 a 7, cupo fuera de rango, día pasado o excepción `cerrado` con horas: `datos_invalidos` (400).<br>4a. Excepción inexistente: `no_encontrado` (404).<br>*. Sesión de cliente: `sin_permiso` (403). |
| **Postcondición** | Horario y excepciones actualizados; una excepción manda sobre el horario de su fecha. Las citas ya creadas no se modifican. |

## 3. Trazabilidad

| UC | Pantalla | Endpoints | Requisitos |
|---|---|---|---|
| UC01 Registrarse con código | A2, luego A3 | `POST /api/auth/registro`; `GET /api/yo` | RF-01, RF-02, RF-03, RF-04, RF-08 |
| UC02 Iniciar sesión | A1 (y C3 para cerrar sesión) | `POST /api/auth/login`; `GET /api/yo` | RF-04, RF-05, RF-06, RF-07, RF-08 |
| UC03 Consultar disponibilidad | C2 | `GET /api/dias`; `GET /api/huecos` | RF-09, RF-10, RF-11 |
| UC04 Solicitar cita | C2 | `POST /api/citas` (y los de UC03) | RF-12, RF-13, RF-14 |
| UC05 Gestionar mis citas | C1 | `GET /api/mis-citas`; `POST /api/citas/{id}/cancelar` | RF-15, RF-16 |
| UC06 Generar invitación | D4 | `POST /api/admin/invitaciones`; `GET /api/admin/invitaciones` | RF-17, RF-18 |
| UC07 Aprobar o bloquear cliente | D4 | `GET /api/admin/clientes`; `POST /api/admin/clientes/{id}/aprobar`; `POST /api/admin/clientes/{id}/bloquear` | RF-08, RF-19, RF-20, RF-21 |
| UC08 Gestionar solicitudes | D1 | `GET /api/admin/citas?estado=pendiente`; `POST /api/admin/citas/{id}/confirmar`; `POST /api/admin/citas/{id}/rechazar` | RF-22, RF-23, RF-24 |
| UC09 Gestionar la agenda | D2 | `GET /api/admin/citas?estado=confirmada&desde&hasta`; `POST /api/admin/citas/{id}/asistencia`; `POST /api/admin/citas/{id}/cancelar` | RF-25, RF-26, RF-27 |
| UC10 Configurar horarios y vacaciones | D3 | `GET /api/admin/horarios`; `PUT /api/admin/horarios`; `GET /api/admin/excepciones`; `POST /api/admin/excepciones`; `DELETE /api/admin/excepciones/{id}` | RF-11, RF-28, RF-29 |
| Sin caso de uso | — | `GET /api/salud` (comprobación del Anexo I) | RF-30 |

C3 (Ajustes) no tiene caso de uso propio: cerrar sesión es una operación local de UC02 y el tema claro u oscuro es el requisito RNF-USA-08. A3 se alimenta de `GET /api/yo` y la usan UC01 y UC02.
