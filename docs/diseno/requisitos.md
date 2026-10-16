# Requisitos

Borrador para revisión · 1 de octubre de 2026 · Fuentes: `docs/PLAN-TFG-Clandestino-Android.md` (§3, §4, §7 y §8), `database/schema.sql` y `analisis/*.json`.

Los requisitos funcionales se llaman `RF-xx` y cada uno enlaza con su caso de uso (`UC01` a `UC10`, en `casos-de-uso.md`). Los no funcionales se llaman `RNF-<grupo>-xx`. Las reglas `RN-xx` y las decisiones de diseño cerradas `DP-xx` están en `reglas-negocio.md`; las rutas y los códigos de error, en `api-contrato.md`.

La tabla de requisitos funcionales y las de requisitos no funcionales recogen solo lo que es P1. Lo que el plan clasifica como P2 o P3 está aparte, en la tabla marcada del §4.

Tipos de verificación de los requisitos no funcionales: **T** test automático (JUnit); **M** prueba manual con captura; **I** inspección de código, de configuración o de documentación; **X** medición. Las cifras de rendimiento y de accesibilidad son objetivos del proyecto que se miden y se documentan; no vienen del plan.

## 1. Requisitos funcionales (P1)

### 1.1 Acceso y sesión

| ID | Requisito | UC | Criterio de aceptación |
|---|---|---|---|
| RF-01 | El sistema permite registrarse con un código de invitación, un @usuario, un teléfono y una contraseña. | UC01 | Con un código vigente y sin usar, `POST /api/auth/registro` devuelve 201, crea un usuario con rol `cliente` y estado `pendiente` y devuelve un token. Los códigos inexistentes, usados y caducados se rechazan con `invitacion_invalida`, `invitacion_usada` e `invitacion_caducada`. |
| RF-02 | El alta del usuario y el consumo de la invitación son atómicos, y la invitación es de un solo uso. Regla: RN-21, RN-22. | UC01 | Tras un registro correcto la invitación queda `usada` con `usada_por` igual al nuevo usuario. Si el registro falla (por ejemplo, `duplicado`), sigue `pendiente` y puede reutilizarse. Con dos registros simultáneos con el mismo código, uno recibe 201 y el otro `invitacion_usada`. |
| RF-03 | El @usuario y el teléfono son únicos. | UC01 | Registrar un @usuario o un teléfono ya existentes devuelve `duplicado` (409) sin indicar cuál de los dos. Lo garantizan `uq_usuarios_usuario` y `uq_usuarios_telefono`. |
| RF-04 | Los datos de alta y de acceso se validan en la app mientras se escriben y de nuevo en el servidor. | UC01, UC02 | @usuario según `[a-z0-9_]{3,20}` tras normalizar; teléfono en formato internacional; contraseña de 8 a 128 caracteres. Con datos incorrectos la app deshabilita el envío y muestra el error junto al campo; si la petición llega al servidor, devuelve `datos_invalidos` (400). A2 muestra además el aviso de privacidad antes del botón de registro (RNF-PRI-05, DP-10). |
| RF-05 | El usuario inicia sesión con @usuario y contraseña. | UC02 | Con credenciales correctas, `POST /api/auth/login` devuelve 200 con un token. Con un usuario inexistente o con una contraseña errónea devuelve `credenciales_invalidas` (401), con el mismo mensaje en los dos casos. |
| RF-06 | La sesión persiste entre ejecuciones y se puede cerrar. | UC02 | Al reabrir la app con un token vigente no se muestra A1. Una respuesta 401 borra el token y lleva a A1. «Cerrar sesión» en C3 borra el token del dispositivo y lleva a A1. |
| RF-07 | Tras iniciar sesión, la app muestra la pantalla que corresponde al rol y al estado. | UC02 | Administrador → D1; cliente `aprobado` → C1; cliente `pendiente` o `bloqueado` → A3. El destino se decide con `GET /api/yo`. |
| RF-08 | La pantalla A3 informa de si la cuenta está pendiente o bloqueada y permite comprobar el estado. | UC01, UC02, UC07 | A3 tiene dos variantes con un texto distinto: `pendiente` (la cuenta espera la aprobación del barbero) y `bloqueado` (el acceso está bloqueado). Las dos se actualizan al abrir la app y al deslizar para refrescar (`GET /api/yo`). Si el estado pasa a `aprobado`, la app abre C1. Un cliente `pendiente` o `bloqueado` no tiene acceso a ninguna otra función. |

