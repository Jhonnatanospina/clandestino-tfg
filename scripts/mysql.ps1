# Arranca, para o comprueba el servidor MySQL de desarrollo.
#   scripts\mysql.cmd iniciar | parar | estado
# MySQL se lanza a mano (no como servicio de Windows) para que solo gaste memoria
# mientras trabajas en el proyecto.

param(
    [Parameter(Mandatory = $true)][ValidateSet('iniciar', 'parar', 'estado')][string]$Accion
)

. "$PSScriptRoot\_comun.ps1"

function Esta-Respondiendo {
    cmd /c "`"$MYSQL_BASE\bin\mysqladmin.exe`" --defaults-file=`"$MYSQL_INI`" -u root --connect-timeout=2 ping > nul 2>&1"
    return ($LASTEXITCODE -eq 0)
}

switch ($Accion) {
    'estado' {
        if (Esta-Respondiendo) { Write-Host 'MySQL está en marcha.' } else { Write-Host 'MySQL está parado.' }
    }
    'iniciar' {
        if (Esta-Respondiendo) { Write-Host 'MySQL ya estaba en marcha.'; return }
        Start-Process -FilePath "$MYSQL_BASE\bin\mysqld.exe" -ArgumentList "--defaults-file=$MYSQL_INI" -WindowStyle Hidden
        for ($i = 0; $i -lt 40; $i++) {
            Start-Sleep -Milliseconds 750
            if (Esta-Respondiendo) { Write-Host 'MySQL arrancado.'; return }
        }
        Write-Host "MySQL no respondió a tiempo. Revisa $MYSQL_DATOS\mysql-error.log"
        exit 1
    }
    'parar' {
        $env_ = Leer-Env
        $env:MYSQL_PWD = $env_['DB_ROOT_PASSWORD']
        try {
            cmd /c "`"$MYSQL_BASE\bin\mysqladmin.exe`" --defaults-file=`"$MYSQL_INI`" -u root shutdown > nul 2>&1"
        } finally {
            $env:MYSQL_PWD = ''
        }
        Write-Host 'Orden de apagado enviada.'
    }
}
