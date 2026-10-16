# Operaciones sobre la base de datos de desarrollo.
#   scripts\bd.cmd cargar -Confirmo   Borra y recrea las tablas y carga el horario de ejemplo.
#   scripts\bd.cmd probar             Pasa las pruebas del esquema en una base aparte (clandestino_test).
#   scripts\bd.cmd usuario-app        Crea el usuario de MySQL con el que entra la API (DB_USER del .env).
#   scripts\bd.cmd base-tests-api     Recrea la base vacía que usan los tests de la API (clandestino_api_test).

param(
    [Parameter(Mandatory = $true)][ValidateSet('cargar', 'probar', 'usuario-app', 'base-tests-api')][string]$Accion,
    [switch]$Confirmo
)

. "$PSScriptRoot\_comun.ps1"

$esquema = Join-Path $RAIZ 'database\schema.sql'
$semilla = Join-Path $RAIZ 'database\seed.sql'
$pruebas = Join-Path $RAIZ 'database\pruebas-esquema.sql'

switch ($Accion) {

    'cargar' {
        if (-not $Confirmo) {
            Write-Host 'Esto BORRA las tablas de la base "clandestino" y las vuelve a crear.'
            Write-Host 'Si estás seguro, repite el comando con -Confirmo.'
            exit 1
        }
        $tmp = Join-Path $env:TEMP 'clandestino_carga.sql'
        Escribir-Utf8SinBom $tmp ((Get-Content $esquema -Raw -Encoding UTF8) + "`n" + (Get-Content $semilla -Raw -Encoding UTF8))
        $salida = Join-Path $env:TEMP 'clandestino_carga.txt'
        $codigo = Ejecutar-SqlComoRoot $tmp $salida
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
        if ($codigo -ne 0) { Get-Content $salida; Write-Host 'La carga falló.'; exit 1 }
        Write-Host 'Esquema y datos iniciales cargados en la base "clandestino".'
    }

    'probar' {
        # Copia del esquema apuntando a una base de pruebas, para no tocar la real.
        $texto = (Get-Content $esquema -Raw -Encoding UTF8) -creplace '\bclandestino\b', 'clandestino_test'
        $tmpEsquema = Join-Path $env:TEMP 'clandestino_test_esquema.sql'
        Escribir-Utf8SinBom $tmpEsquema $texto
        $tmpLimpiar = Join-Path $env:TEMP 'clandestino_test_limpiar.sql'
        Escribir-Utf8SinBom $tmpLimpiar 'DROP DATABASE IF EXISTS clandestino_test;'

        $null = Ejecutar-SqlComoRoot $tmpLimpiar (Join-Path $env:TEMP 'clandestino_test_a.txt')
        $codigo = Ejecutar-SqlComoRoot $tmpEsquema (Join-Path $env:TEMP 'clandestino_test_b.txt')
        if ($codigo -ne 0) { Get-Content (Join-Path $env:TEMP 'clandestino_test_b.txt'); Write-Host 'No se pudo crear la base de pruebas.'; exit 1 }

        $resultado = Join-Path $RAIZ 'database\pruebas-esquema.resultado.txt'
        $null = Ejecutar-SqlComoRoot $pruebas $resultado -Forzar
        $null = Ejecutar-SqlComoRoot $tmpLimpiar (Join-Path $env:TEMP 'clandestino_test_c.txt')
        Get-Content $resultado -Encoding UTF8
        Write-Host ''
        Write-Host "Resultado guardado en $resultado"
    }

    'usuario-app' {
        # La API no entra como root: entra con un usuario que solo puede leer y escribir filas
        # (ni crear ni borrar tablas). Si alguien consiguiera colar SQL, no podría tirar la base.
        $env_ = Leer-Env
        $usuario = $env_['DB_USER']
        # En SQL una comilla simple dentro del texto se escribe doble.
        $clave = $env_['DB_PASSWORD'] -replace "'", "''"
        $tmp = Join-Path $env:TEMP 'clandestino_usuario.sql'
        try {
            Escribir-Utf8SinBom $tmp @"
CREATE USER IF NOT EXISTS '$usuario'@'localhost' IDENTIFIED BY '$clave';
GRANT SELECT, INSERT, UPDATE, DELETE ON clandestino.* TO '$usuario'@'localhost';
"@
            $salida = Join-Path $env:TEMP 'clandestino_usuario.txt'
            $codigo = Ejecutar-SqlComoRoot $tmp $salida
        } finally {
            # El fichero lleva la contraseña: se borra pase lo que pase.
            Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
        }
        if ($codigo -ne 0) { Get-Content $salida; Write-Host 'No se pudo crear el usuario.'; exit 1 }
        Write-Host "Usuario $usuario listo con permisos de lectura y escritura sobre la base clandestino."
    }

    'base-tests-api' {
        # Los tests de la API borran y meten filas sin parar. Van en su propia base para no
        # tocar nunca los datos de "clandestino".
        $texto = (Get-Content $esquema -Raw -Encoding UTF8) -creplace '\bclandestino\b', 'clandestino_api_test'
        $usuario = (Leer-Env)['DB_USER']
        $texto = "DROP DATABASE IF EXISTS clandestino_api_test;`n" + $texto + "`nGRANT SELECT, INSERT, UPDATE, DELETE ON clandestino_api_test.* TO '$usuario'@'localhost';`n"
        $tmp = Join-Path $env:TEMP 'clandestino_api_test.sql'
        Escribir-Utf8SinBom $tmp $texto
        $salida = Join-Path $env:TEMP 'clandestino_api_test.txt'
        $codigo = Ejecutar-SqlComoRoot $tmp $salida
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
        if ($codigo -ne 0) { Get-Content $salida; Write-Host 'No se pudo crear la base de tests.'; exit 1 }
        Write-Host 'Base clandestino_api_test creada (vacía) para los tests de la API.'
    }
}