### 1.2 Disponibilidad

| ID | Requisito | UC | Criterio de aceptación |
|---|---|---|---|
| RF-09 | El cliente consulta los días del horizonte y su estado. Regla: RN-05, RN-06. | UC03 | `GET /api/dias` devuelve los 14 días de hoy a hoy + 13, cada uno `libre`, `completo`, `vacaciones` o `cerrado`. C2 muestra Libre, Completo y Vacaciones y deshabilita los demás. |
| RF-10 | El cliente consulta los huecos de un día. Regla: RN-04. | UC03 | `GET /api/huecos` devuelve los huecos de una hora del tramo con su campo `ocupado`. Con un tramo 14:00–20:00 devuelve 6 huecos (14:00 a 19:00). C2 deshabilita los ocupados. |
| RF-11 | La disponibilidad de un día se resuelve con la precedencia excepción, horario semanal activo, cerrado. Regla: RN-01, RN-02. | UC03, UC10 | Cuatro casos de prueba: excepción `cerrado` con horario activo, día `vacaciones`; excepción `abierto` con horario inactivo, día abierto con el tramo y el cupo de la excepción; sin excepción y con horario activo, el del horario; sin ninguno, `cerrado`. |

### 1.3 Citas del cliente

| ID | Requisito | UC | Criterio de aceptación |
|---|---|---|---|
| RF-12 | El cliente solicita una cita para un día y una hora, y la cita queda pendiente. Regla: RN-10. | UC04 | Un `POST /api/citas` válido devuelve 201 con `estado` `pendiente` y `hora_final` nula. La cita aparece en `GET /api/mis-citas` y ocupa hueco y cupo. |
| RF-13 | Las validaciones de la solicitud se aplican en un orden fijo y cada una con su código de error. | UC04 | Hay al menos un test por cada código de validación de los pasos 5 a 12 de la tabla de RN-09 (`json_invalido`, `cuerpo_demasiado_grande`, `datos_invalidos`, `fecha_fuera_de_horizonte`, `dia_cerrado`, `hora_fuera_de_tramo`, `hora_invalida`, `hora_pasada`, `dia_lleno`, `hueco_ocupado`) y un test que comprueba el orden cuando fallan dos a la vez. |
| RF-14 | El cupo diario y el hueco no se pueden superar con peticiones simultáneas. Regla: RN-07, RN-24. | UC04 | Test 1: con cupo 1 y 20 peticiones simultáneas a horas distintas del mismo día se crea exactamente 1 cita. Test 2: con 20 peticiones simultáneas al mismo hueco se crea exactamente 1 cita y las demás reciben `hueco_ocupado`. |
| RF-15 | El cliente ve sus citas con su estado. | UC05 | `GET /api/mis-citas` devuelve solo citas propias: las de los últimos 30 días y las futuras. Cada tarjeta de C1 muestra día, hora y el estado en texto, sin precio ni propina: la respuesta no incluye `precio`, `propina` ni `decidida_en` (RN-18). |
| RF-16 | El cliente cancela una cita propia pendiente o confirmada. Regla: RN-15. | UC05 | La app pide confirmación. Tras `POST /api/citas/{id}/cancelar` la cita está `cancelada` y su hueco y su cupo quedan libres: una nueva solicitud a esa hora devuelve 201. Cancelar una cita de otro cliente devuelve `no_encontrado`. |

### 1.4 Invitaciones y clientes

