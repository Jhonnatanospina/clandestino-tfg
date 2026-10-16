# Reglas de negocio

Borrador para revisión · 1 de octubre de 2026 · Fuentes: `docs/PLAN-TFG-Clandestino-Android.md` (§4, §5 y §3.2), `database/schema.sql`, `analisis/datos.json` y `analisis/logica.json`.

Cada regla lleva un identificador `RN-xx`. Lo que el plan y el análisis no resuelven se ha cerrado como decisión de diseño `DP-xx` (§11), con el criterio que ya recoge el contrato de la API. Los códigos de error y las rutas son los de `api-contrato.md`.

## 0. Definiciones

| Término | Definición |
|---|---|
| Ahora, hoy | Instante y fecha según un `Reloj` de `Europe/Madrid`, inyectable en los tests. No se hacen conversiones de zona: `DATE` y `TIME` ya están en hora de Madrid. |
| Cita activa | Cita en estado `pendiente` o `confirmada`. Solo ellas ocupan hueco y cupo (plan §4). |
| Hueco | Intervalo de 60 minutos identificado por su hora de inicio. Una cita dura una hora. |
| Hora efectiva | `hora_final` si no es `NULL`; si no, `hora_pedida`. En la base de datos, la columna generada `hora_ocupada` vale la hora efectiva mientras la cita está activa y `NULL` en cualquier otro estado. |
| Cita pasada | Cita cuya fecha y hora efectiva son iguales o anteriores a ahora. |
| Horizonte | Las fechas de hoy a hoy + 13 días (RN-05). |

## 1. Disponibilidad

| ID | Regla |
|---|---|
| RN-01 | **Precedencia.** La disponibilidad de una fecha se resuelve así: (1) si existe una fila de `excepciones_dia` para esa fecha, manda ella, aunque haya horario semanal activo; (2) si no, el horario de `horarios_semanales` con `activo = TRUE` cuyo `dia_semana` corresponde al día de la semana; (3) si tampoco existe, el día está cerrado. Un día de la semana sin fila equivale a un día sin horario. |
| RN-02 | **Excepciones.** Una fila por fecha (`uq_excepciones_fecha`): una excepción nueva sustituye a la anterior. `cerrado` es un día libre o de vacaciones y no lleva horas ni cupo. `abierto` es un día extra o un día con otro horario: lleva `hora_inicio` y `hora_fin` obligatorias, con inicio anterior a fin, y un `cupo` opcional que, si es `NULL`, se toma como 2. Una excepción `abierto` manda aunque el horario semanal de ese día esté inactivo. |
| RN-03 | **Horario semanal.** Un tramo por día de la semana (`uq_horarios_dia`) con `hora_inicio` anterior a `hora_fin`, `cupo` (2 por defecto) y `activo`. `dia_semana` usa la convención ISO de `java.time.DayOfWeek`: 1 = lunes ... 7 = domingo (así está en `schema.sql`). |
| RN-04 | **Huecos.** Se generan desde `hora_inicio`, de 60 en 60 minutos, mientras `hora + 60 min <= hora_fin`. Con un tramo 14:00–20:00 salen 14:00, 15:00, 16:00, 17:00, 18:00 y 19:00. Un tramo de menos de una hora no genera huecos. Los huecos no se guardan: se calculan en cada consulta. |
| RN-05 | **Horizonte.** Es fijo: de hoy a hoy + 13 días (14 días, «dos semanas» del plan §4), sin tabla de ajustes. El cliente solo ve y solo puede pedir fechas dentro del horizonte (DP-01). |
| RN-06 | **Estado de un día para el cliente.** Se evalúa en este orden: `vacaciones` si hay una excepción `cerrado`; `cerrado` si, por RN-01, el día no tiene tramo; `completo` si las citas activas de la fecha son iguales o superiores al cupo; `libre` en el resto. Solo un día `libre` admite pedir cita. |
| RN-07 | **Cupo.** Es el máximo de citas activas por fecha, no por hora ni por cliente: un mismo cliente puede tener varias citas el mismo día mientras quede cupo. Cuentan las `pendiente` y las `confirmada` de todos los clientes. Se comprueba al pedir (RN-09) y no al confirmar (RN-13). Si el administrador baja el cupo por debajo de las citas activas que ya existen, estas se mantienen y el día pasa a `completo`. |
| RN-08 | **Cambios de disponibilidad.** Cambiar el horario, crear una excepción o borrarla no modifica ni cancela las citas ya existentes esos días, y no avisa a nadie. Las gestiona el administrador desde la agenda (UC09). |

