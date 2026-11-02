# Monta la presentacion final sobre la plantilla oficial. Lo llama generar.js al terminar.
# Se queda con la portada y los agradecimientos de la plantilla, borra las diapositivas de
# instrucciones y mete en medio las de contenido que acaba de dibujar pptxgenjs.
# Va con PowerPoint por COM porque asi el patron, el logo y el tema siguen siendo los de la
# plantilla, sin copiar a mano su XML.
param(
    [Parameter(Mandatory)] [string]$Plantilla,
    [Parameter(Mandatory)] [string]$Contenido,
    [Parameter(Mandatory)] [string]$Textos,
    [Parameter(Mandatory)] [string]$Salida,
    [Parameter(Mandatory)] [string]$Pdf
)
$ErrorActionPreference = 'Stop'

# Los textos llegan en un JSON en UTF-8 para que las tildes no dependan de la codificacion de este script
$t = Get-Content $Textos -Raw -Encoding UTF8 | ConvertFrom-Json

# El movil llega ya dibujado por generar.js (marco, sombra y captura en una sola imagen) y con
# su sitio en puntos. Ni borde ni sombra de PowerPoint: el visor del iPhone pinta como PDF las
# imagenes que los llevan, y esos PDF se le cuelan en otras diapositivas.
function PonerMovil($diapositiva, $movil, $nombre) {
    $foto = $diapositiva.Shapes.AddPicture($movil.ruta, 0, -1, $movil.x, $movil.y, $movil.w, $movil.h)
    $foto.Name = $nombre
}

$app = New-Object -ComObject PowerPoint.Application
# Se abre como solo lectura para no tocar nunca la plantilla original
$pres = $app.Presentations.Open($Plantilla, -1, 0, 0)
try {
    $portada = $pres.Slides(1)

    # El titulo vive dentro de un grupo junto a la barrita turquesa. Se deshace el grupo porque
    # el iPhone tambien pinta los grupos como PDF. Por dentro son dos rectangulos y no cambia nada.
    $portada.Shapes('Grupo 1').GroupItems('CuadroTexto 20').TextFrame.TextRange.Text = $t.portada.titulo
    [void]$portada.Shapes('Grupo 1').Ungroup()
    $sub = $portada.Shapes.AddTextbox(1, 108, 140, 560, 70)
    $sub.Name = 'Subtitulo'
    $sub.TextFrame.WordWrap = -1
    $sub.TextFrame.TextRange.Text = $t.portada.subtitulo
    $sub.TextFrame.TextRange.Font.Name = 'Raleway'
    $sub.TextFrame.TextRange.Font.Size = 20
    $sub.TextFrame.TextRange.Font.Color.RGB = 0xFFFFFF
    # Nombre, tutor, curso y ciclo, uno por linea, con el formato que ya traia la plantilla
    $datos = $portada.Shapes('Title 14').TextFrame.TextRange
    $datos.Text = ($t.portada.datos -join "`r")
    # La plantilla encogia el texto con autoajuste. Con cuatro lineas se fija el tamano a mano.
    $portada.Shapes('Title 14').TextFrame2.AutoSize = 0
    $datos.Font.Size = 22
    # La plantilla deja poner una foto en la portada. Va una captura de la app a la derecha.
    PonerMovil $portada $t.portada.movil 'Captura portada'

    # Fuera las diapositivas de instrucciones (2 a 7 y la 9). Se borran de atras adelante
    # para que los numeros no se muevan mientras tanto.
    foreach ($n in 9, 7, 6, 5, 4, 3, 2) { $pres.Slides($n).Delete() }

    # Ahora quedan la portada y los agradecimientos. El contenido entra entre las dos.
    # Entran con su propio diseno, que ya trae dibujados la ola y el escudo
    $metidas = $pres.Slides.InsertFromFile($Contenido, 1)

    # La del video de respaldo queda oculta: solo se va a ella si la demo en directo falla
    $pres.Slides([int]$t.oculta).SlideShowTransition.Hidden = -1

    $gracias = $pres.Slides($pres.Slides.Count)
    $cuerpo = $gracias.Shapes('TextBox 12').TextFrame.TextRange
    $cuerpo.Text = ($t.agradecimientos.lineas -join "`r")
    $cuerpo.Paragraphs(1).Font.Size = 28
    $cuerpo.Paragraphs(1).Font.Bold = -1
    $cuerpo.Paragraphs(2).Font.Size = 16
    $cuerpo.Paragraphs(2).ParagraphFormat.SpaceBefore = 12
    $gracias.Shapes('TextBox 12').Width = 560
    PonerMovil $gracias $t.agradecimientos.movil 'Captura agradecimientos'

    # La ola de esta diapositiva es una imagen volteada, otra cosa que el iPhone pasa a PDF.
    # Se cambia por la ola que generar.js ya ha volteado en el propio fichero, en el mismo
    # sitio y al fondo, que es donde estaba.
    $vieja = $gracias.Shapes('Imagen 1')
    $ola = $gracias.Shapes.AddPicture($t.ola, 0, -1, $vieja.Left, $vieja.Top, $vieja.Width, $vieja.Height)
    $ola.ZOrder(1)  # 1 = al fondo
    $vieja.Delete()
    $ola.Name = 'Ola'

    # Notas del orador de las dos diapositivas de la plantilla (las demas ya traen las suyas)
    $portada.NotesPage.Shapes.Placeholders(2).TextFrame.TextRange.Text = $t.portada.notas
    $gracias.NotesPage.Shapes.Placeholders(2).TextFrame.TextRange.Text = $t.agradecimientos.notas

    # Secciones con los apartados de la plantilla, para moverse rapido en el modo moderador
    $sec = $pres.SectionProperties
    while ($sec.Count -gt 0) { $sec.Delete(1, 0) }
    foreach ($s in $t.secciones) { [void]$sec.AddBeforeSlide([int]$s.desde, $s.nombre) }

    # La plantilla trae como autor a quien la hizo. Se cambia para que no salga en el trabajo.
    # Estas propiedades no tienen tipo para PowerShell, por eso se llega a ellas con InvokeMember
    $props = $pres.BuiltInDocumentProperties
    foreach ($par in @(('Title', 'Clandestino'), ('Author', 'Jhonnatan Ospina Henao'))) {
        $prop = [System.__ComObject].InvokeMember('Item', 'GetProperty', $null, $props, @($par[0]))
        [void][System.__ComObject].InvokeMember('Value', 'SetProperty', $null, $prop, @($par[1]))
    }

    $pres.SaveAs($Salida, 24)  # 24 = pptx
    # Y una copia en PDF, que en el movil se ve con la letra buena. La oculta no sale.
    $pres.SaveCopyAs($Pdf, 32)  # 32 = pdf
    Write-Output ("Montadas {0} diapositivas de contenido ({1} en total)" -f $metidas, $pres.Slides.Count)
}
finally {
    $pres.Close()
    $app.Quit()
}