| ID | Requisito | UC | Criterio de aceptación |
|---|---|---|---|
| RF-17 | El administrador genera invitaciones. Regla: RN-20. | UC06 | `POST /api/admin/invitaciones` devuelve 201 con un código de 12 caracteres, `estado` `pendiente` y `caduca_en` a 7 días de `creada_en`. Dos invitaciones seguidas tienen códigos distintos. |
| RF-18 | El administrador consulta las invitaciones y su estado. | UC06 | `GET /api/admin/invitaciones` lista código, nota, `estado`, `caducada` y quién la usó. Una invitación `pendiente` con `caduca_en` pasada sale con `caducada` verdadero. D4 muestra cada invitación con su código, su nota y un chip de estado con texto: Pendiente, Usada o Caducada. |
| RF-19 | El administrador consulta los clientes. | UC07 | `GET /api/admin/clientes` lista solo usuarios con rol `cliente`, con su estado. El filtro `estado` devuelve solo los de ese estado. |
| RF-20 | El administrador aprueba clientes. Regla: RN-23. | UC07 | Tras `POST /api/admin/clientes/{id}/aprobar`, el cliente queda `aprobado` con `aprobado_en` relleno, y su siguiente petición a `GET /api/dias` devuelve 200 en lugar de `no_aprobado`. |
| RF-21 | El administrador bloquea clientes. Regla: RN-23. | UC07 | Tras `POST /api/admin/clientes/{id}/bloquear`, la siguiente petición del cliente con el mismo token devuelve `no_aprobado` en todas las rutas salvo `GET /api/yo`. Sus citas existentes no cambian de estado. |

### 1.5 Solicitudes y agenda

| ID | Requisito | UC | Criterio de aceptación |
|---|---|---|---|
| RF-22 | El administrador consulta las solicitudes pendientes. | UC08 | `GET /api/admin/citas?estado=pendiente` devuelve las citas `pendiente` con @usuario, fecha y hora pedida, ordenadas por fecha y hora. |
| RF-23 | El administrador confirma una solicitud con una hora final. Regla: RN-12, RN-13. | UC08 | `POST /api/admin/citas/{id}/confirmar` con `hora_final` deja la cita `confirmada` con esa `hora_final` y `decidida_en`. Sin `hora_final`, queda igual a la pedida. Si otra cita activa tiene esa fecha y hora, devuelve `hueco_ocupado` y la cita sigue `pendiente`. La hora pedida original queda libre para otra solicitud. |
| RF-24 | El administrador rechaza una solicitud. Regla: RN-14. | UC08 | `POST /api/admin/citas/{id}/rechazar` deja la cita `rechazada` con `decidida_en` y su hora queda libre para otra solicitud. La app pide confirmación. |
| RF-25 | El administrador consulta la agenda por semana o por mes. | UC09 | `GET /api/admin/citas?estado=confirmada&desde&hasta` con las fechas de la semana o del mes visibles devuelve solo las citas `confirmada` de ese intervalo, ordenadas por fecha y hora. D2 no muestra citas `hecha`, `no_asistio` ni `cancelada`: una cita sale de la agenda en cuanto deja de estar `confirmada`. |
| RF-26 | El administrador registra la asistencia de una cita pasada. Regla: RN-17, RN-18. | UC09 | `POST /api/admin/citas/{id}/asistencia` con `hecha` deja la cita `hecha` con `precio` igual al `precio_corte` del cliente en ese momento (15,00 por defecto); con `no_asistio`, `precio` queda nulo. Antes de la hora de la cita devuelve `cita_futura`. Tras marcarla, la cita sale de la agenda al recargarla. |
| RF-27 | El administrador cancela una cita confirmada futura. Regla: RN-16. | UC09 | `POST /api/admin/citas/{id}/cancelar` deja la cita `cancelada`, libera hueco y cupo, y la cita sale de la agenda. Una cita ya pasada devuelve `cita_pasada`. La app pide confirmación. |

### 1.6 Horarios y vacaciones

| ID | Requisito | UC | Criterio de aceptación |
|---|---|---|---|
| RF-28 | El administrador define el horario semanal. Regla: RN-03, RN-08. | UC10 | `PUT /api/admin/horarios` guarda tramo, cupo y `activo` por día (`dia_semana` de 1 a 7). Con `hora_inicio` igual o posterior a `hora_fin` devuelve `datos_invalidos` y no guarda nada. El cambio se refleja en `GET /api/dias` y `GET /api/huecos` desde la petición siguiente. |
| RF-29 | El administrador gestiona excepciones y vacaciones. Regla: RN-02, RN-08. | UC10 | `POST /api/admin/excepciones` crea la excepción para una o varias fechas y sustituye la anterior de la misma fecha; `DELETE /api/admin/excepciones/{id}` la elimina. Las citas ya existentes esos días no se modifican. |