## 2. Solicitar una cita

| ID | Regla |
|---|---|
| RN-09 | **Orden de comprobación de `POST /api/citas`.** Se detiene en la primera que falla (tabla siguiente). |
| RN-10 | **Resultado.** La cita nace `pendiente`, con `hora_pedida` igual a la pedida y `hora_final`, `precio`, `propina` y `decidida_en` en `NULL`. Desde ese instante ocupa hueco y cupo. Ninguna cita se agenda sola: toda solicitud pasa por el administrador (UC08). |
| RN-11 | **Hueco ocupado.** Dos citas activas no pueden compartir fecha y hora efectiva. Lo garantiza `uq_citas_hueco_activo` sobre `(fecha, hora_ocupada)`: como `hora_ocupada` es `NULL` para las citas no activas y MySQL admite varios `NULL` en un índice único, las canceladas, rechazadas, caducadas, hechas y no asistidas liberan su hueco sin ninguna otra acción. Confirmar con otra hora libera la pedida y ocupa la final. El índice solo impide la misma hora exacta (DP-04). |

Orden de comprobación de RN-09:

| Paso | Comprobación | Error | HTTP | Dónde |
|---|---|---|---|---|
| 1 | Cabecera `Authorization`; firma y caducidad del token | `no_autenticado`, `token_invalido` | 401 | Filtro |
| 2 | El usuario del token existe en la base de datos | `token_invalido` | 401 | Filtro |
| 3 | `estado` del usuario = `aprobado` | `no_aprobado` | 403 | Filtro |
| 4 | `rol` del usuario = `cliente` | `sin_permiso` | 403 | Filtro |
| 5 | Cuerpo: tamaño, JSON, `fecha` real en `yyyy-MM-dd` y `hora_pedida` en `HH:mm` | `cuerpo_demasiado_grande`, `json_invalido`, `datos_invalidos` | 400 | HTTP |
| 6 | La fecha está dentro del horizonte (DP-01) | `fecha_fuera_de_horizonte` | 400 | Servicio, antes del bloqueo |
| | *Se entra en el bloqueo de la fecha y se abre la transacción (RN-24)* | | | |
| 7 | El día está abierto (RN-01) | `dia_cerrado` | 409 | Servicio |
| 8 | La hora cabe en el tramo: `hora >= hora_inicio` y `hora + 60 min <= hora_fin` | `hora_fuera_de_tramo` | 400 | Servicio |
| 9 | La hora está alineada: `hora - hora_inicio` es múltiplo de 60 minutos (DP-03) | `hora_invalida` | 400 | Servicio |
| 10 | Si la fecha es hoy, la hora aún no ha llegado (DP-02) | `hora_pasada` | 400 | Servicio |
| 11 | Cupo: las citas activas de la fecha son menos que el cupo | `dia_lleno` | 409 | Servicio |
| 12 | Hueco libre: ninguna cita activa con esa fecha y hora efectiva | `hueco_ocupado` | 409 | Servicio |
| 13 | Se inserta la cita `pendiente`, se hace `commit` y se suelta el bloqueo | — | 201 | Servicio |

El orden va de lo más barato y estructural a lo que depende del estado actual de la agenda. Los pasos 7, 8, 9, 11 y 12 siguen el orden de `analisis/logica.json` (día abierto, tramo, alineación, cupo, hueco); el paso 6 (horizonte) y el paso 10 (hora pasada) se añaden por DP-01 y DP-02. El paso 6 va antes del bloqueo para que el mapa de bloqueos por fecha no pueda crecer más allá de 14 entradas. Si la inserción del paso 13 viola `uq_citas_hueco_activo` (`SQLState 23000`, error 1062), la respuesta es también `hueco_ocupado`.

