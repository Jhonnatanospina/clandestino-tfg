# Contrato de la API REST v1

Borrador para revisión · 1 de octubre de 2026 · Fuentes: `docs/PLAN-TFG-Clandestino-Android.md` (§3.2 y §6), `database/schema.sql` y `analisis/*.json`.

Los identificadores de casos de uso (UC01 a UC10), requisitos (RF-xx, RNF-xxx-xx) y reglas (RN-xx, DP-xx) son los de `casos-de-uso.md`, `requisitos.md` y `reglas-negocio.md`.

## 1. Convenciones

| Aspecto | Convención |
|---|---|
| URL base | `http://10.0.2.2:8080` desde el emulador; `http://localhost:8080` desde el PC o desde un móvil físico con `adb reverse tcp:8080 tcp:8080`. Todas las rutas cuelgan de `/api`, salvo la página de estado (P2). La versión `v1` identifica este contrato; no aparece en la ruta. |
| Formato | JSON en UTF-8. Cabecera `Content-Type: application/json; charset=utf-8` en peticiones con cuerpo y en todas las respuestas, salvo `GET /` (P2) y `204`. |
| Autenticación | `Authorization: Bearer <token>` en todo salvo `GET /api/salud`, `POST /api/auth/registro` y `POST /api/auth/login`. |
| Fechas y horas | Fecha `yyyy-MM-dd` y hora `HH:mm`, siempre en hora de Madrid (`Europe/Madrid`), sin zona en el texto. Las marcas de tiempo (`creada_en`, `decidida_en`, `caduca_en`, `creado_en`, `aprobado_en`) usan `yyyy-MM-ddTHH:mm:ss`, también en hora de Madrid. Las fechas se validan con `LocalDate.parse` estricto: `2026-02-30` es inválida. |
| Nombres | Los campos JSON se llaman como las columnas de `schema.sql` (`hora_pedida`, `precio_corte`, `dia_semana`...). Los valores de `rol`, `estado` y `tipo` son los de los `ENUM` del esquema. Los importes (`DECIMAL(6,2)`) viajan como número con dos decimales (`15.00`). Los identificadores son enteros. |
| Éxito | `200` con el recurso; `201` al crear (con el recurso creado); `204` sin cuerpo en `DELETE`. |
| Error | Siempre `{ "error": "codigo", "mensaje": "Texto para la persona" }` con el código HTTP indicado en el catálogo (§5). El `codigo` es estable y la app lo traduce a su propio texto; el `mensaje` está en español y no revela datos internos. |
| Listas | Sin paginación en v1 (el volumen es bajo: un cupo de pocas citas al día). Las listas van dentro de un objeto con el nombre de la colección. |
| Rutas y métodos | Una ruta desconocida devuelve `404 ruta_no_encontrada`; un método no previsto en una ruta existente, `405 metodo_no_permitido` con la cabecera `Allow`. No se usa `PATCH` (`HttpURLConnection` no lo soporta bien); las modificaciones usan `PUT` o `POST`. |

## 2. Resumen de endpoints

Prioridad según plan §4. «Estado exigido» es el `estado` que debe tener en la base de datos el usuario del token.

| # | Método y ruta | Rol | Estado exigido | UC | Prioridad |
|---|---|---|---|---|---|
| 1 | `GET /api/salud` | público | — | — | P1 |
| 2 | `POST /api/auth/registro` | público | — | UC01 | P1 |
| 3 | `POST /api/auth/login` | público | — | UC02 | P1 |
| 4 | `GET /api/yo` | cualquiera | cualquiera | UC02, UC01 (A3) | P1 |
| 5 | `GET /api/dias?desde&hasta` | cliente | `aprobado` | UC03 | P1 |
| 6 | `GET /api/huecos?fecha` | cliente | `aprobado` | UC03 | P1 |
| 7 | `POST /api/citas` | cliente | `aprobado` | UC04 | P1 |
| 8 | `GET /api/mis-citas` | cliente | `aprobado` | UC05 | P1 |
| 9 | `POST /api/citas/{id}/cancelar` | cliente | `aprobado` | UC05 | P1 |
| 10 | `GET /api/admin/citas?estado&desde&hasta` | admin | `aprobado` | UC08, UC09 | P1 |
| 11 | `POST /api/admin/citas/{id}/confirmar` | admin | `aprobado` | UC08 | P1 |
| 12 | `POST /api/admin/citas/{id}/rechazar` | admin | `aprobado` | UC08 | P1 |
| 13 | `POST /api/admin/citas/{id}/cancelar` | admin | `aprobado` | UC09 | P1 |
| 14 | `POST /api/admin/citas/{id}/asistencia` | admin | `aprobado` | UC09 | P1 |
| 15 | `GET /api/admin/horarios` | admin | `aprobado` | UC10 | P1 |
| 16 | `PUT /api/admin/horarios` | admin | `aprobado` | UC10 | P1 |
| 17 | `GET /api/admin/excepciones` | admin | `aprobado` | UC10 | P1 |
| 18 | `POST /api/admin/excepciones` | admin | `aprobado` | UC10 | P1 |
| 19 | `DELETE /api/admin/excepciones/{id}` | admin | `aprobado` | UC10 | P1 |
| 20 | `GET /api/admin/clientes?estado` | admin | `aprobado` | UC07 | P1 |
| 21 | `POST /api/admin/clientes/{id}/aprobar` | admin | `aprobado` | UC07 | P1 |
| 22 | `POST /api/admin/clientes/{id}/bloquear` | admin | `aprobado` | UC07 | P1 |
| 23 | `GET /api/admin/invitaciones` | admin | `aprobado` | UC06 | P1 |
| 24 | `POST /api/admin/invitaciones` | admin | `aprobado` | UC06 | P1 |
| 25 | `PUT /api/yo` | cliente | `aprobado` | UC02 (C3) | P2 |
| 26 | `POST /api/admin/citas` | admin | `aprobado` | UC08, UC09 (D5) | P2 |
| 27 | `DELETE /api/admin/clientes/{id}` | admin | `aprobado` | UC07 | P2 |
| 28 | `GET /` (página de estado) | público | — | — | P2 |
| 29 | `POST /api/admin/copia-seguridad` | admin | `aprobado` | — | P2 |