### 1.7 Soporte

| ID | Requisito | UC | Criterio de aceptación |
|---|---|---|---|
| RF-30 | La API informa de su estado. | — (Anexo I) | `GET /api/salud` devuelve 200 con `"estado": "ok"` y `"base_de_datos": "ok"` con la base de datos arrancada, y 503 `base_de_datos_no_disponible` con ella parada. |

### 1.8 Cobertura de casos de uso

| UC | Requisitos |
|---|---|
| UC01 | RF-01, RF-02, RF-03, RF-04, RF-08 |
| UC02 | RF-04, RF-05, RF-06, RF-07, RF-08 |
| UC03 | RF-09, RF-10, RF-11 |
| UC04 | RF-12, RF-13, RF-14 |
| UC05 | RF-15, RF-16 |
| UC06 | RF-17, RF-18 |
| UC07 | RF-08, RF-19, RF-20, RF-21 |
| UC08 | RF-22, RF-23, RF-24 |
| UC09 | RF-25, RF-26, RF-27 |
| UC10 | RF-11, RF-28, RF-29 |

## 2. Requisitos no funcionales (P1)

### 2.1 Seguridad

| ID | Requisito | Criterio de aceptación | Ver. |
|---|---|---|---|
| RNF-SEG-01 | Las contraseñas se guardan con PBKDF2-HMAC-SHA256, sal aleatoria y 600 000 iteraciones. | Ningún `password_hash` guarda la contraseña en claro: todos empiezan por `pbkdf2-sha256$600000$`. Dos usuarios con la misma contraseña tienen hashes distintos. Calcular un hash tarda entre 0,3 y 1,5 s en el equipo de desarrollo (el plan midió de 0,5 a 0,8 s). | T, X |
| RNF-SEG-02 | La sesión usa un JWT HS256 con caducidad. | Un token con la firma alterada, mal formado o caducado recibe `token_invalido` (un test por caso). La clave tiene al menos 32 bytes y no aparece en el repositorio (la búsqueda en Git no devuelve resultados). | T, I |
| RNF-SEG-03 | El estado del usuario se lee en la base de datos en cada petición. | Con un token vigente, tras bloquear al cliente su siguiente petición a cualquier ruta salvo `GET /api/yo` recibe `no_aprobado` (403). Un test parametrizado recorre todas las rutas del contrato con un cliente `pendiente`: todas dan 403 salvo `GET /api/yo` y las tres públicas. | T |
| RNF-SEG-04 | Cada endpoint comprueba el rol y la propiedad. | Con un token de cliente, el 100 % de las rutas `/api/admin/**` devuelve `sin_permiso` (403). Con un token de administrador, el 100 % de las rutas de cliente salvo `GET /api/yo` también. Un cliente que cancela la cita de otro recibe `no_encontrado`. | T |
| RNF-SEG-05 | Hay un limitador de intentos en el login y en el registro. | La petición fallida número 6 de una misma clave en 15 minutos recibe `demasiados_intentos` (429) con `Retry-After`. Un login correcto reinicia el contador de su clave. Valores en DP-08. | T |
| RNF-SEG-06 | Todas las consultas SQL van parametrizadas. | No hay ninguna sentencia que concatene datos de entrada (revisión del código de `repositorio`). Un login con `usuario` igual a `' OR '1'='1` no devuelve ningún usuario ni un error de SQL. | I, T |
| RNF-SEG-07 | Los códigos de invitación no se pueden adivinar. | Tienen 12 caracteres generados con `SecureRandom` del alfabeto sin caracteres confusos. Un test genera 10 000 códigos: ninguno repetido y todos dentro del alfabeto. Una invitación no se puede usar dos veces ni después de 7 días. | T |
| RNF-SEG-08 | Todo dato de entrada se valida en el servidor, aunque la app ya lo haya validado. | Cada campo de cada endpoint del contrato tiene al menos un test con un valor inválido que devuelve 400. Un cuerpo de más de 16 KiB devuelve `cuerpo_demasiado_grande`. | T |
| RNF-SEG-09 | Los errores no revelan información interna. | Los fallos inesperados devuelven siempre `error_interno`, sin traza. El login responde con el mismo código y cuerpo para un usuario inexistente y para una contraseña errónea, y la diferencia media de tiempo entre ambos casos (10 intentos de cada) es inferior a 100 ms. | T, X |
| RNF-SEG-10 | La app guarda la sesión de forma segura. | El token se cifra con AES-GCM y una clave del Android Keystore, y se guarda con su IV. El fichero de `SharedPreferences` no contiene el token legible. El manifiesto declara `allowBackup="false"`. | I, M |
| RNF-SEG-11 | El tráfico HTTP en claro se limita al entorno de desarrollo. | `network_security_config` permite HTTP en claro solo en la variante `debug` y solo hacia `10.0.2.2` y `localhost`; la variante `release` no lo permite. La memoria declara esta limitación (HTTPS es P2). | I |
| RNF-SEG-12 | Los secretos y las credenciales no están en el repositorio. | `.env.example` documenta `JWT_SECRET` y las credenciales de la base de datos sin valores reales, y `.gitignore` excluye `.env`. La API se conecta a MySQL con un usuario sin privilegios de administración. | I |
| RNF-SEG-13 | La memoria relaciona la seguridad con OWASP MASVS. | Incluye una tabla que enlaza cada medida de RNF-SEG-01 a RNF-SEG-12 con su requisito MASVS. | I |

