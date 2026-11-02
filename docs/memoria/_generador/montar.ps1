# Genera la memoria en Word desde los Markdown y la termina con Word instalado:
# calcula el índice con los números de página reales, guarda y exporta el PDF.
#
# Uso, desde cualquier carpeta:  powershell -File docs\memoria\_generador\montar.ps1

$ErrorActionPreference = 'Stop'
$memoria = Split-Path $PSScriptRoot -Parent
$plantilla = Join-Path $memoria '..\plantillas\plantilla-memoria-dam-linea2.docx'
$docx = Join-Path $memoria 'memoria-clandestino.docx'
$pdf = Join-Path $memoria 'memoria-clandestino.pdf'

python -P (Join-Path $PSScriptRoot 'generar_word.py') $plantilla $docx
if ($LASTEXITCODE -ne 0) { throw 'Ha fallado generar_word.py' }

# Word va en un trabajo aparte por dos motivos. Lanzado directamente desde
# algunas consolas se queda bloqueado al abrir el documento, y así además se
# le puede poner un límite de tiempo y cerrarlo si no responde.
$trabajo = Start-Job -ArgumentList $docx, $pdf -ScriptBlock {
    param($docx, $pdf)
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    try {
        $documento = $word.Documents.Open($docx)
        # El índice se actualiza dos veces porque al rellenarse ocupa más de
        # una página y eso mueve los números de todo lo que va detrás. Update()
        # ya repagina, y llamar antes a Repaginate() deja a Word colgado.
        $documento.TablesOfContents(1).Update()
        $documento.TablesOfContents(1).Update()
        $documento.Save()
        $documento.ExportAsFixedFormat($pdf, 17)  # 17 = PDF
        "Páginas: $($documento.ComputeStatistics(2))"
        $documento.Close(0)
    }
    finally {
        $word.Quit()
    }
}

if (-not (Wait-Job $trabajo -Timeout 300)) {
    Stop-Job $trabajo
    Get-Process WINWORD -ErrorAction SilentlyContinue | Stop-Process -Force
    throw 'Word no ha terminado en cinco minutos y se ha cerrado'
}
Receive-Job $trabajo
Remove-Job $trabajo
Write-Output "Listo: $docx y $pdf"
