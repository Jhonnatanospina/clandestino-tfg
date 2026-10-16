# Utilidades que comparten los scripts: dónde está MySQL y cómo leer el .env.
# Si instalaste MySQL en otra carpeta, define las variables de entorno
# MYSQL_BASE y MYSQL_DATOS antes de lanzar los scripts.

$RAIZ        = Split-Path -Parent $PSScriptRoot
$MYSQL_BASE  = if ($env:MYSQL_BASE)  { $env:MYSQL_BASE }  else { 'C:\Dev\mysql-9.7.2-winx64' }
$MYSQL_DATOS = if ($env:MYSQL_DATOS) { $env:MYSQL_DATOS } else { 'C:\Dev\mysql-data' }
$MYSQL_INI   = Join-Path $MYSQL_DATOS 'my.ini'

# Lee el fichero .env (líneas CLAVE=valor, ignorando comentarios) y devuelve un hashtable.
function Leer-Env {
    $ruta = Join-Path $RAIZ '.env'
    if (-not (Test-Path $ruta)) { throw "No existe $ruta. Copia .env.example a .env y rellénalo." }
    $valores = @{}
    foreach ($linea in Get-Content $ruta) {
        if ($linea -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $valores[$Matches[1]] = $Matches[2].Trim() }
    }
    return $valores
}

# Ejecuta el cliente mysql como root contra un fichero .sql y guarda salida y errores
# juntos en $salida, en el orden en que ocurrieron. Con -Forzar sigue aunque una
# sentencia falle (así las pruebas que deben fallar no cortan el script).
function Ejecutar-SqlComoRoot([string]$fichero, [string]$salida, [switch]$Forzar) {
    $env_ = Leer-Env
    $env:MYSQL_PWD = $env_['DB_ROOT_PASSWORD']
    try {
        $opciones = if ($Forzar) { '--force --table' } else { '' }
        cmd /c "`"$MYSQL_BASE\bin\mysql.exe`" --defaults-file=`"$MYSQL_INI`" -u root $opciones < `"$fichero`" > `"$salida`" 2>&1"
        return $LASTEXITCODE
    } finally {
        $env:MYSQL_PWD = ''
    }
}

function Escribir-Utf8SinBom([string]$ruta, [string]$texto) {
    [System.IO.File]::WriteAllText($ruta, $texto, (New-Object System.Text.UTF8Encoding($false)))
}