### 2.2 Rendimiento y concurrencia

| ID | Requisito | Criterio de aceptación | Ver. |
|---|---|---|---|
| RNF-REN-01 | Las consultas de la API responden con rapidez. | Con 10 000 citas de prueba y 200 peticiones, el percentil 95 es inferior a 300 ms en `GET /api/dias`, `GET /api/huecos` y `GET /api/mis-citas`, e inferior a 500 ms en `POST /api/citas`, medido en el equipo de desarrollo. | X |
| RNF-REN-02 | El login y el registro responden en un tiempo razonable pese al hash lento. | Menos de 2 s por petición, incluido el hash PBKDF2 de 0,5 a 0,8 s. | X |
| RNF-REN-03 | Las consultas de disponibilidad usan índices. | Con 10 000 citas, el `EXPLAIN` de la consulta de huecos y el de la cuenta de cupo usan `uq_citas_hueco_activo` o `ix_citas_fecha_estado` (`type` distinto de `ALL`). La memoria incluye el `EXPLAIN` antes y después del índice. | X |
| RNF-REN-04 | La reserva es correcta bajo concurrencia. | Los dos tests de RF-14 pasan en 10 ejecuciones seguidas. Quitando el `synchronized`, el test 1 falla, y esa demostración queda documentada. | T |
| RNF-REN-05 | La app no hace red en el hilo principal. | Con `StrictMode` (`detectNetwork`) activo en `debug`, 0 violaciones al recorrer los casos de uso UC01 a UC10. Los tiempos de espera son de 5 s de conexión y 10 s de lectura. | M, I |
| RNF-REN-06 | La app no consume batería sin necesidad. | Sin sondeo periódico: con la app abierta e inactiva 5 minutos en cada pantalla, 0 peticiones de red (Profiler). Los datos se refrescan al volver a la pantalla o al deslizar. | M |
| RNF-REN-07 | La rotación no pierde el estado ni deja fugas. | Tras 10 rotaciones seguidas de C2, el Profiler no muestra ninguna `Activity` filtrada y siguen elegidos el día y la hora. | M |
| RNF-REN-08 | El servidor atiende varias peticiones a la vez. | El servidor usa un pool de 8 hilos. Con 5 logins simultáneos en curso, `GET /api/salud` responde en menos de 200 ms. | X |

### 2.3 Usabilidad y accesibilidad