Los endpoints 25 a 29 no entran en los diagramas ni en los mockups obligatorios. El plan no define ninguna ruta para el precio por cliente (P2); véase DP-11.

## 3. Endpoints P1

### 3.1 Público

#### `GET /api/salud`

Comprueba que la API está arrancada y que alcanza la base de datos (Anexo I). Sin autenticación.

`200`:

```json
{ "estado": "ok", "base_de_datos": "ok" }
```

Errores: `503 base_de_datos_no_disponible` si la consulta de prueba falla.

#### `POST /api/auth/registro` (UC01)

Crea un cliente `pendiente` con un código de invitación y abre sesión. Pasa por el limitador (§6).

Petición:

```json
{
  "codigo": "K7M2-QX9P-TD4R",
  "usuario": "@Marcos_R",
  "telefono": "611 22 23 33",
  "password": "mi-clave-larga-1"
}
```

| Campo | Regla |
|---|---|
| `codigo` | 12 caracteres del alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 símbolos, sin `0`, `O`, `1`, `I`, `L`). La API ignora mayúsculas, espacios y guiones al comparar. |
| `usuario` | Se normaliza (recorta, minúsculas, quita una `@` inicial) y debe cumplir `[a-z0-9_]{3,20}`. |
| `telefono` | Se normaliza a formato internacional (`+34611222333`): se quitan espacios, guiones, puntos y paréntesis; si no empieza por `+` y tiene 9 dígitos, se antepone `+34`. Debe cumplir `\+[1-9][0-9]{7,14}` (cabe en `VARCHAR(16)`). Véase DP-09. |
| `password` | De 8 a 128 caracteres. Véase DP-09. |