## 3. Confirmar y rechazar

| ID | Regla |
|---|---|
| RN-12 | **Confirmar.** Solo una cita `pendiente`. El administrador acepta la hora pedida o fija otra `hora_final` (`HH:mm`, de 08:00 a 22:00, minutos `00` o `30`; no tiene que caer dentro del tramo ni en la rejilla, DP-04). Sin `hora_final` se usa `hora_pedida`. La fecha no cambia en P1. Se guardan `estado = confirmada`, `hora_final` y `decidida_en`. La fecha y la `hora_final` no pueden haber pasado (DP-06). La actualización lleva la guarda `WHERE id = ? AND estado = 'pendiente'`: si la cita cambió de estado entre la lectura y la escritura, no afecta a ninguna fila y se responde `estado_invalido`. Orden: formato (`datos_invalidos`) → existe (`no_encontrado`) → está `pendiente` (`estado_invalido`) → no ha pasado (`cita_pasada`) → índice único (`hueco_ocupado`). |
| RN-13 | **Confirmar no revalida el cupo**: la solicitud ya lo ocupaba como `pendiente` y confirmarla no cambia el número de citas activas del día. Sí se respeta el hueco: si otra cita activa tiene la misma fecha y hora efectiva, `hueco_ocupado` y la cita sigue `pendiente`. |
| RN-14 | **Rechazar.** Solo `pendiente` → `rechazada`, con `decidida_en`. Libera hueco y cupo. No hay motivo de rechazo ni aviso: el cliente lo ve al abrir la app o al deslizar para refrescar (plan §4). |

## 4. Cancelar

| ID | Regla |
|---|---|
| RN-15 | **Cancelación por el cliente.** Solo sus propias citas: una cita ajena o inexistente da `no_encontrado` sin distinguir los dos casos. Estado `pendiente` o `confirmada` (si no, `estado_invalido`). No hay antelación mínima: se puede cancelar el mismo día. La cita no puede haber pasado (`cita_pasada`, DP-05). La app pide confirmación en un diálogo (C1). Libera hueco y cupo. |
| RN-16 | **Cancelación por el administrador.** Solo citas `confirmada` que no han pasado. Una `pendiente` se rechaza (RN-14) y una pasada se cierra con la asistencia (RN-17). Libera hueco y cupo, y la cita sale de la agenda. La app pide confirmación. |

## 5. Asistencia y precio

| ID | Regla |
|---|---|
| RN-17 | **Asistencia.** Solo sobre una cita `confirmada` que ya ha pasado; antes de su hora, `cita_futura`. El resultado es `hecha` («Asistió») o `no_asistio` («No vino»). No hay automatismo, penalización ni bloqueo por no asistir. Una cita ya clasificada no se puede reclasificar (DP-07). Al clasificarla deja de ser `confirmada` y sale de la agenda, porque D2 solo carga citas `confirmada` (`GET /api/admin/citas?estado=confirmada`). |
| RN-18 | **Precio.** Al marcar `hecha`, la API copia `usuarios.precio_corte` (15,00 por defecto, siempre mayor que 0) a `citas.precio`. El valor queda fijado: un cambio posterior del precio del cliente no altera las citas ya cerradas. Con `no_asistio`, `precio` queda `NULL`. El cliente no ve el precio ni la propina: `GET /api/mis-citas` no los devuelve (tampoco `decidida_en`) y C1 no los muestra. Solo los recibe el administrador, en `GET /api/admin/citas` y en la respuesta de la asistencia. |
| RN-19 | **Propina (P2).** Opcional y mayor o igual que 0. Solo se guarda con `hecha`; con `no_asistio` se descarta. Se fija al marcar la asistencia y no se edita después. |

## 6. Invitaciones y registro