| ID | Requisito | Criterio de aceptación | Ver. |
|---|---|---|---|
| RNF-USA-01 | Las zonas táctiles son de al menos 48 dp. | El 100 % de los controles interactivos mide 48 × 48 dp o más (Layout Inspector y Accessibility Scanner sin avisos de tamaño). | M |
| RNF-USA-02 | Los controles tienen etiqueta para los lectores de pantalla. | Todo elemento interactivo no textual tiene `contentDescription`. Accessibility Scanner no muestra avisos de etiqueta en las 10 pantallas (A1–A3, C1–C3, D1–D4). | M |
| RNF-USA-03 | El contraste de color es suficiente. | Texto normal con relación de contraste de al menos 4,5:1 y texto grande y componentes de al menos 3:1 (WCAG 2.1 AA), en tema claro y oscuro. La memoria incluye la tabla de contrastes, con el rojo `#DD0426` sobre sus fondos. | X |
| RNF-USA-04 | El estado nunca se indica solo con color. | Todo chip o indicador de estado (`pendiente`, `confirmada`, `Libre`, `Completo`, `Vacaciones`) lleva texto. En escala de grises siguen distinguiéndose. | M |
| RNF-USA-05 | La app funciona en vertical y apaisado. | Las 10 pantallas conservan los datos introducidos al rotar. En C2 se conservan el día y la hora elegidos (ViewModel y `onSaveInstanceState`). | M |
| RNF-USA-06 | La interfaz respeta el tamaño de texto del usuario. | Textos en `sp` y medidas en `dp`. Con el tamaño de fuente del sistema al máximo, ningún texto se corta ni se solapa en las 10 pantallas. | M |
| RNF-USA-07 | Los mensajes de error son comprensibles y están en español. | Cada código del catálogo que la app puede recibir tiene un texto propio en `strings.xml`. Ninguna pantalla muestra un código en crudo al provocar cada error. No hay cadenas de interfaz fijas en el código. | I, M |
| RNF-USA-08 | La app usa Material 3 con la identidad de Clandestino en tema claro y oscuro. | C3 permite elegir entre dos temas, Claro y Oscuro; no hay opción «Sistema». El rojo `#DD0426` solo se usa en acciones afirmativas y en la selección. Títulos en Anton y cuerpo en Inter. | M |
| RNF-USA-09 | La app da retroalimentación y evita el doble envío. | Mientras hay una petición en curso se muestra un indicador y el botón queda deshabilitado. Pulsar dos veces seguidas «Pedir cita» envía una sola petición. | M |
| RNF-USA-10 | Pedir una cita requiere pocos pasos. | Desde C1 hasta enviar la solicitud hacen falta 5 toques como máximo (Pedir cita, día, hora y confirmar). | M |
| RNF-USA-11 | Las animaciones son breves y no estorban. | La entrada de tarjetas, la transición entre fragmentos y la escala al pulsar botones duran 300 ms como máximo y no impiden pulsar. | M |

### 2.4 Compatibilidad

| ID | Requisito | Criterio de aceptación | Ver. |
|---|---|---|---|
| RNF-COM-01 | La app funciona en las versiones de Android previstas. | `minSdk` 26, `targetSdk` 36 y `compileSdk` 36.1. Las funciones P1 se recorren sin fallos en al menos dos versiones distintas, una cercana a la API 26 y otra a la 36 (emulador o móvil físico). | M |
| RNF-COM-02 | La app se adapta a teléfonos de distintos tamaños. | Se prueba desde 360 × 640 dp en vertical y en apaisado. La variante de tableta es P2. | M |
| RNF-COM-03 | La API se ejecuta con el entorno previsto. | Compila con `options.release = 21` y se ejecuta con el JBR de Android Studio o con cualquier JDK 21 o superior, contra MySQL 9.7 LTS. | M |
| RNF-COM-04 | La app llega a la API desde el emulador y desde un móvil físico. | Funciona con `10.0.2.2:8080` en el emulador y con `adb reverse tcp:8080 tcp:8080` en un móvil físico. La API escucha en `127.0.0.1` (dirección explícita, no `localhost`, para no quedarse en IPv6): el `10.0.2.2` del emulador apunta ahí y la API no queda abierta a la wifi. Se puede cambiar con `API_HOST`. Pendiente de comprobar con el emulador. | M |
| RNF-COM-05 | Los formatos de intercambio son únicos y están definidos. | JSON en UTF-8, fechas `yyyy-MM-dd`, horas `HH:mm` en hora de Madrid y base de datos en `utf8mb4`. Una nota con ñ y tildes se guarda y se lee idéntica. | T |
| RNF-COM-06 | La lógica de fechas es correcta con la zona de Madrid y el cambio de hora. | Toda la lógica usa un `Reloj` de `Europe/Madrid` inyectable. Hay tests con fechas fijas, incluido el cambio de hora del 25-10-2026 y el caso de las 23:30 UTC de un día de verano, que en Madrid ya es el día siguiente. | T |
| RNF-COM-07 | El contrato de errores es estable. | Todas las respuestas de error tienen la forma `{ "error", "mensaje" }`. Un test recorre el catálogo de `api-contrato.md` §5 y comprueba forma y código HTTP de cada uno. | T |

