# Clandestino · TFG Android (Java + MySQL)

App Android de citas para barberos que trabajan desde casa. La hice para mi propio uso, porque corto el pelo en casa y las apps del mercado están pensadas para locales a pie de calle o funcionan como un marketplace que enseña otras barberías a tus clientes. Aquí solo entran los clientes que invitas, tú decides cuántas citas aceptas cada día y la app se queda en lo justo. El barbero aprueba a sus clientes, confirma o rechaza sus solicitudes y lleva su agenda y sus horarios. Proyecto Intermodular de DAM, línea 2 (Universae, curso 2025-2026).

Arquitectura: app Android (Java, vistas XML, Material 3, MVVM) → API REST en Java 21 sobre el `HttpServer` del JDK → MySQL 9.7 en local. La app nunca se conecta a la base de datos.

## Estado

Prototipo P1 terminado y probado en el emulador, más la cita que el barbero crea a mano, que es la primera función de la segunda fase:

- 97 pruebas de la API, 26 de la app y 21 del esquema, todas en verde.
- Memoria en borrador, presentación de la defensa y guion del vídeo.

## Carpetas

| Carpeta | Contenido |
|---|---|
| `android/` | App Android |
| `api/` | API REST con sus pruebas JUnit |
| `database/` | Esquema (`schema.sql`), datos iniciales (`seed.sql`) y pruebas del esquema |
| `scripts/` | Arrancar y parar MySQL, cargar el esquema y preparar las bases |
| `docs/diseno/` | Casos de uso, requisitos, contrato de la API y reglas de negocio |
| `docs/diagramas/` · `docs/mockups/` | Diagramas y mockups en draw.io, con los scripts que los generan |
| `docs/capturas/` | Capturas de la app en el emulador |
| `docs/memoria/` | Memoria del proyecto y anexos |
| `docs/presentacion/` | Presentación de la defensa y su generador |

## Puesta en marcha (Windows)

Los pasos completos, con lo que hay que instalar, están en el [anexo I de la memoria](docs/memoria/anexo-1-instalacion.md). En corto:

1. Copia `.env.example` como `.env` y rellena las contraseñas y la clave `JWT_SECRET`.
2. `scripts\mysql.cmd iniciar` arranca MySQL (también existen `parar` y `estado`).
3. `scripts\bd.cmd cargar -Confirmo` crea las tablas y carga un horario de ejemplo. **Borra los datos que hubiera.**
4. `scripts\bd.cmd usuario-app` crea el usuario de MySQL con el que entra la API.
5. Desde `api`, con `JAVA_HOME` apuntando a un JDK 21, `.\gradlew.bat crearAdmin --args="barbero +34600111222"` crea la cuenta del barbero y `.\gradlew.bat run` arranca la API en `127.0.0.1:8080`.
6. Abre `android` en Android Studio y lánzala en un emulador con API 36. La versión de depuración llama a la API en `http://10.0.2.2:8080`.

## Pruebas

- Esquema: `scripts\bd.cmd probar`.
- API: `scripts\bd.cmd base-tests-api` y después `.\gradlew.bat test` desde `api`.
- App: `.\gradlew.bat test` desde `android`.

MySQL se espera en `C:\Dev\mysql-9.7.2-winx64` y sus datos en `C:\Dev\mysql-data`. Se puede cambiar con las variables de entorno `MYSQL_BASE` y `MYSQL_DATOS`.
