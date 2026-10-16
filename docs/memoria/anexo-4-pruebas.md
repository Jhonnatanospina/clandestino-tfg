# Anexo IV. Casos de prueba manuales

Las pruebas automáticas (88 de la API, 25 de la app y 21 del esquema) se describen en el apartado 7.8. Este anexo recoge las que se hicieron a mano: el recorrido completo de la app en el emulador y unas comprobaciones de la API con `curl`.

## IV.1 Recorrido de la app en el emulador

Se hizo el 15 de octubre de 2026 en un emulador con Android 16 (API 36), pantalla de 1080 × 2400 y zona horaria Europe/Madrid, contra la API y la base de datos locales. Los casos van en el orden en que se ejecutaron, porque cada uno parte del estado que deja el anterior. Las capturas del anexo II se tomaron durante este recorrido.

| Id | Caso | Pasos | Resultado esperado | Resultado |
|---|---|---|---|---|
| CP-01 | Configurar horarios (UC10) | Entrar como barbero. En «Horarios», cambiar el tramo y el cupo de varios días y pulsar «Guardar». Añadir un día extra y unas vacaciones de varios días. | La plantilla se guarda. El día extra y las vacaciones aparecen en la lista, una fila por día de vacaciones. | Correcto (figuras II.9 a II.11) |
| CP-02 | Generar invitación (UC06) | En «Clientes», escribir una nota y pulsar «Generar». | Aparece un código de 12 caracteres con el botón de copiar y la invitación sale como pendiente en la lista. | Correcto (figura II.13) |
| CP-03 | Registrarse con código (UC01) | Cerrar sesión. En la entrada, pulsar «Crear cuenta con código» y rellenar el código del CP-02, el usuario `carlos_m`, un teléfono y una contraseña. | La cuenta se crea y la app muestra la pantalla de acceso pendiente. La invitación pasa a usada por `@carlos_m`. | Correcto (figuras II.2, II.3 y II.12) |
| CP-04 | Aprobar cliente (UC07) | Entrar como barbero y, en «Clientes», aprobar a `carlos_m`. Después, volver a entrar como cliente. | El cliente aparece como aprobado y, al entrar, ve su panel en lugar de la pantalla de espera. | Correcto |
| CP-05 | Solicitar cita (UC03 y UC04) | Como cliente, en «Pedir», tocar el jueves 22, elegir las 15:00 y pulsar el botón «Pedir cita». | La cita se crea y aparece como pendiente en «Mis citas». | Correcto (figuras II.4 y II.5) |
| CP-06 | Confirmar con otra hora (UC08 y UC09) | Como barbero, en «Solicitudes», cambiar la hora final a 15:30 y pulsar «Confirmar». Abrir «Agenda» en esa semana y tocar el jueves. | La solicitud desaparece de la lista y la cita sale confirmada a las 15:30 en la agenda. | Correcto (figuras II.7 y II.8) |
| CP-07 | Tema oscuro y cerrar sesión | En «Ajustes», activar el tema oscuro y después pulsar «Cerrar sesión». | La app pasa a colores oscuros y, al cerrar sesión, vuelve a la pantalla de entrada. | Correcto |

*Tabla IV.1. Casos de prueba manuales en el emulador. Fuente: elaboración propia.*

Durante el recorrido aparecieron ocho fallos visuales y de configuración que las pruebas automáticas no podían detectar, como el texto de las horas descentrado en «Pedir cita» o el botón «No vino» que parecía activo en una cita futura. Se corrigieron en el momento y se repitió el paso afectado. Están descritos en la entrada del 15 de octubre del diario técnico.

## IV.2 Comprobaciones de la API con `curl`

Se ejecutaron el 16 de octubre de 2026 contra la API en marcha en `http://127.0.0.1:8080`. Sirven para ver de un vistazo que la API responde con un código HTTP correcto y un error con nombre fijo en los casos más habituales. Estas mismas situaciones también están cubiertas por la clase de prueba `PruebaApi`.

| Id | Petición | Resultado esperado | Respuesta obtenida |
|---|---|---|---|
| CP-08 | `GET /api/salud` | 200 y base de datos disponible | 200 `{"estado":"ok","base_de_datos":"ok"}` |
| CP-09 | `GET /api/mis-citas` sin cabecera `Authorization` | 401 | 401 `no_autenticado` |
| CP-10 | `GET /api/mis-citas` con un token inventado | 401 | 401 `token_invalido` |
| CP-11 | `GET /api/nada` | 404 | 404 `ruta_no_encontrada` |
| CP-12 | `DELETE /api/salud` | 405 | 405 `metodo_no_permitido` |
| CP-13 | `POST /api/auth/login` con el cuerpo `{"usuario":` | 400 | 400 `json_invalido` |
| CP-14 | `POST /api/auth/login` con un cuerpo de unos 20 KB | 400, porque el límite es de 16 KiB | 400 `cuerpo_demasiado_grande` |
| CP-15 | `POST /api/auth/login` con un usuario que no existe | 401 con el mensaje genérico | 401 `credenciales_invalidas`, «Usuario o contraseña incorrectos.» |

*Tabla IV.2. Comprobaciones manuales de la API. Fuente: elaboración propia.*

Por ejemplo, el CP-09 se lanza así:

```
curl -i http://127.0.0.1:8080/api/mis-citas
```