### 2.5 Mantenibilidad

| ID | Requisito | Criterio de aceptación | Ver. |
|---|---|---|---|
| RNF-MAN-01 | El código se organiza en capas. | API: `http` → `servicio` → `repositorio` → MySQL. App: MVVM. 0 importaciones de `java.sql` fuera de `repositorio` y 0 importaciones de `data.remote` desde `ui`. | I |
| RNF-MAN-02 | Las reglas de negocio tienen pruebas automáticas. | `gradlew test` termina en verde. Cada código de error de RN-09 y de RN-21 tiene al menos un test. Hay tests del router (404, 405, JSON inválido y cuerpo grande), de la autenticación (hash y JWT) y los dos de concurrencia. | T |
| RNF-MAN-03 | Hay casos de prueba manuales documentados. | Entre 6 y 8 casos con pasos, resultado esperado, resultado real y captura. | M |
| RNF-MAN-04 | El código es legible y se puede explicar. | Nombres del dominio en español, Javadoc en las clases públicas, comentarios solo para el porqué y ningún método de más de 40 líneas sin justificar. | I |
| RNF-MAN-05 | La configuración sale del código. | Puerto, URL y credenciales de la base de datos, clave JWT, iteraciones de PBKDF2 (1 000 en los tests) y zona horaria se leen de variables de entorno o de configuración. 0 valores de este tipo fijos en el código. | I |
| RNF-MAN-06 | El entorno se reproduce desde cero. | `schema.sql` y `seed.sql` recrean la base de datos. `scripts/arrancar-api.cmd` y `scripts/reiniciar-bd.cmd` arrancan la API y reinician la base de datos. El Anexo I se ha seguido desde cero hasta que `GET /api/salud` devuelve 200. | M |
| RNF-MAN-07 | El código pasa el análisis estático de Android. | `gradlew lint` sin errores. | I |
| RNF-MAN-08 | El índice único se prueba antes de construir sobre él. | El primer día de la fase de desarrollo, dos inserciones duplicadas de una cita activa fallan y dos citas canceladas a la misma hora coexisten (plan §5). El script queda en el repositorio. | T |

### 2.6 Privacidad y RGPD

| ID | Requisito | Criterio de aceptación | Ver. |
|---|---|---|---|
| RNF-PRI-01 | Solo se guardan los datos necesarios. | La base de datos guarda usuario, teléfono, hash de la contraseña, estado y datos de las citas. No se piden nombre real, correo, ubicación ni fecha de nacimiento (revisión de `schema.sql` y del formulario A2). | I |
| RNF-PRI-02 | Un cliente no accede a datos personales de otro. | `GET /api/dias` y `GET /api/huecos` devuelven solo estados y booleanos, y `GET /api/mis-citas` solo citas propias. Un test con dos clientes lo comprueba. | T |
| RNF-PRI-03 | Los datos personales no van a los registros. | Tras recorrer UC01 a UC10, una búsqueda de contraseñas, tokens y teléfonos en el registro de la API y en Logcat no encuentra ninguno. | M, I |
| RNF-PRI-04 | Los datos de prueba son ficticios. | `seed.sql` y las capturas de la memoria usan solo usuarios y teléfonos inventados, ningún dato de clientes reales de Clandestino. | I |
| RNF-PRI-05 | El usuario recibe información sobre el tratamiento de sus datos. | A2 muestra, visible antes de pulsar el botón de registro, un aviso breve: quién trata los datos, para qué (gestionar citas) y cómo ejercer sus derechos (DP-10). Se comprueba con una captura de A2. | M |
| RNF-PRI-06 | Las copias de seguridad no exponen datos personales. | Los volcados de `mysqldump` se guardan fuera del repositorio y `.gitignore` los excluye. | I |

El derecho de supresión (borrar a un cliente y sus citas) es P2: véase RF-P2-03.