| ID | Regla |
|---|---|
| RN-20 | **Código de invitación.** Lo genera el administrador (UC06). Doce caracteres con `SecureRandom` del alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 símbolos, sin `0`, `O`, `1`, `I`, `L`). Nota opcional de hasta 60 caracteres. `caduca_en` = momento de creación + 7 días. Un solo uso. En P1 no se puede revocar. Al comparar se ignoran mayúsculas, espacios y guiones. |
| RN-21 | **Registro (UC01).** Orden: limitador → cuerpo y formato (`usuario` normalizado en `[a-z0-9_]{3,20}`, teléfono en formato internacional, contraseña de 8 a 128 caracteres) → la invitación existe (`invitacion_invalida`) → no está usada (`invitacion_usada`) → `caduca_en` posterior a ahora (`invitacion_caducada`) → `usuario` y teléfono libres (`duplicado`). La invitación se bloquea con `SELECT ... FOR UPDATE`, y la creación del usuario y el paso de la invitación a `usada` con `usada_por` ocurren en una sola transacción: un fallo en cualquier punto la deja `pendiente`. El usuario nace con rol `cliente`, estado `pendiente` y `precio_corte` de 15,00, y la respuesta incluye un token para que quede con sesión iniciada en A3. El administrador no se registra: se crea con `seed.sql`. |
| RN-22 | **Estados de la invitación.** `pendiente` → `usada`, solo al registrarse. «Caducada» no es un estado guardado: es una invitación `pendiente` con `caduca_en` pasada, y no se puede usar. |

## 7. Estados del cliente

| ID | Regla |
|---|---|
| RN-23 | Un cliente tiene uno de los tres estados de `usuarios.estado`. Las transiciones son `pendiente` → `aprobado`, `pendiente` → `bloqueado`, `aprobado` → `bloqueado` y `bloqueado` → `aprobado`, todas del administrador (UC07). Nunca se vuelve a `pendiente`. Repetir el estado en el que ya está da `estado_invalido`. El estado no viaja en el token: se lee de la base de datos en cada petición, así que el cambio surte efecto en la siguiente. El administrador siempre está `aprobado` y no se puede aprobar ni bloquear. |

| Estado | Cómo se llega | Qué puede hacer | Qué no puede hacer |
|---|---|---|---|
| `pendiente` | Al registrarse con una invitación (UC01). | Iniciar sesión, consultar su cuenta (`GET /api/yo`), ver A3 y cerrar sesión. | Todo lo demás: ver días y huecos, pedir citas, ver o cancelar citas. Recibe `no_aprobado` (403). |
| `aprobado` | El administrador lo aprueba (UC07). | UC03, UC04 y UC05. | — |
| `bloqueado` | El administrador lo bloquea (UC07). | Lo mismo que `pendiente`; A3 muestra el texto de bloqueo. | Lo mismo que `pendiente`. Sus citas existentes se mantienen (DP-13). |

## 8. Concurrencia

| ID | Regla |
|---|---|
| RN-24 | **Serialización de la reserva.** La carrera real es el cupo diario («contar las citas activas del día y, si hay sitio, insertar»): ningún índice la protege. El servicio guarda un objeto de bloqueo por fecha (un mapa de `LocalDate` a objeto, acotado a 14 entradas porque la fecha se valida antes, paso 6) y ejecuta los pasos 7 a 13 de RN-09 dentro de `synchronized` sobre ese objeto y de una sola transacción, con `commit` antes de soltar el bloqueo. Dos peticiones a fechas distintas no se esperan. Como red de seguridad, el índice único captura el doble reserva del mismo hueco, tanto al pedir como al confirmar. Limitación: el bloqueo vive en la memoria de un proceso, así que solo es válido con una única instancia de la API, que es la del proyecto. Pruebas: (1) cupo 1 y N peticiones a horas distintas del mismo día, que sin `synchronized` deja pasar varias y con él solo una; (2) N peticiones al mismo hueco, donde actúa el índice. |

## 9. Transiciones de estado de la cita

Estados que ocupan hueco y cupo: `pendiente` y `confirmada`. Estados terminales: `rechazada`, `cancelada`, `caducada`, `hecha` y `no_asistio`. Cualquier transición que no esté en la tabla da `estado_invalido` (409).