`201`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3IiwiaWF0IjoxNzkwODQ0MTIwLCJleHAiOjE3OTE0NDg5MjB9.Fi4TVZTnUD7tNnr-zo6XQSYzGyZWaSlYS5-OTSpIigY",
  "usuario": {
    "id": 7,
    "usuario": "marcos_r",
    "telefono": "+34611222333",
    "rol": "cliente",
    "estado": "pendiente"
  }
}
```

Errores, en el orden en que se comprueban: `429 demasiados_intentos` · `400 cuerpo_demasiado_grande` · `400 json_invalido` · `400 datos_invalidos` · `400 invitacion_invalida` · `409 invitacion_usada` · `409 invitacion_caducada` · `409 duplicado`.

La API bloquea la fila de la invitación (`SELECT ... FOR UPDATE`), crea el usuario y marca la invitación `usada` con `usada_por` en una sola transacción. Si el usuario o el teléfono ya existen (`SQLState 23000`, error 1062), se deshace todo y la invitación sigue `pendiente`. El código `duplicado` no dice cuál de los dos datos colisiona.

#### `POST /api/auth/login` (UC02)

Petición:

```json
{ "usuario": "marcos_r", "password": "mi-clave-larga-1" }
```

`200`: mismo cuerpo que el registro (`token` y `usuario`). Se emite el token también a los clientes `pendiente` y `bloqueado`: la app necesita sesión para consultar su estado en `GET /api/yo` y mostrar la pantalla A3. El resto de rutas les responde `403 no_aprobado`.

Errores: `429 demasiados_intentos` · `400 json_invalido` · `400 datos_invalidos` (falta algún campo) · `401 credenciales_invalidas` (mismo mensaje si el usuario no existe o la contraseña es incorrecta).

### 3.2 Cuenta

#### `GET /api/yo` (UC02, pantalla A3)

Devuelve la cuenta del token. Es la única ruta autenticada que responde a un cliente `pendiente` o `bloqueado`. A3 elige su texto (espera o bloqueo) según el `estado` de la respuesta.

`200`:

```json
{ "id": 7, "usuario": "marcos_r", "telefono": "+34611222333", "rol": "cliente", "estado": "aprobado" }
```

Errores: `401 no_autenticado` · `401 token_invalido` (también si el usuario del token ya no existe).

### 3.3 Cliente

Todos exigen rol `cliente` (si no, `403 sin_permiso`) y estado `aprobado` (si no, `403 no_aprobado`), además de los `401` de autenticación. Para no repetirlos, las listas de errores de este apartado solo recogen los propios de cada ruta.

#### `GET /api/dias?desde&hasta` (UC03)

Estado de cada día del horizonte: de hoy a hoy + 13 días (RN-05). `desde` y `hasta` son opcionales (por defecto, el horizonte entero); el intervalo se recorta al horizonte, sin error.

`200` (ejemplo con hoy = jueves 2026-10-01; la respuesta real trae los 14 días, aquí se muestran 8):

```json
{
  "horizonte": { "desde": "2026-10-01", "hasta": "2026-10-14" },
  "dias": [
    { "fecha": "2026-10-01", "estado": "libre" },
    { "fecha": "2026-10-02", "estado": "cerrado" },
    { "fecha": "2026-10-03", "estado": "cerrado" },
    { "fecha": "2026-10-04", "estado": "cerrado" },
    { "fecha": "2026-10-05", "estado": "completo" },
    { "fecha": "2026-10-06", "estado": "libre" },
    { "fecha": "2026-10-07", "estado": "vacaciones" },
    { "fecha": "2026-10-08", "estado": "vacaciones" }
  ]
}
```

| `estado` | Significado (RN-06) |
|---|---|
| `libre` | Día abierto con cupo disponible. |
| `completo` | Día abierto con el cupo alcanzado. |
| `vacaciones` | Hay una excepción `cerrado` para esa fecha. |
| `cerrado` | Sin excepción ni horario semanal activo. |

Errores: `400 datos_invalidos` (fecha mal formada o `desde` posterior a `hasta`).

#### `GET /api/huecos?fecha` (UC03)

Huecos de una hora del día `fecha` (obligatoria, dentro del horizonte). No incluye datos de otros clientes, solo si cada hueco está ocupado.

`200` (lunes 2026-10-05, horario 14:00 a 20:00, cupo 2 ya alcanzado):

```json
{
  "fecha": "2026-10-05",
  "estado": "completo",
  "huecos": [
    { "hora": "14:00", "ocupado": false },
    { "hora": "15:00", "ocupado": true },
    { "hora": "16:00", "ocupado": false },
    { "hora": "17:00", "ocupado": false },
    { "hora": "18:00", "ocupado": true },
    { "hora": "19:00", "ocupado": false }
  ]
}
```

En un día `cerrado` o `vacaciones`, `huecos` es una lista vacía. Si la fecha es hoy, no se incluyen las horas que ya han empezado (DP-02). Un día `completo` devuelve sus huecos tal cual; la app los deshabilita por el estado del día.

Errores: `400 datos_invalidos` (falta `fecha` o no es una fecha real) · `400 fecha_fuera_de_horizonte`.

#### `POST /api/citas` (UC04)

Solicita una cita. Queda `pendiente` hasta que el administrador decida.

Petición:

```json
{ "fecha": "2026-10-06", "hora_pedida": "17:00" }
```

`201`:

```json
{
  "id": 12,
  "fecha": "2026-10-06",
  "hora_pedida": "17:00",
  "hora_final": null,
  "estado": "pendiente",
  "creada_en": "2026-10-01T10:42:15"
}
```

Errores, en el orden en que se comprueban (detalle en RN-09):

| Orden | Código | HTTP |
|---|---|---|
| 1 | `json_invalido`, `cuerpo_demasiado_grande`, `datos_invalidos` | 400 |
| 2 | `fecha_fuera_de_horizonte` | 400 |
| 3 | `dia_cerrado` | 409 |
| 4 | `hora_fuera_de_tramo` | 400 |
| 5 | `hora_invalida` | 400 |
| 6 | `hora_pasada` | 400 |
| 7 | `dia_lleno` | 409 |
| 8 | `hueco_ocupado` | 409 |

Las comprobaciones 3 a 8 de esta tabla y la inserción (pasos 7 a 13 de RN-09) van dentro de un `synchronized` por fecha y de una sola transacción (RN-24). El índice único `uq_citas_hueco_activo` actúa como red de seguridad: si la inserción lo viola (`SQLState 23000`, error 1062), la respuesta es también `409 hueco_ocupado`.

#### `GET /api/mis-citas` (UC05)

Citas del propio cliente: las de los últimos 30 días y todas las futuras, de la más reciente a la más antigua (por `fecha` y hora efectiva descendentes). La app muestra el estado con texto.

`200`:

```json
{
  "citas": [
    { "id": 12, "fecha": "2026-10-06", "hora_pedida": "17:00", "hora_final": "17:30", "estado": "confirmada", "creada_en": "2026-10-01T10:42:15" },
    { "id": 9, "fecha": "2026-09-28", "hora_pedida": "15:00", "hora_final": "15:00", "estado": "hecha", "creada_en": "2026-09-25T18:03:40" }
  ]
}
```

La hora efectiva de una cita es `hora_final` si no es `null`, y si no, `hora_pedida`. El cliente no recibe `precio`, `propina` ni `decidida_en` (RN-18).

#### `POST /api/citas/{id}/cancelar` (UC05)

Cancela una cita propia en estado `pendiente` o `confirmada`. Sin cuerpo.

`200`: la cita con `"estado": "cancelada"`, con la forma de `mis-citas`.

Errores: `400 datos_invalidos` (`id` no numérico) · `404 no_encontrado` (no existe o es de otro cliente) · `409 estado_invalido` · `409 cita_pasada` (DP-05).

### 3.4 Administrador: citas

Todos exigen rol `admin` (si no, `403 sin_permiso`) y estado `aprobado`. Los `404 no_encontrado` de estas rutas significan que la cita no existe.

#### `GET /api/admin/citas?estado&desde&hasta` (UC08, UC09)

Lista citas de cualquier cliente. Parámetros opcionales: `estado` (uno o varios valores del enum separados por comas, por ejemplo `pendiente,confirmada`), `desde` y `hasta` (fechas inclusivas). Orden: `fecha` y hora efectiva ascendentes.

`200`:

```json
{
  "citas": [
    {
      "id": 12,
      "cliente": { "id": 7, "usuario": "marcos_r", "telefono": "+34611222333" },
      "fecha": "2026-10-06",
      "hora_pedida": "17:00",
      "hora_final": null,
      "estado": "pendiente",
      "precio": null,
      "propina": null,
      "creada_en": "2026-10-01T10:42:15",
      "decidida_en": null
    }
  ]
}
```

Errores: `400 datos_invalidos` (`estado` fuera del enum, fecha mal formada o `desde` posterior a `hasta`).

Uso desde la app: D1 pide `estado=pendiente`; D2 pide `estado=confirmada` con el intervalo de la semana o del mes visible. D2 solo carga citas `confirmada`: al marcar la asistencia (`hecha` o `no_asistio`) o al cancelar, la cita deja de ser `confirmada` y sale de la agenda al recargarla.

#### `POST /api/admin/citas/{id}/confirmar` (UC08)

Confirma una solicitud `pendiente` fijando la hora final. El cuerpo es opcional; sin `hora_final` se conserva la hora pedida. No se comprueba el cupo: la solicitud ya lo ocupaba (RN-13).

Petición:

```json
{ "hora_final": "17:30" }
```

`hora_final`: `HH:mm` entre `08:00` y `22:00`, con minutos `00` o `30` (DP-04).

`200`: la cita con `"estado": "confirmada"`, `"hora_final": "17:30"` y `decidida_en` relleno, con la forma de `GET /api/admin/citas`.

Errores, en orden: `400 json_invalido` · `400 datos_invalidos` · `404 no_encontrado` · `409 estado_invalido` (la cita no está `pendiente`) · `409 cita_pasada` (DP-06) · `409 hueco_ocupado` (otra cita activa con la misma fecha y hora efectiva).

#### `POST /api/admin/citas/{id}/rechazar` (UC08)

Pasa una cita `pendiente` a `rechazada` y libera hueco y cupo. Sin cuerpo.

`200`: la cita con `"estado": "rechazada"` y `decidida_en` relleno.

Errores: `404 no_encontrado` · `409 estado_invalido`.

#### `POST /api/admin/citas/{id}/cancelar` (UC09)

Cancela una cita `confirmada` que aún no ha pasado. Sin cuerpo.

`200`: la cita con `"estado": "cancelada"`.

Errores: `404 no_encontrado` · `409 estado_invalido` (no está `confirmada`) · `409 cita_pasada`.

#### `POST /api/admin/citas/{id}/asistencia` (UC09)

Cierra una cita `confirmada` cuya hora ya ha pasado.

Petición:

```json
{ "resultado": "hecha" }
```

`resultado`: `hecha` o `no_asistio`. Con `hecha`, la API copia `usuarios.precio_corte` del cliente a `citas.precio` en ese momento; con `no_asistio`, `precio` queda `null`. El campo opcional `propina` (número ≥ 0, P2) solo se guarda con `hecha`; con `no_asistio` se ignora.

`200`:

```json
{
  "id": 9,
  "cliente": { "id": 7, "usuario": "marcos_r", "telefono": "+34611222333" },
  "fecha": "2026-09-28",
  "hora_pedida": "15:00",
  "hora_final": "15:00",
  "estado": "hecha",
  "precio": 15.00,
  "propina": null,
  "creada_en": "2026-09-25T18:03:40",
  "decidida_en": "2026-09-26T09:10:02"
}
```

Errores, en orden: `400 json_invalido` · `400 datos_invalidos` (`resultado` desconocido) · `404 no_encontrado` · `409 estado_invalido` · `409 cita_futura` (la hora efectiva aún no ha llegado).

### 3.5 Administrador: horarios y excepciones

#### `GET /api/admin/horarios` (UC10)

Devuelve las filas existentes de `horarios_semanales`, ordenadas por `dia_semana`. Un día sin fila no tiene horario (RN-01). `dia_semana` sigue la convención ISO: 1 = lunes ... 7 = domingo.

`200`:

```json
{
  "horarios": [
    { "id": 1, "dia_semana": 1, "hora_inicio": "14:00", "hora_fin": "20:00", "cupo": 2, "activo": true },
    { "id": 2, "dia_semana": 2, "hora_inicio": "14:00", "hora_fin": "20:00", "cupo": 2, "activo": true },
    { "id": 3, "dia_semana": 3, "hora_inicio": "14:00", "hora_fin": "20:00", "cupo": 2, "activo": true },
    { "id": 4, "dia_semana": 4, "hora_inicio": "14:00", "hora_fin": "20:00", "cupo": 2, "activo": true }
  ]
}
```

#### `PUT /api/admin/horarios` (UC10)

Guarda uno o varios días. Cada elemento se inserta o actualiza por `dia_semana`; los días que no aparecen no se tocan. Todo en una transacción.

Petición:

```json
{
  "horarios": [
    { "dia_semana": 5, "hora_inicio": "10:00", "hora_fin": "14:00", "cupo": 3, "activo": true }
  ]
}
```

| Campo | Regla |
|---|---|
| `dia_semana` | Entero de 1 a 7; sin repetir dentro de la petición. |
| `hora_inicio`, `hora_fin` | `HH:mm`, con `hora_inicio` anterior a `hora_fin` (restricción `ck_horarios_tramo`). |
| `cupo` | Entero de 0 a 255. Opcional; por defecto 2. Con 0 el día queda siempre completo. |
| `activo` | Booleano. Opcional; por defecto `true`. |

`200`: la lista completa de horarios tras guardar, con la forma del `GET`.

Errores: `400 json_invalido` · `400 datos_invalidos`.

#### `GET /api/admin/excepciones?desde` (UC10)

Excepciones con `fecha` igual o posterior a `desde` (por defecto, hoy), ordenadas por fecha.

`200`:

```json
{
  "excepciones": [
    { "id": 3, "fecha": "2026-10-07", "tipo": "cerrado", "hora_inicio": null, "hora_fin": null, "cupo": null, "nota": "Vacaciones" },
    { "id": 5, "fecha": "2026-10-17", "tipo": "abierto", "hora_inicio": "10:00", "hora_fin": "14:00", "cupo": 3, "nota": "Sábado extra" }
  ]
}
```

Errores: `400 datos_invalidos` (`desde` mal formada).

#### `POST /api/admin/excepciones` (UC10)

Crea la misma excepción para una o varias fechas (de 1 a 31). Si una fecha ya tenía excepción, la nueva la sustituye (`fecha` es única). Todo en una transacción. No modifica ni cancela las citas ya existentes esos días (RN-08).

Petición de vacaciones:

```json
{ "fechas": ["2026-10-07", "2026-10-08"], "tipo": "cerrado", "nota": "Vacaciones" }
```

Petición de día extra:

```json
{ "fechas": ["2026-10-17"], "tipo": "abierto", "hora_inicio": "10:00", "hora_fin": "14:00", "cupo": 3, "nota": "Sábado extra" }
```

| Campo | Regla |
|---|---|
| `fechas` | De 1 a 31 fechas reales, sin repetir, no anteriores a hoy. |
| `tipo` | `cerrado` o `abierto`. |
| `hora_inicio`, `hora_fin` | Obligatorias con `abierto` (inicio anterior a fin). Prohibidas con `cerrado`. |
| `cupo` | Solo con `abierto`, opcional. Sin valor se guarda `null` y la API usa 2. Prohibido con `cerrado`. |
| `nota` | Opcional, hasta 80 caracteres. |

`201`:

```json
{
  "excepciones": [
    { "id": 6, "fecha": "2026-10-07", "tipo": "cerrado", "hora_inicio": null, "hora_fin": null, "cupo": null, "nota": "Vacaciones" },
    { "id": 7, "fecha": "2026-10-08", "tipo": "cerrado", "hora_inicio": null, "hora_fin": null, "cupo": null, "nota": "Vacaciones" }
  ]
}
```

Errores: `400 json_invalido` · `400 datos_invalidos`.

#### `DELETE /api/admin/excepciones/{id}` (UC10)

Borra la excepción. `204` sin cuerpo. Errores: `404 no_encontrado`.

### 3.6 Administrador: clientes e invitaciones

#### `GET /api/admin/clientes?estado` (UC07)

Lista los usuarios con rol `cliente`, los más recientes primero. `estado` opcional (`pendiente`, `aprobado` o `bloqueado`).

`200`:

```json
{
  "clientes": [
    { "id": 7, "usuario": "marcos_r", "telefono": "+34611222333", "estado": "pendiente", "precio_corte": 15.00, "creado_en": "2026-10-01T10:30:02", "aprobado_en": null }
  ]
}
```

Errores: `400 datos_invalidos` (`estado` fuera del enum).

#### `POST /api/admin/clientes/{id}/aprobar` (UC07)

Pasa el cliente a `aprobado` desde `pendiente` o `bloqueado` y guarda `aprobado_en`. Sin cuerpo.

`200`: el cliente con la forma de la lista, con `"estado": "aprobado"`.

Errores: `404 no_encontrado` (no existe o no es un cliente: el administrador no se puede aprobar ni bloquear) · `409 estado_invalido` (ya estaba `aprobado`).

#### `POST /api/admin/clientes/{id}/bloquear` (UC07)

Pasa el cliente a `bloqueado` desde `pendiente` o `aprobado`. Sin cuerpo. Surte efecto en la siguiente petición del cliente, aunque su token siga vigente (§6). No cancela sus citas (DP-13).

`200`: el cliente con `"estado": "bloqueado"`. Errores: `404 no_encontrado` · `409 estado_invalido` (ya estaba `bloqueado`).

#### `GET /api/admin/invitaciones` (UC06)

Lista las invitaciones, las más recientes primero. `caducada` no es un estado guardado: vale `true` cuando `estado` es `pendiente` y `caduca_en` ya ha pasado. D4 muestra cada invitación con su código, su nota y un chip de estado: Pendiente, Usada o Caducada (esta última cuando `caducada` es `true`).

`200`:

```json
{
  "invitaciones": [
    { "id": 4, "codigo": "W5HN8CZ3JEF6", "nota": null, "estado": "pendiente", "caducada": false, "caduca_en": "2026-10-08T11:05:00", "creada_en": "2026-10-01T11:05:00", "usada_por": null },
    { "id": 3, "codigo": "K7M2QX9PTD4R", "nota": "Marcos, el del gimnasio", "estado": "usada", "caducada": false, "caduca_en": "2026-10-08T09:15:00", "creada_en": "2026-10-01T09:15:00", "usada_por": { "id": 7, "usuario": "marcos_r" } }
  ]
}
```

#### `POST /api/admin/invitaciones` (UC06)

Genera un código de un solo uso con `caduca_en` = ahora + 7 días y `creada_por` = el administrador del token.

Petición (el cuerpo y la nota son opcionales):

```json
{ "nota": "Marcos, el del gimnasio" }
```

`201`:

```json
{ "id": 4, "codigo": "W5HN8CZ3JEF6", "nota": "Marcos, el del gimnasio", "estado": "pendiente", "caducada": false, "caduca_en": "2026-10-08T11:05:00", "creada_en": "2026-10-01T11:05:00", "usada_por": null }
```

Errores: `400 json_invalido` · `400 datos_invalidos` (`nota` de más de 60 caracteres).

## 4. Endpoints P2 (fuera de los diagramas obligatorios)

| Endpoint | Contrato resumido |
|---|---|
| `PUT /api/yo` | Cambia el `@usuario` del cliente `aprobado`. Cuerpo `{ "usuario": "marcos" }` con la misma normalización y regla que el registro. `200` con la cuenta (forma de `GET /api/yo`). Errores: `400 datos_invalidos`, `409 usuario_en_uso`. Tras el cambio, el siguiente login usa el nombre nuevo. |
| `POST /api/admin/citas` | Cita manual (pantalla D5). Cuerpo `{ "cliente_id": 7, "fecha": "2026-10-08", "hora_final": "18:00" }`. Nace `confirmada`, con `hora_pedida` igual a `hora_final` y `decidida_en` relleno. Salta el cupo, pero el índice único protege la hora exacta. `201` con la cita. Errores: `400 datos_invalidos`, `404 no_encontrado` (cliente), `409 cliente_no_aprobado`, `409 hueco_ocupado`. |
| `DELETE /api/admin/clientes/{id}` | Borrado real del cliente. Sus citas se borran en cascada (`ON DELETE CASCADE`); las invitaciones que usó se conservan con `usada_por` a `NULL`. No se puede borrar al administrador. `204`. Errores: `404 no_encontrado`. |
| `GET /` | Página de estado en HTML, CSS y JS, pública. No muestra datos personales. |
| `POST /api/admin/copia-seguridad` | Lanza `mysqldump` con `ProcessBuilder` y guarda el volcado fuera del repositorio. `200` con `{ "archivo": "clandestino-2026-10-01T11-30-00.sql", "bytes": 18234 }`. Errores: `500 copia_fallida`. |

## 5. Catálogo de códigos de error

El plan fija los códigos HTTP posibles (400, 401, 403, 404, 405, 409, 429) y cuatro códigos de error de reserva. El resto procede de `analisis/datos.json` y `analisis/logica.json` (traducidos) o son decisiones de diseño cerradas, marcadas con su `DP-xx` (`reglas-negocio.md` §11).

| Código | HTTP | Cuándo | Origen |
|---|---|---|---|
| `json_invalido` | 400 | El cuerpo falta o no es JSON válido. | Plan §3.2 |
| `cuerpo_demasiado_grande` | 400 | El cuerpo supera el límite (16 KiB). | Plan §3.2; DP-08, DP-12 |
| `datos_invalidos` | 400 | Falta un campo, el tipo o el formato no es correcto (fecha inexistente, hora que no es `HH:mm`, `usuario` fuera de `[a-z0-9_]{3,20}`, enum desconocido, `id` no numérico...). El `mensaje` nombra el campo. | Original |
| `fecha_fuera_de_horizonte` | 400 | La fecha es anterior a hoy o posterior a hoy + 13 días. | Decisión DP-01 |
| `hora_fuera_de_tramo` | 400 | La hora, con su hora de duración, no cabe en el tramo del día. | Plan §5 |
| `hora_invalida` | 400 | La hora no está alineada con la rejilla de huecos del tramo. | Original (el plan no le da código); DP-03 |
| `hora_pasada` | 400 | La fecha es hoy y la hora ya ha llegado. | Decisión DP-02 |
| `invitacion_invalida` | 400 | El código de invitación no existe. | Original |
| `no_autenticado` | 401 | Falta la cabecera `Authorization` o no es `Bearer`. Incluye `WWW-Authenticate: Bearer`. | |
| `token_invalido` | 401 | Token mal formado, con firma incorrecta o caducado, o de un usuario que ya no existe. | Plan §3.2 |
| `credenciales_invalidas` | 401 | Usuario o contraseña incorrectos. | Original |
| `no_aprobado` | 403 | El usuario del token está `pendiente` o `bloqueado` y la ruta no es `GET /api/yo`. El detalle lo da `GET /api/yo`. | Plan §3.2 |
| `sin_permiso` | 403 | El rol del usuario no corresponde a la ruta. | |
| `no_encontrado` | 404 | El recurso no existe o, para un cliente, es de otro cliente. | Original |
| `ruta_no_encontrada` | 404 | La ruta no existe. | Plan §3.2 |
| `metodo_no_permitido` | 405 | Método no previsto en una ruta existente. Incluye `Allow`. | Plan §3.2 |
| `dia_cerrado` | 409 | El día no está abierto (sin horario activo o con excepción `cerrado`). | Plan §5 |
| `dia_lleno` | 409 | El cupo del día está alcanzado. | Plan §5 |
| `hueco_ocupado` | 409 | Hay otra cita activa con la misma fecha y hora efectiva. Al pedir y al confirmar. | Plan §3.2 y §5 |
| `estado_invalido` | 409 | El estado actual del recurso no admite la operación. | |
| `cita_pasada` | 409 | La operación exige una cita que aún no ha pasado. | Original; DP-05, DP-06 |
| `cita_futura` | 409 | Se marca asistencia antes de que llegue la hora de la cita. | Original (`confirmada -> hecha` solo cuando la hora real ya pasó) |
| `duplicado` | 409 | El `@usuario` o el teléfono ya están registrados (no se indica cuál). | Original (`duplicate`) |
| `invitacion_usada` | 409 | La invitación ya se usó. | Original |
| `invitacion_caducada` | 409 | La invitación superó `caduca_en`. | Original |
| `demasiados_intentos` | 429 | El limitador bloquea la clave. Incluye `Retry-After` en segundos. | Plan §3.2; DP-08 |
| `error_interno` | 500 | Fallo no previsto. El cuerpo es genérico; el detalle va al registro del servidor. | DP-12 |
| `base_de_datos_no_disponible` | 503 | Solo en `GET /api/salud`. | DP-12 |
| `usuario_en_uso` (P2) | 409 | `PUT /api/yo` con un `@usuario` ya existente. | Original |
| `cliente_no_aprobado` (P2) | 409 | `POST /api/admin/citas` para un cliente que no está `aprobado`. | Original |
| `copia_fallida` (P2) | 500 | `mysqldump` termina con error. | |

## 6. Notas de seguridad

**Orden de proceso de cada petición.** Un fallo en un paso corta la petición y los pasos siguientes no se ejecutan:

1. Enrutador: ruta y método (`404`, `405`).
2. Limitador de intentos, solo en login y registro (`429`).
3. Autenticación: cabecera `Authorization: Bearer`, firma y caducidad del token (`401`).
4. Filtro de estado: lee el usuario en la base de datos en cada petición. Si no existe, `401 token_invalido`. El `rol` y el `estado` se toman de la base de datos, no del token.
5. Autorización: estado `aprobado` en todo salvo `GET /api/yo` (`403 no_aprobado`) y rol correcto (`403 sin_permiso`). Un cliente `pendiente` o `bloqueado` recibe `403` en todo salvo `GET /api/yo`, aunque su token siga vigente; por eso aprobar o bloquear surte efecto en la siguiente petición.
6. Lectura del cuerpo: límite de tamaño, JSON y formato de los campos (`400`).
7. Servicio y repositorio: reglas de negocio y SQL.

**Token.** JWT firmado con HS256 mediante `javax.crypto.Mac`, sin librería. Lleva solo `sub` (id del usuario), `iat` y `exp`. La clave (al menos 32 bytes aleatorios) llega por variable de entorno y no se guarda en el repositorio. La firma se compara en tiempo constante (`MessageDigest.isEqual`). No hay revocación en el servidor: cerrar sesión borra el token en la app, y el riesgo de un token robado queda acotado por su caducidad (DP-08) y por el filtro de estado.

**Contraseñas.** `pbkdf2-sha256$600000$sal$hash` (PBKDF2-HMAC-SHA256, sal aleatoria, 600 000 iteraciones; 1 000 en los tests). En el login, cuando el usuario no existe, la API calcula igualmente un hash de relleno para que el tiempo de respuesta no delate si el usuario existe.

**Limitador.** Cuenta en memoria, por clave y en una ventana de 15 minutos. En login la clave es dirección IP + `usuario`; en registro, la dirección IP. Cuentan los intentos fallidos (cualquier respuesta distinta de `200` o `201`) y un login correcto reinicia el contador de su clave. Con 5 fallos se responde `429 demasiados_intentos` con `Retry-After`. Desde el emulador todas las peticiones llegan con la misma dirección, `127.0.0.1` (el `10.0.2.2` del emulador es la interfaz de *loopback* del PC), por eso en login la clave incluye el `usuario`. Valores fijados en DP-08.

**Datos.** Todas las consultas usan `PreparedStatement`. La API se conecta con un usuario de MySQL sin privilegios de administración. Las respuestas de error no incluyen trazas ni textos de excepción. Las respuestas llevan `Cache-Control: no-store` y `X-Content-Type-Options: nosniff`. Ni la contraseña, ni el token, ni el teléfono se escriben en los registros.

**Transporte.** En P1 el tráfico va en HTTP plano solo contra el servidor local de desarrollo; la memoria lo declara como limitación y en producción haría falta TLS (HTTPS es P2). La app permite HTTP en claro solo en la variante `debug` y solo hacia `10.0.2.2` y `localhost` (`network_security_config`).

## 7. Parámetros de la API

| Parámetro | Valor | Origen |
|---|---|---|
| Horizonte de reserva | 14 días: de hoy a hoy + 13 | Plan §4 («dos semanas»), anclado a `Europe/Madrid` |
| Duración de una cita | 60 minutos | Plan §5 |
| Cupo por defecto | 2 citas activas al día | Plan §5, `schema.sql` |
| Estados que ocupan hueco y cupo | `pendiente` y `confirmada` | Plan §4 |
| Código de invitación | 12 caracteres, `SecureRandom`, alfabeto sin `0 O 1 I L`, un solo uso | Plan §3.2 (mínimo 12) |
| Caducidad de la invitación | 7 días | Plan §3.2 |
| PBKDF2 | HMAC-SHA256, 600 000 iteraciones | Plan §3.2 |
| Caducidad del token | 7 días | Decisión DP-08 |
| Limitador | 5 fallos por clave en 15 minutos | Decisión DP-08 |
| Tamaño máximo del cuerpo | 16 KiB | Decisión DP-08 |
| Ventana de «mis citas» | 30 días hacia atrás y todo lo futuro | `analisis/datos.json` |
| Pool de hilos del servidor | 8 | Plan §3.2 |
| Tiempos de espera de la app | 5 s de conexión y 10 s de lectura | Plan §3.1 |
