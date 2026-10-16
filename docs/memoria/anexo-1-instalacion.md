# Anexo I. Manual de instalación

Este anexo explica cómo poner en marcha el prototipo en un ordenador con Windows: la base de datos, la API y la app en el emulador de Android Studio. Los pasos se han comprobado en el equipo de desarrollo. Al final de cada bloque hay una comprobación para saber si ha ido bien antes de pasar al siguiente.

## I.1 Requisitos

| Elemento | Requisito |
|---|---|
| Sistema operativo | Windows 10 u 11 de 64 bits |
| Memoria | 8 GB de RAM como mínimo (el emulador reserva unos 2 GB) |
| Android Studio | Panda 4 o posterior, que admita Android Gradle Plugin 9.2. Trae su propio JDK 21 (JBR), que también sirve para la API |
| SDK de Android | Plataforma 36.1 y una imagen de sistema de Android 16 (API 36) para el emulador |
| MySQL | MySQL Community Server 9.7.2 LTS, en ZIP para Windows de 64 bits |
| Puertos libres | 3306 (MySQL) y 8080 (API), los dos solo en `127.0.0.1` |

*Tabla I.1. Requisitos de instalación. Fuente: elaboración propia.*

## I.2 Obtener el proyecto

[completar: URL del repositorio público de GitHub.]

```
git clone [URL del repositorio] C:\Dev\clandestino-tfg
```

Conviene usar una ruta corta y sin espacios ni tildes, porque Gradle y algunos scripts de MySQL dan problemas con ellas. En el resto del anexo se supone que el proyecto está en `C:\Dev\clandestino-tfg`.

## I.3 Base de datos

1. Descomprimir el ZIP de MySQL en `C:\Dev\mysql-9.7.2-winx64`.
2. Crear la carpeta `C:\Dev\mysql-data` y dentro un fichero `my.ini` con este contenido:

   ```
   [mysqld]
   basedir=C:/Dev/mysql-9.7.2-winx64
   datadir=C:/Dev/mysql-data/datos
   port=3306
   bind-address=127.0.0.1
   mysqlx=OFF
   character-set-server=utf8mb4
   collation-server=utf8mb4_0900_ai_ci
   innodb_buffer_pool_size=128M
   max_connections=30
   performance_schema=OFF
   log-error=C:/Dev/mysql-data/mysql-error.log

   [client]
   port=3306
   default-character-set=utf8mb4
   ```

   `bind-address=127.0.0.1` hace que MySQL solo acepte conexiones del propio ordenador.

3. Inicializar la carpeta de datos. El comando escribe en `mysql-error.log` una contraseña temporal para `root`:

   ```
   C:\Dev\mysql-9.7.2-winx64\bin\mysqld.exe --defaults-file=C:\Dev\mysql-data\my.ini --initialize
   ```

4. Arrancar MySQL con el script del proyecto:

   ```
   C:\Dev\clandestino-tfg\scripts\mysql.cmd iniciar
   ```

5. Entrar con la contraseña temporal y cambiarla por una propia:

   ```
   C:\Dev\mysql-9.7.2-winx64\bin\mysql.exe -u root -p
   ALTER USER 'root'@'localhost' IDENTIFIED BY 'nueva_contraseña';
   ```

Si MySQL está en otra carpeta, basta con definir las variables de entorno `MYSQL_BASE` y `MYSQL_DATOS` antes de usar los scripts.

**Comprobación:** `scripts\mysql.cmd estado` responde «MySQL está en marcha.».

## I.4 Fichero de configuración `.env`

1. Copiar `.env.example` como `.env` en la raíz del proyecto. Este fichero no se sube nunca al repositorio.
2. Rellenar los valores:
   - `DB_HOST=127.0.0.1`, `DB_PORT=3306` y `DB_NAME=clandestino`.
   - `DB_USER` y `DB_PASSWORD`: el usuario con el que entrará la API (por ejemplo `clandestino_app`) y una contraseña inventada. El usuario se crea en el paso siguiente.
   - `DB_ROOT_PASSWORD`: la contraseña de `root` del paso I.3.
   - `JWT_SECRET`: una clave aleatoria de al menos 32 caracteres. Se puede generar en PowerShell con:

     ```
     $b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
     ```

   - `API_PORT=8080`.

## I.5 Crear las tablas y el usuario de la API

```
cd C:\Dev\clandestino-tfg
scripts\bd.cmd cargar -Confirmo
scripts\bd.cmd usuario-app
```

El primero crea la base `clandestino` con sus cinco tablas y carga un horario de ejemplo (de lunes a jueves, de 16:00 a 22:00, con cupo 2). Borra las tablas si ya existían, y por eso exige `-Confirmo`. El segundo crea el usuario de `DB_USER`, que solo puede consultar y modificar filas, nunca crear ni borrar tablas.

**Comprobación (opcional):** `scripts\bd.cmd probar` pasa las 21 pruebas del esquema en una base aparte, sin tocar la real.

## I.6 API

1. Abrir PowerShell en la carpeta `api` y apuntar `JAVA_HOME` al JDK de Android Studio:

   ```
   cd C:\Dev\clandestino-tfg\api
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   ```

2. Crear la cuenta del barbero (solo la primera vez). Pide la contraseña por teclado:

   ```
   .\gradlew.bat crearAdmin --args="barbero +34600111222"
   ```

3. Arrancar la API:

   ```
   .\gradlew.bat run
   ```

**Comprobación:** en otra ventana, `curl http://127.0.0.1:8080/api/salud` devuelve `{"estado":"ok","base_de_datos":"ok"}`. Si MySQL está parado devuelve un error 503 `base_de_datos_no_disponible`.

**Pruebas (opcional):** `scripts\bd.cmd base-tests-api` crea la base vacía de pruebas, y después `.\gradlew.bat test` pasa las 88 pruebas de la API.

## I.7 App en el emulador

1. Abrir la carpeta `C:\Dev\clandestino-tfg\android` en Android Studio y esperar a que termine la sincronización de Gradle.
2. En *Device Manager*, crear un dispositivo virtual con una imagen de Android 16 (API 36). Conviene ponerle la zona horaria Europe/Madrid, porque la API trabaja siempre en hora de Madrid y así la app y la API coinciden.
3. Con la API en marcha, pulsar *Run* con la configuración `app` en modo *debug*.

La versión de depuración llama a la API en `http://10.0.2.2:8080`, que es la dirección con la que el emulador ve al ordenador. Para probar en un móvil real por cable se usa `adb reverse tcp:8080 tcp:8080` y se cambia esa URL por `http://localhost:8080` en `android/app/build.gradle`.

También se puede instalar el APK de depuración sin abrir Android Studio. Se genera con `.\gradlew.bat assembleDebug` desde la carpeta `android` y queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

**Comprobación:** la app abre la pantalla de inicio de sesión y entra con la cuenta del barbero creada en el paso I.6.

## I.8 Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| La app dice que no hay conexión | La API no está arrancada o escucha en otro puerto. Comprobar `/api/salud` desde el PC |
| `/api/salud` devuelve 503 | MySQL está parado: `scripts\mysql.cmd iniciar` |
| La API no arranca y dice «Falta … en el .env» | Falta el `.env` o una variable obligatoria (`DB_USER`, `DB_PASSWORD`, `JWT_SECRET`) |
| El selector de fechas sale en inglés | El emulador está en inglés. En un móvil en español no pasa |

*Tabla I.2. Problemas frecuentes de instalación. Fuente: elaboración propia.*