| ID | De | A | Quién | Condición | Endpoint |
|---|---|---|---|---|---|
| T01 | (no existe) | `pendiente` | Cliente | Cumple RN-09. | `POST /api/citas` |
| T02 | `pendiente` | `confirmada` | Administrador | La cita no ha pasado y la hora final no está ocupada (RN-12, RN-13). Guarda `hora_final` y `decidida_en`. | `POST /api/admin/citas/{id}/confirmar` |
| T03 | `pendiente` | `rechazada` | Administrador | Ninguna. Guarda `decidida_en`. | `POST /api/admin/citas/{id}/rechazar` |
| T04 | `pendiente` | `cancelada` | Cliente propietario | La cita no ha pasado (DP-05). | `POST /api/citas/{id}/cancelar` |
| T05 | `confirmada` | `cancelada` | Cliente propietario | La cita no ha pasado (DP-05). | `POST /api/citas/{id}/cancelar` |
| T06 | `confirmada` | `cancelada` | Administrador | La cita no ha pasado. | `POST /api/admin/citas/{id}/cancelar` |
| T07 | `confirmada` | `hecha` | Administrador | La cita ha pasado. Guarda `precio` = `precio_corte` del cliente (RN-18). | `POST /api/admin/citas/{id}/asistencia` |
| T08 | `confirmada` | `no_asistio` | Administrador | La cita ha pasado. `precio` queda `NULL`. | `POST /api/admin/citas/{id}/asistencia` |
| T09 (P2) | `pendiente` | `caducada` | Sistema (tarea programada) | Fecha y `hora_pedida` ya pasadas. Guarda `decidida_en`. | Ninguno |
| T10 (P2) | (no existe) | `confirmada` | Administrador | Cliente `aprobado` y hora libre; salta el cupo. | `POST /api/admin/citas` |

## 10. Diferencias respecto al Clandestino original

Se citan porque la memoria debe justificarlas (plan §4) o porque el alumno las encontrará en `analisis/*.json`.

| Aspecto | Original | Este proyecto |
|---|---|---|
| Acceso del cliente | `@usuario` + teléfono, sin contraseña. | `@usuario` + contraseña con PBKDF2; el teléfono es un dato de contacto. |
| Invitación | Enlace con token de 24 bytes. Estados `pendiente`, `usado`, `revocado`. | Código de 12 caracteres. Estados `pendiente` y `usada`; la caducidad se calcula. |
| Cliente bloqueado | El login falla y no se emite sesión. | El login funciona para ver A3; el resto de rutas da `no_aprobado`. |
| Horizonte | De 1 a 8 semanas, configurable. | Fijo, 14 días. |
| Precio | Se recalcula con el precio actual del cliente. | Se guarda en la cita al marcar `hecha`. |
| Confirmar | Puede cambiar también el día. | Solo la hora (P1). |
| Cancelar y rechazar | Cancelar sin diálogo; rechazar sin guarda de estado. | Cancelar con diálogo; rechazar y confirmar con guarda de estado. |
| Fechas | `2026-02-30` pasa la validación de formato. | `LocalDate.parse` estricto. |
| Fuera de alcance | Recurrentes (y `con_conflicto`), «otra hora» fuera de agenda, reprogramar, push y WhatsApp, métricas y finanzas. | Solo se conservan como proyección a futuro o, las métricas, como P3. |

## 11. Decisiones de diseño cerradas

El alumno cerró las trece decisiones el 2026-10-01, aceptando en cada una el criterio que ya recogían el contrato de la API y los requisitos. La tabla queda como registro de la decisión tomada: la columna «Decisión» es lo que aplican los documentos y cambiarla obliga a revisar los de la columna «Afecta a».