## 3. Pantallas y requisitos

Para el diseño de mockups y de pruebas, qué requisitos se ven en cada pantalla P1.

| Pantalla | Requisitos |
|---|---|
| A1 Login | RF-04, RF-05, RF-06, RF-07 |
| A2 Registro con código | RF-01, RF-02, RF-03, RF-04, RNF-PRI-05 (aviso de privacidad) |
| A3 Espera / bloqueado | RF-08 |
| C1 Mis citas | RF-15, RF-16 |
| C2 Pedir cita | RF-09, RF-10, RF-12, RF-13, RF-14 |
| C3 Ajustes | RF-06 (cerrar sesión) y RNF-USA-08 (tema) |
| D1 Solicitudes | RF-22, RF-23, RF-24 |
| D2 Agenda | RF-25, RF-26, RF-27 |
| D3 Horarios y vacaciones | RF-11, RF-28, RF-29 |
| D4 Clientes e invitaciones | RF-17, RF-18, RF-19, RF-20, RF-21 |

## 4. Requisitos P2 y P3 (fuera de la tabla principal)

> **Esta tabla no forma parte de los requisitos obligatorios.** Son las ampliaciones del plan §4: P2 si hay tiempo y P3 opcional. No entran en los diagramas de casos de uso ni en los mockups obligatorios. Se recortan en este orden si el calendario aprieta: P2, calendario de tres estados, agenda semanal (plan §10).

| ID | Prioridad | Requisito | Relacionado con | Nota |
|---|---|---|---|---|
| RF-P2-01 | P2 | Cambiar mi @usuario (C3). | UC02 | `PUT /api/yo`; error `usuario_en_uso`. |
| RF-P2-02 | P2 | Crear una cita manual con `DatePickerDialog` y `TimePickerDialog` (D5). | UC08, UC09 | `POST /api/admin/citas`; nace `confirmada` y salta el cupo (T10). |
| RF-P2-03 | P2 | Eliminar un cliente y sus citas (derecho de supresión). | UC07 | `DELETE /api/admin/clientes/{id}`; cascada en `citas`, `usada_por` a `NULL` en `invitaciones`. |
| RF-P2-04 | P2 | Precio por cliente y propina. | UC07, UC09 | La propina se guarda en `asistencia` (RN-19). El plan no define la ruta para editar el precio; la fija DP-11. |
| RF-P2-05 | P2 | Llamar al cliente con un intent implícito `ACTION_DIAL`. | UC09 | Da uso al teléfono del cliente; no hay endpoint. |
| RF-P2-06 | P2 | Caducidad automática de solicitudes. | UC08 | Tarea programada; transición T09 a `caducada`. |
| RF-P2-07 | P2 | Pool de conexiones (HikariCP) medido frente a una conexión por petición. | — | Medición de latencia con N peticiones. |
| RF-P2-08 | P2 | Página de estado en HTML, CSS y JS. | — | `GET /`. |
| RF-P2-09 | P2 | Copia de seguridad con `ProcessBuilder` y `mysqldump`. | — | `POST /api/admin/copia-seguridad`. |
| RF-P2-10 | P2 | HTTPS con `HttpsServer` y un certificado autofirmado de `keytool`. | — | Sustituye a la limitación de RNF-SEG-11. |
| RF-P2-11 | P2 | Gesto de deslizar en la agenda. | UC09 | D2. |
| RF-P2-12 | P2 | Variante de tableta. | — | Amplía RNF-COM-02. |
| RF-P2-13 | P2 | Pruebas de interfaz con Espresso. | — | Amplía RNF-MAN-02. |
| RF-P3-01 | P3 | Pantalla «Resumen del mes» (ingresos y citas atendidas) (D6). | — | Informe del administrador; el plan no define su endpoint. |
| RF-P3-02 | P3 | Caché de «mis citas» con `SQLiteOpenHelper`. | UC05 | Lectura sin conexión. |
| RF-P3-03 | P3 | `BroadcastReceiver` de conectividad. | — | Avisa de que no hay red. |

Quedan como proyección a futuro, sin requisito: citas recurrentes, solicitar otra hora fuera de horario, nóminas y gastos fijos, avisos (push, recordatorios y WhatsApp), tiempo real, y varios barberos y servicios.