| ID | Tema | Qué dicen las fuentes | Decisión | Afecta a | Fecha |
|---|---|---|---|---|---|
| DP-01 | Fecha fuera del horizonte | El plan fija un horizonte de dos semanas pero no dice que el servidor rechace otras fechas. El original no lo comprueba. | `400 fecha_fuera_de_horizonte` si la fecha es anterior a hoy o posterior a hoy + 13. | RN-05, RN-09 (paso 6), `POST /api/citas`, `GET /api/huecos` | 2026-10-01 |
| DP-02 | Horas de hoy que ya han llegado | El original no filtra las horas pasadas del día. | `GET /api/huecos` omite las de hoy que ya han llegado y `POST /api/citas` las rechaza con `400 hora_pasada`. | RN-09 (paso 10) | 2026-10-01 |
| DP-03 | Rejilla de huecos | El plan dice «alineada a la hora en punto»; el original, múltiplo de 60 minutos desde el inicio del tramo (un tramo 14:30–17:00 da 14:30 y 15:30). | Regla del original. Coincide con el plan si `hora_inicio` tiene minutos `00`. Alternativa descartada: exigir minutos `00` al guardar horarios. | RN-04, RN-09 (paso 9) | 2026-10-01 |
| DP-04 | Hora final y solapes | El original deja elegir de 08:00 a 22:00 cada 30 minutos sin restringir al tramo, y el índice solo impide la misma hora exacta: una cita a las 10:00 y otra a las 10:30 coexisten aunque cada una dura una hora. | Igual que el original, declarando el límite en la memoria. Alternativa descartada: rechazar con `hueco_ocupado` si otra cita activa del día queda a menos de 60 minutos. | RN-11, RN-12 | 2026-10-01 |
| DP-05 | Cliente cancela una cita pasada | El original lo permite, de modo que una `confirmada` pasada puede acabar `cancelada` en lugar de `hecha` o `no_asistio`. | `409 cita_pasada`. | RN-15, T04, T05 | 2026-10-01 |
| DP-06 | Solicitudes pendientes que ya han pasado | La caducidad automática es P2; en P1 siguen `pendiente`, en D1 y ocupando cupo de un día pasado. | Confirmarlas da `409 cita_pasada`; el administrador solo puede rechazarlas. | RN-12, T02 | 2026-10-01 |
| DP-07 | Corregir una asistencia | El original no lo permite. | Sin transiciones desde `hecha` ni `no_asistio`. Una corrección se haría directamente en la base de datos. | RN-17 | 2026-10-01 |
| DP-08 | Parámetros de seguridad sin valor en el plan | El plan pide caducidad del token, limitador y límite de cuerpo sin fijar cifras. El original usaba 30 días de sesión. | Token de 7 días; 5 fallos por clave en 15 minutos; cuerpo de 16 KiB. Todos configurables. | `api-contrato.md` §6 y §7, RNF-SEG-02, RNF-SEG-05, RNF-SEG-08 | 2026-10-01 |
| DP-09 | Contraseña y teléfono sin librería | El plan limita las librerías de la API al driver de MySQL y `org.json`; el original usaba `libphonenumber`. | Contraseña de 8 a 128 caracteres sin reglas de composición. Teléfono: normalización sencilla y expresión regular E.164; no se comprueba que el número exista. | RF-04, `POST /api/auth/registro` | 2026-10-01 |
| DP-10 | Aviso de privacidad | El plan solo cita «RGPD con minimización de datos». | A2 muestra un aviso breve (responsable, finalidad, derechos) antes de enviar. | RF-04, RNF-PRI-05 | 2026-10-01 |
| DP-11 | Precio por cliente (P2) | El plan lo clasifica como P2 pero no define ninguna ruta. | Si se llega a hacer (P2), la ruta es `PUT /api/admin/clientes/{id}/precio` con `{ "precio_corte": 18.00 }`, mayor que 0. | RF-P2-04 | 2026-10-01 |
| DP-12 | Códigos HTTP y de error añadidos | El plan lista 400, 401, 403, 404, 405, 409 y 429 y cuatro códigos de reserva. | Se añaden `500 error_interno` y `503` solo para `GET /api/salud`; el límite de cuerpo se responde con 400 (no 413); el catálogo incorpora los códigos del original traducidos y los propios de este diseño (los que dependen de una decisión llevan su DP-xx en la columna «Origen»). | `api-contrato.md` §5 | 2026-10-01 |
| DP-13 | Citas de un cliente bloqueado | El original no las cancela. | Se mantienen activas y siguen ocupando cupo. El cliente bloqueado no puede cancelarlas; el administrador las rechaza o cancela desde D1 y D2. | RN-23 | 2026-10-01 |
