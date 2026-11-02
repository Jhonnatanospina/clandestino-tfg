// Genera docs/presentacion/clandestino-defensa.pptx sobre la plantilla oficial de Universae,
// y al lado una copia en PDF.
// Las diapositivas siguen los apartados de la plantilla y, dentro de ellos, el orden de la memoria.
// Ejecutar desde esta carpeta: node generar.js (hace falta PowerPoint instalado para el montaje).
//
// El trabajo va en dos pasos. Aquí pptxgenjs dibuja las diapositivas de contenido con el estilo
// de la plantilla (ola, barra turquesa, Raleway). Luego montar.ps1 abre la plantilla con
// PowerPoint, rellena su portada y sus agradecimientos y mete en medio estas diapositivas.
// Así el patrón, el logo y el tema son los de la plantilla y no una copia.

const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const pptxgen = require("pptxgenjs");
const JSZip = require("jszip");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const fa = require("react-icons/fa");

const RAIZ = path.resolve(__dirname, "../../..");
const PLANTILLA = path.join(RAIZ, "docs/plantillas/plantilla-ppt-dam-linea2.pptx");
const CONTENIDO = path.join(__dirname, "contenido.pptx");
const TEXTOS = path.join(__dirname, "textos.json");
const SALIDA = path.resolve(__dirname, "../clandestino-defensa.pptx");
// Copia en PDF para verla en el móvil, donde el visor no tiene la letra de la plantilla
const SALIDA_PDF = path.resolve(__dirname, "../clandestino-defensa.pdf");
const captura = (n) => path.join(RAIZ, "docs/capturas", n + ".png");
const mockup = (n) => path.join(RAIZ, "docs/mockups", n + ".png");
const diagrama = (n) => path.join(RAIZ, "docs/diagramas", n + ".png");
const VIDEO = path.resolve(__dirname, "../demo-respaldo.mp4");

// Los colores van en hexadecimal y no como colores del tema: al pasar a la plantilla, el tema
// es el de Office que trae ella, y un color del tema cambiaría de golpe.
const COL = {
  azul: "134C8F", turquesa: "45B8AC", turquesaTexto: "1C8379",
  texto: "1F2D3D", texto2: "55657A", gris: "8A97A8",
  tarjeta: "EEF3F9", blanco: "FFFFFF", celeste: "BFD6F0",
  rojo: "DD0426", marcoMovil: "1E1E21",
};
// Raleway es la letra de la plantilla. Es una fuente de la nube de Office, así que
// PowerPoint la descarga solo si el ordenador no la tiene.
const LETRA = "Raleway";
const TITULAR = "Raleway SemiBold";

// Zona útil de las diapositivas de contenido. Por debajo de 5,7" empieza la ola.
const X0 = 0.85, ANCHO = 11.65, Y0 = 1.9;

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13,333 × 7,5 pulgadas, igual que la plantilla
pres.defineSlideMaster({ title: "CONTENIDO", background: { color: COL.blanco } });

// ---------- Ayudas ----------

// El visor que traen el iPhone y el Mac no es PowerPoint. Los rectángulos lisos los pinta bien,
// pero cualquier otra forma (esquinas redondeadas, círculos, líneas, sombras, imágenes volteadas)
// la convierte en un PDF, y por un fallo de su caché esos PDF acaban saliendo en otras
// diapositivas. Por eso aquí todo lo que no es un rectángulo va dibujado dentro de una imagen.
const PPP = 400; // píxeles por pulgada de esas imágenes
const px = (pulgadas) => Math.round(pulgadas * PPP);
const comoDato = (png) => "image/png;base64," + png.toString("base64");
const svgAPng = (svg) => sharp(Buffer.from(svg)).png().toBuffer();

function svgIcono(nombre, color, tam) {
  return ReactDOMServer.renderToStaticMarkup(React.createElement(fa[nombre], { color: "#" + color, size: tam }));
}

async function icono(nombre, color, tam = 256) {
  return comoDato(await svgAPng(svgIcono(nombre, color, tam)));
}

// Icono blanco en un círculo azul: es el motivo que se repite en todas las diapositivas.
// Sin icono queda el círculo solo, que es el que llevan los pasos numerados.
async function circulo(slide, nombre, x, y, d, id) {
  const lado = 512, m = lado * 0.26;
  const dentro = nombre ? `<g transform="translate(${m} ${m})">${svgIcono(nombre, COL.blanco, lado - 2 * m)}</g>` : "";
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${lado}" height="${lado}">` +
    `<circle cx="${lado / 2}" cy="${lado / 2}" r="${lado / 2}" fill="#${COL.azul}"/>${dentro}</svg>`;
  slide.addImage({ data: comoDato(await svgAPng(svg)), x, y, w: d, h: d, objectName: id + "-circulo" });
}

// Captura de pantalla dentro de un marco oscuro que hace de móvil. Marco, sombra y captura van
// en una sola imagen, que sobresale un poco del marco para que quepa la sombra. Sin captura
// queda solo el marco, que es lo que lleva detrás el vídeo de respaldo.
const BORDE_MOVIL = 0.07, SOMBRA = 0.2;
async function imagenMovil(imagen, alto, proporcion) {
  const ancho = alto * proporcion;
  const marco = { w: ancho + 2 * BORDE_MOVIL, h: alto + 2 * BORDE_MOVIL };
  const total = { w: marco.w + 2 * SOMBRA, h: marco.h + 2 * SOMBRA };
  const rect = (bajada, resto) => `<rect x="${px(SOMBRA)}" y="${px(SOMBRA + bajada)}" width="${px(marco.w)}" ` +
    `height="${px(marco.h)}" rx="${px(0.14)}" ${resto}/>`;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${px(total.w)}" height="${px(total.h)}">` +
    `<filter id="difuminado" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="${px(0.05)}"/></filter>` +
    rect(0.04, `fill="#000000" fill-opacity="0.2" filter="url(#difuminado)"`) +
    rect(0, `fill="#${COL.marcoMovil}"`) + "</svg>";
  let lienzo = sharp(Buffer.from(svg));
  if (imagen) {
    const pantalla = await sharp(imagen).resize(px(ancho), px(alto), { fit: "fill" }).toBuffer();
    lienzo = lienzo.composite([{ input: pantalla, left: px(SOMBRA + BORDE_MOVIL), top: px(SOMBRA + BORDE_MOVIL) }]);
  }
  return { png: await lienzo.png().toBuffer(), marco, total };
}

// (x, y) es la esquina del marco, no la de la sombra. Devuelve el ancho del marco.
async function movil(slide, imagen, x, y, alto, proporcion, id) {
  const m = await imagenMovil(imagen, alto, proporcion);
  slide.addImage({ data: comoDato(m.png), x: x - SOMBRA, y: y - SOMBRA, w: m.total.w, h: m.total.h, objectName: id });
  return m.marco.w;
}

// Los móviles de la portada y de los agradecimientos los pone montar.ps1, que trabaja en puntos
// (72 por pulgada), así que se le pasan en un fichero y con su sitio ya calculado.
async function movilSuelto(imagen, x, y, alto, nombre) {
  const m = await imagenMovil(imagen, alto, PROP_CAPTURA);
  const ruta = path.join(__dirname, nombre + ".png");
  fs.writeFileSync(ruta, m.png);
  const pt = (pulgadas) => +(pulgadas * 72).toFixed(2);
  return { ruta, x: pt(x - SOMBRA), y: pt(y - SOMBRA), w: pt(m.total.w), h: pt(m.total.h) };
}

// Flecha de doble punta. Con una línea de PowerPoint el iPhone volvería a sacar un PDF.
async function flechaDoble(ancho, alto, color) {
  const w = px(ancho), h = px(alto), punta = px(0.11), grosor = px(2.5 / 72);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" fill="#${color}">` +
    `<rect x="${punta - 1}" y="${(h - grosor) / 2}" width="${w - 2 * punta + 2}" height="${grosor}"/>` +
    `<polygon points="0,${h / 2} ${punta},0 ${punta},${h}"/>` +
    `<polygon points="${w},${h / 2} ${w - punta},0 ${w - punta},${h}"/></svg>`;
  return comoDato(await svgAPng(svg));
}

// Las tarjetas son rectángulos de esquina viva a propósito, por lo del visor del iPhone.
function tarjeta(slide, x, y, w, h, id, color = COL.tarjeta) {
  slide.addShape(pres.shapes.RECTANGLE, { x, y, w, h, fill: { color }, line: { type: "none" }, objectName: id });
}

function texto(slide, contenido, opciones) {
  slide.addText(contenido, Object.assign({ isTextBox: true, margin: 0, valign: "top", color: COL.texto, fontFace: LETRA }, opciones));
}

function vineta(lineas, opciones = {}) {
  return lineas.map((l, i) => ({ text: l, options: Object.assign({ bullet: { indent: 14 }, paraSpaceAfter: 6,
    breakLine: i < lineas.length - 1 }, opciones) }));
}

// La ola de abajo y el escudo de arriba salen de la propia plantilla, así no se guarda
// una copia de sus imágenes. La plantilla pone la ola volteada. Aquí se voltea la imagen
// misma, porque una imagen volteada es otra de las cosas que el iPhone pasa a PDF.
// La ola de los agradecimientos la cambia montar.ps1, por eso también se guarda en un fichero.
const imagenPlantilla = {};
const OLA = path.join(__dirname, "ola.png");
async function cargarImagenesPlantilla() {
  const zip = await JSZip.loadAsync(fs.readFileSync(PLANTILLA));
  const leer = (ruta) => zip.file(ruta).async("nodebuffer");
  const ola = await sharp(await leer("ppt/media/image4.png")).flop().png().toBuffer();
  fs.writeFileSync(OLA, ola);
  imagenPlantilla.ola = comoDato(ola);
  imagenPlantilla.escudo = comoDato(await leer("ppt/media/image1.png"));
}

// Cada diapositiva lleva la cabecera de la plantilla: barra turquesa, título del apartado en
// azul y, debajo, de qué va esa diapositiva en concreto.
const secciones = [];
let numero = 1; // la 1 es la portada de la plantilla
function contenido(apartado, subtitulo) {
  const s = pres.addSlide({ masterName: "CONTENIDO" });
  numero++;
  if (!secciones.length || secciones[secciones.length - 1].nombre !== apartado) secciones.push({ nombre: apartado, desde: numero });
  s.addImage({ data: imagenPlantilla.ola, x: 0, y: 5.6955, w: 13.333, h: 1.8048, objectName: "ola" });
  // En la plantilla el escudo lo pone su diseño de diapositiva, pero al pasar estas
  // diapositivas a ese diseño PowerPoint deja de dibujarlas. Por eso va aquí, en el mismo sitio.
  s.addImage({ data: imagenPlantilla.escudo, x: 12.3359, y: 0.277, w: 0.5807, h: 0.6396, objectName: "escudo" });
  s.addShape(pres.shapes.RECTANGLE, { x: 0.528, y: 0.542, w: 0.167, h: 1.111, fill: { color: COL.turquesa }, line: { type: "none" }, objectName: "barra" });
  texto(s, apartado, { x: 0.85, y: 0.52, w: 11.2, h: 0.62, fontSize: 32, fontFace: TITULAR, color: COL.azul, valign: "middle" });
  texto(s, subtitulo, { x: 0.85, y: 1.17, w: 11.2, h: 0.42, fontSize: 18, color: COL.turquesaTexto, valign: "middle" });
  return s;
}

const PROP_CAPTURA = 1080 / 2400;
const PROP_MOCKUP = 776 / 1536;

const INTRO = "1. Introducción al tema", OBJETIVOS = "2. Objetivos planteados", DISENO = "3. Diseño y funcionalidad",
  METODO = "4. Metodología y planificación", DESARROLLO = "5. Desarrollo", CONCLUSIONES = "6. Conclusiones y proyección a futuro";

// ---------- Diapositivas ----------

async function construir() {
  await cargarImagenesPlantilla();

  // Introducción
  let s = contenido(INTRO, "Una app para el barbero que trabaja en casa");
  const rasgos = [
    ["FaHome", "Desde casa", "Sin local a pie de calle y con una clientela propia y fiel."],
    ["FaTicketAlt", "Clientes por invitación", "Solo entra quien recibe un código y el barbero aprueba."],
    ["FaCalendarCheck", "Cupo diario", "Cada día admite un número máximo de citas."],
  ];
  for (const [i, [ic, cab, desc]] of rasgos.entries()) {
    const y = Y0 + 0.05 + i * 1.02;
    await circulo(s, ic, X0, y, 0.65, "rasgo-" + i);
    texto(s, cab, { x: X0 + 0.9, y: y - 0.02, w: 5.0, h: 0.36, fontSize: 18, bold: true, color: COL.azul });
    texto(s, desc, { x: X0 + 0.9, y: y + 0.36, w: 5.0, h: 0.5, fontSize: 14, color: COL.texto2 });
  }
  texto(s, "La hice para mí. Ya uso una versión web propia y aquí rehago su núcleo como app Android nativa.",
    { x: X0, y: 5.0, w: 5.9, h: 0.6, fontSize: 13, italic: true, color: COL.texto2 });
  const datos = [
    ["96,3 %", "de las personas de 16 a 74 años usó Internet en los últimos tres meses", "INE, 2025"],
    ["94,6 %", "de los internautas usa WhatsApp de forma habitual", "CNMC, 2026"],
    ["60,2 %", "de las empresas de servicios personales no tiene asalariados", "INE, DIRCE"],
  ];
  datos.forEach(([cifra, que, fuente], i) => {
    const y = Y0 + i * 1.27;
    tarjeta(s, 7.2, y, 5.3, 1.12, "dato-" + i);
    texto(s, cifra, { x: 7.45, y: y + 0.16, w: 1.9, h: 0.8, fontSize: 32, fontFace: TITULAR, color: COL.azul, valign: "middle" });
    texto(s, que, { x: 9.4, y: y + 0.14, w: 2.9, h: 0.62, fontSize: 13, valign: "middle" });
    texto(s, fuente, { x: 9.4, y: y + 0.78, w: 2.9, h: 0.22, fontSize: 10, color: COL.gris });
  });
  s.addNotes("Corto el pelo en casa, y las apps que encontré están pensadas para locales a pie de calle o, como Booksy, son un marketplace que enseña otras barberías a tus clientes. Clandestino es para barberos como yo, que trabajan desde casa y quieren cuidar a su propia clientela. Solo entran clientes invitados, cada día tiene un máximo de citas y la app se queda en lo justo. Los datos públicos dicen que casi todo el mundo usa el móvil a diario y que en este sector predomina el negocio sin empleados, así que tiene sentido una app pensada primero para el teléfono.");

  // Mercado
  s = contenido(INTRO, "Análisis de mercado: siete apps y ninguna cumple las tres reglas");
  const cab = (t) => ({ text: t, options: { bold: true, color: COL.blanco, fill: { color: COL.azul } } });
  const filas = [
    ["Booksy", "34,99 € + IVA al mes y comisiones", "Marketplace que enseña otras barberías"],
    ["Fresha", "19,95 € al mes y tarifa por cliente nuevo", "Pensada para captar clientes"],
    ["Treatwell", "25 % de la primera reserva de cada cliente nuevo", "Su negocio es traer clientes nuevos"],
    ["AgendaPro", "Desde 10 € al mes", "Personal, inventario y caja"],
    ["Square", "Gratis, con comisión por cobro", "Vive de su pasarela de pago"],
    ["Setmore", "Gratis hasta 4 usuarios, Pro a 5 $", "Generalista, sin aprobación de clientes"],
    ["Calendly", "Gratis con un evento, 10 $ por usuario", "Sin cupo diario ni aprobaciones"],
  ].map((f, i) => f.map((t, j) => ({ text: t, options: { bold: j === 0, fill: { color: i % 2 ? COL.blanco : COL.tarjeta } } })));
  s.addTable([[cab("App"), cab("Precio"), cab("Por qué no encaja")], ...filas], {
    x: X0, y: Y0, w: 8.35, colW: [1.3, 3.45, 3.6], rowH: 0.4, fontSize: 12, color: COL.texto, fontFace: LETRA,
    border: { type: "none" }, margin: [3, 7, 3, 7], valign: "middle", objectName: "tabla-mercado" });
  texto(s, "Fuente: webs oficiales de cada aplicación, consultadas el 30-09-2026.", { x: X0, y: 5.36, w: 8.35, h: 0.25, fontSize: 10, color: COL.gris });
  tarjeta(s, 9.45, Y0, 3.05, 3.7, "tarjeta-clandestino", COL.azul);
  texto(s, "CLANDESTINO", { x: 9.7, y: 2.1, w: 2.6, h: 0.45, fontSize: 22, fontFace: TITULAR, color: COL.blanco });
  const reglas = ["Acceso solo por invitación y aprobación", "Cupo diario configurable", "Sin cuotas ni comisiones"];
  for (const [i, r] of reglas.entries()) {
    const y = 2.75 + i * 0.68;
    s.addImage({ data: await icono("FaCheck", COL.turquesa), x: 9.7, y: y + 0.03, w: 0.24, h: 0.24, objectName: "regla-" + i });
    texto(s, r, { x: 10.1, y, w: 2.25, h: 0.6, fontSize: 13, color: COL.blanco });
  }
  texto(s, "A cambio, no sirve para salones con varios empleados ni cobra desde la app.",
    { x: 9.7, y: 4.75, w: 2.6, h: 0.75, fontSize: 12, color: COL.celeste });
  s.addNotes("Revisé siete aplicaciones de reservas en sus webs oficiales. Las especializadas, como Booksy, Fresha o Treatwell, giran en torno a un escaparate público que trae clientes nuevos a cambio de cuotas o comisiones y que, de paso, enseña la competencia a los clientes que ya tienes. Las generalistas, como Setmore o Calendly, no entienden de cupos ni de aprobar clientes. Ninguna junta las tres reglas que necesita un barbero que trabaja en casa, y ese es el hueco del proyecto.");

  // Objetivos
  s = contenido(OBJETIVOS, "Un objetivo general y cinco específicos");
  tarjeta(s, X0, Y0, ANCHO, 1.0, "objetivo-general", COL.azul);
  texto(s, "OBJETIVO GENERAL", { x: X0 + 0.3, y: Y0 + 0.13, w: ANCHO - 0.6, h: 0.25, fontSize: 11, bold: true, color: COL.turquesa, charSpacing: 2 });
  texto(s, "Desarrollar una app Android, con su API y su base de datos, para que un barbero que trabaja desde casa gestione las citas de sus clientes con acceso por invitación, cupo diario y sin reservas duplicadas.",
    { x: X0 + 0.3, y: Y0 + 0.4, w: ANCHO - 0.6, h: 0.52, fontSize: 14, color: COL.blanco });
  const objetivos = [
    ["FaDatabase", "Base de datos", "MySQL que impide por sí misma dos citas a la misma hora"],
    ["FaServer", "API REST", "Java sin frameworks, con contraseñas protegidas, tokens y roles"],
    ["FaUsers", "Cupo seguro", "Se respeta aunque varios clientes pidan a la vez, y una prueba con hilos lo demuestra"],
    ["FaMobileAlt", "App Android", "MVVM, siete pantallas y la red fuera del hilo principal"],
    ["FaCheckCircle", "Pruebas", "Unitarias, de integración y un recorrido completo en el emulador"],
  ];
  const anchoObj = (ANCHO - 4 * 0.2) / 5;
  for (const [i, [ic, nombre, desc]] of objetivos.entries()) {
    const x = X0 + i * (anchoObj + 0.2);
    tarjeta(s, x, 3.1, anchoObj, 2.5, "objetivo-" + (i + 1));
    await circulo(s, ic, x + 0.22, 3.3, 0.6, "objetivo-" + (i + 1));
    texto(s, (i + 1) + ". " + nombre, { x: x + 0.22, y: 4.05, w: anchoObj - 0.4, h: 0.32, fontSize: 15, bold: true, color: COL.azul });
    texto(s, desc, { x: x + 0.22, y: 4.42, w: anchoObj - 0.4, h: 1.1, fontSize: 12, color: COL.texto2 });
  }
  s.addNotes("El objetivo general es que un barbero que trabaja en casa lleve sus citas desde el móvil con sus tres reglas y sin reservas duplicadas. Lo concreté en cinco objetivos: una base de datos que bloquee por sí sola las dobles reservas, una API en Java sin frameworks, un cupo que aguante peticiones a la vez, una app Android con MVVM y pruebas que demuestren todo lo anterior.");

  // Mockups
  s = contenido(DISENO, "Mockups: del dibujo a la pantalla real");
  const altoMov = 3.3;
  const anchoMock = await movil(s, mockup("C2-pedir-cita"), X0, Y0, altoMov, PROP_MOCKUP, "mockup-c2");
  s.addImage({ data: await icono("FaArrowRight", COL.turquesa), x: X0 + anchoMock + 0.22, y: 3.35, w: 0.42, h: 0.42, objectName: "flecha-mockup" });
  const xCaptura = X0 + anchoMock + 0.85;
  const anchoCap = await movil(s, captura("C2-pedir-cita"), xCaptura, Y0, altoMov, PROP_CAPTURA, "captura-c2");
  texto(s, "Mockup en draw.io", { x: X0 - 0.2, y: 5.4, w: anchoMock + 0.4, h: 0.3, fontSize: 12, color: COL.texto2, align: "center" });
  texto(s, "App en el emulador", { x: xCaptura - 0.2, y: 5.4, w: anchoCap + 0.4, h: 0.3, fontSize: 12, color: COL.texto2, align: "center" });
  texto(s, "11", { x: 6.1, y: Y0, w: 1.2, h: 0.85, fontSize: 52, fontFace: TITULAR, color: COL.azul });
  texto(s, "mockups dibujados antes de escribir ningún layout, uno por pantalla y variante", { x: 7.3, y: Y0 + 0.08, w: 5.2, h: 0.7, fontSize: 15, valign: "middle" });
  texto(s, vineta([
    "Identidad de mi marca: rojo para la acción principal y Anton en los títulos",
    "Componentes de Material 3, con zonas táctiles de 48 dp y tema claro y oscuro",
    "Al construirla, el calendario se quedó en las dos semanas reservables, sin flechas",
    "La agenda del barbero se quedó en vista semanal",
  ]), { x: 6.1, y: 3.05, w: 6.4, h: 2.55, fontSize: 14 });
  s.addNotes("Antes de programar dibujé once mockups en draw.io, uno por pantalla. A la izquierda está el de «Pedir cita» y a la derecha cómo quedó en la app. Se parecen mucho. El cambio principal es que el calendario solo enseña las dos semanas que se pueden reservar, así que sobraban las flechas para cambiar de mes.");

  // Modelo de datos
  s = contenido(DISENO, "Modelo de datos: cinco tablas contra la doble reserva");
  const altoER = 3.75;
  s.addImage({ path: diagrama("02-er"), x: X0, y: Y0 - 0.05, w: altoER * 1440 / 2010, h: altoER, objectName: "diagrama-er" });
  const xDatos = 3.95;
  const anchos = [1.25, 1.5, 2.05, 1.8, 0.85];
  ["usuarios", "invitaciones", "horarios_semanales", "excepciones_dia", "citas"].forEach((t, i) => {
    const x = xDatos + anchos.slice(0, i).reduce((a, b) => a + b + 0.14, 0);
    tarjeta(s, x, Y0, anchos[i], 0.38, "chip-" + t, t === "citas" ? COL.azul : COL.tarjeta);
    texto(s, t, { x, y: Y0, w: anchos[i], h: 0.38, fontSize: 12, fontFace: "Courier New", bold: true, align: "center", valign: "middle",
      color: t === "citas" ? COL.blanco : COL.texto });
  });
  tarjeta(s, xDatos, 2.45, 8.55, 1.32, "codigo-hora-ocupada", COL.azul);
  texto(s, [
    { text: "hora_ocupada TIME GENERATED ALWAYS AS (", options: { breakLine: true } },
    { text: "  IF(estado IN ('pendiente','confirmada'),", options: { breakLine: true } },
    { text: "     COALESCE(hora_final, hora_pedida), NULL))", options: { breakLine: true } },
    { text: "UNIQUE KEY uq_citas_hueco_activo (fecha, hora_ocupada)", options: { color: COL.turquesa, bold: true } },
  ], { x: xDatos + 0.25, y: 2.6, w: 8.1, h: 1.05, fontSize: 13, fontFace: "Courier New", color: COL.blanco });
  texto(s, vineta([
    "Una cita cancelada o rechazada deja la columna a NULL, y el índice único admite varios NULL.",
    "Una segunda cita activa a la misma hora choca con el índice (error 1062) y la API responde 409 hueco_ocupado.",
    "El precio se copia en la cita al marcarla como hecha, así un cambio de tarifa no altera el historial.",
  ]), { x: xDatos, y: 3.98, w: 8.55, h: 1.65, fontSize: 13.5 });
  s.addNotes("La base de datos tiene cinco tablas. La pieza más interesante está en «citas». Una columna generada, hora_ocupada, vale la hora de la cita solo mientras está pendiente o confirmada, y si no vale nulo. Con un índice único sobre la fecha y esa columna, MySQL rechaza por sí mismo una segunda cita activa a la misma hora, sin depender de la API.");

  // Casos de uso
  s = contenido(DISENO, "Casos de uso: diez casos, todos programados");
  const altoCU = 3.75;
  s.addImage({ path: diagrama("03-casos-de-uso"), x: X0, y: Y0 - 0.05, w: altoCU * 1674 / 1904, h: altoCU, objectName: "diagrama-casos" });
  const xActores = 4.6, anchoActores = 12.5 - xActores;
  const actores = [
    ["FaUserPlus", "Visitante", "UC01 Registrarse con código"],
    ["FaUser", "Cliente", "UC03 Consultar disponibilidad · UC04 Solicitar cita · UC05 Gestionar mis citas"],
    ["FaCut", "Barbero", "UC06 Generar invitación · UC07 Aprobar o bloquear · UC08 Solicitudes · UC09 Agenda · UC10 Horarios"],
  ];
  for (const [i, [ic, actor, casos]] of actores.entries()) {
    const y = Y0 + i * 1.0;
    tarjeta(s, xActores, y, anchoActores, 0.88, "actor-" + actor);
    await circulo(s, ic, xActores + 0.2, y + 0.16, 0.56, "actor-" + actor);
    texto(s, actor, { x: xActores + 0.95, y: y + 0.1, w: anchoActores - 1.15, h: 0.3, fontSize: 15, bold: true, color: COL.azul });
    texto(s, casos, { x: xActores + 0.95, y: y + 0.4, w: anchoActores - 1.15, h: 0.45, fontSize: 12, color: COL.texto2 });
  }
  texto(s, "UC02 Iniciar sesión es común a cliente y barbero.", { x: xActores, y: 4.95, w: anchoActores, h: 0.3, fontSize: 13, italic: true, color: COL.texto2 });
  texto(s, [
    { text: "30", options: { fontFace: TITULAR, fontSize: 22, color: COL.azul } }, { text: " requisitos funcionales    ", options: { fontSize: 13 } },
    { text: "53", options: { fontFace: TITULAR, fontSize: 22, color: COL.azul } }, { text: " no funcionales", options: { fontSize: 13 } },
  ], { x: xActores, y: 5.25, w: anchoActores, h: 0.42, valign: "middle" });
  s.addNotes("Hay tres actores y diez casos de uso. El visitante solo puede registrarse con un código. El cliente consulta los días libres, pide cita y gestiona las suyas, y el barbero lleva las invitaciones, los clientes, las solicitudes, la agenda y los horarios. De ahí salieron treinta requisitos funcionales, y la memoria tiene una tabla que relaciona cada caso con su pantalla y sus rutas.");

  // Metodología
  s = contenido(METODO, "Ciclo incremental · 78 horas estimadas junto al trabajo");
  const etapas = [["Inicio", "30 sep – 1 oct"], ["Diseño", "1 – 9 oct"], ["Desarrollo", "9 – 17 oct"], ["Cierre", "16 – 31 oct"]];
  const anchoEtapa = 2.6, hueco = (ANCHO - 4 * anchoEtapa) / 3;
  for (const [i, [et, fechas]] of etapas.entries()) {
    const x = X0 + i * (anchoEtapa + hueco);
    tarjeta(s, x, Y0, anchoEtapa, 0.8, "etapa-" + et, i === 3 ? COL.azul : COL.tarjeta);
    texto(s, et, { x: x + 0.22, y: Y0 + 0.09, w: anchoEtapa - 0.44, h: 0.34, fontSize: 16, bold: true, color: i === 3 ? COL.blanco : COL.azul });
    texto(s, fechas, { x: x + 0.22, y: Y0 + 0.44, w: anchoEtapa - 0.44, h: 0.28, fontSize: 12, color: i === 3 ? COL.celeste : COL.texto2 });
    if (i < 3) s.addImage({ data: await icono("FaChevronRight", COL.turquesa), x: x + anchoEtapa + hueco / 2 - 0.13, y: Y0 + 0.27, w: 0.26, h: 0.26, objectName: "flecha-etapa-" + i });
  }
  // La proporción se lee del PNG porque el Gantt crece cada vez que gana una tarea
  const gantt = await sharp(diagrama("08-gantt")).metadata();
  const altoG = 2.75, anchoG = altoG * gantt.width / gantt.height;
  s.addImage({ path: diagrama("08-gantt"), x: (13.333 - anchoG) / 2, y: 2.9, w: anchoG, h: altoG, objectName: "gantt" });
  s.addNotes("Seguí un ciclo incremental. Primero diseñé todo el sistema y después lo construí por piezas que se podían probar solas: la base de datos, la API y por último la app. De Scrum tomé la lista de trabajo por prioridades, P1, P2 y P3, y los ciclos cortos con revisión al final. El Gantt muestra las cuatro etapas. Lo macizo ya está hecho y lo discontinuo es el cierre. La última barra del desarrollo es la cita a mano, de la segunda fase. He compaginado el proyecto con el trabajo, con unas tres horas por día entre semana y entre dos y cuatro horas en todo el fin de semana. La estimación total es de 78 horas, incluido el cierre pendiente.");

  // Arquitectura
  s = contenido(DESARROLLO, "Arquitectura: la app nunca toca la base de datos");
  const piezas = [
    ["FaMobileAlt", "APP ANDROID", ["Java 17 y vistas XML", "Material 3", "MVVM con LiveData", "Red en un hilo aparte", "Sesión cifrada con el Keystore"]],
    ["FaServer", "API REST", ["Java 21 y HttpServer del JDK", "Rutas y JSON escritos a mano", "24 rutas y 8 hilos", "Capas http, seguridad, servicio y repositorio"]],
    ["FaDatabase", "MYSQL 9.7", ["5 tablas", "Índice único contra dobles reservas", "Usuario de la API sin permisos sobre las tablas", "Escucha solo en 127.0.0.1"]],
  ];
  const anchoPieza = 3.2, huecoPieza = (ANCHO - 3 * anchoPieza) / 2;
  const xs = [0, 1, 2].map((i) => X0 + i * (anchoPieza + huecoPieza));
  for (const [i, [ic, nombre, lista]] of piezas.entries()) {
    tarjeta(s, xs[i], Y0, anchoPieza, 3.2, "pieza-" + i);
    await circulo(s, ic, xs[i] + 0.25, Y0 + 0.22, 0.62, "pieza-" + i);
    texto(s, nombre, { x: xs[i] + 1.02, y: Y0 + 0.3, w: anchoPieza - 1.15, h: 0.45, fontSize: 18, fontFace: TITULAR, color: COL.azul, valign: "middle" });
    texto(s, vineta(lista, { paraSpaceAfter: 4 }), { x: xs[i] + 0.25, y: Y0 + 1.05, w: anchoPieza - 0.45, h: 2.05, fontSize: 12.5 });
  }
  const enlaces = [["HTTP + JSON", "token JWT"], ["JDBC", "PreparedStatement"]];
  for (const [i, [a, b]] of enlaces.entries()) {
    const x = xs[i] + anchoPieza + 0.08, w = xs[i + 1] - x - 0.08;
    s.addImage({ data: await flechaDoble(w, 0.16, COL.turquesa), x, y: 3.42, w, h: 0.16, objectName: "enlace-" + i });
    // Las etiquetas invaden un poco el margen de las tarjetas para que «PreparedStatement» quepa en una línea
    texto(s, a, { x: x - 0.4, y: 3.0, w: w + 0.8, h: 0.28, fontSize: 11, bold: true, align: "center", color: COL.texto2 });
    texto(s, b, { x: x - 0.4, y: 3.62, w: w + 0.8, h: 0.28, fontSize: 10, align: "center", color: COL.texto2 });
  }
  texto(s, "Solo la API habla con MySQL, así las credenciales de la base nunca viajan dentro del APK.",
    { x: X0, y: 5.25, w: ANCHO, h: 0.35, fontSize: 14, italic: true, color: COL.texto2 });
  s.addNotes("El sistema tiene tres piezas. La app no se conecta nunca a MySQL. Si lo hiciera, las credenciales irían dentro del APK y cualquiera podría sacarlas. La app habla por HTTP con la API, enviando JSON y un token, y la API es la única que entra en la base de datos. La API no usa ningún framework. Va sobre el servidor HTTP que trae el JDK, y las rutas, el JSON y las transacciones están escritos a mano para poder explicarlos.");

  // Seguridad
  s = contenido(DESARROLLO, "Seguridad revisada con OWASP MASVS");
  const medidas = [
    ["FaKey", "Contraseñas", "PBKDF2-HMAC-SHA256 con 600.000 iteraciones y una sal de 16 bytes"],
    ["FaIdBadge", "Token de sesión", "JWT HS256 firmado a mano con una clave de 32 bytes o más, válido 7 días"],
    ["FaLock", "Sesión en el móvil", "Cifrada con AES-GCM y una clave que vive en el Android Keystore"],
    ["FaUserShield", "Intentos de acceso", "Tras 5 fallos en 15 minutos la API responde 429"],
    ["FaDatabase", "Consultas", "Siempre con PreparedStatement, nunca SQL concatenado"],
    ["FaNetworkWired", "Red y copias", "HTTP en claro solo hacia el emulador en debug, y copias de seguridad de la app desactivadas"],
  ];
  const anchoMed = (ANCHO - 2 * 0.25) / 3;
  for (const [i, [ic, nombre, desc]] of medidas.entries()) {
    const x = X0 + (i % 3) * (anchoMed + 0.25), y = i < 3 ? Y0 : Y0 + 1.95;
    tarjeta(s, x, y, anchoMed, 1.78, "medida-" + i);
    await circulo(s, ic, x + 0.22, y + 0.2, 0.55, "medida-" + i);
    texto(s, nombre, { x: x + 0.95, y: y + 0.27, w: anchoMed - 1.15, h: 0.4, fontSize: 16, bold: true, color: COL.azul, valign: "middle" });
    texto(s, desc, { x: x + 0.22, y: y + 0.9, w: anchoMed - 0.44, h: 0.8, fontSize: 12.5, color: COL.texto2 });
  }
  s.addNotes("La seguridad la revisé con las categorías del estándar OWASP MASVS. Las contraseñas se guardan con PBKDF2, que repite el cálculo seiscientas mil veces para dificultar que alguien pruebe contraseñas de forma masiva. La sesión es un JWT que firmo yo con HMAC, y en el móvil se guarda cifrado con una clave del Keystore de Android. Además hay un límite de intentos de acceso y todas las consultas van con PreparedStatement, para separar los datos del código SQL.");

  // Concurrencia
  s = contenido(DESARROLLO, "Concurrencia: dos clientes y una sola plaza libre");
  const pasos = ["Cada petición cuenta las citas del día y ve 0 de 1", "Las dos creen que queda sitio e insertan su cita", "El día acaba con dos citas cuando el cupo era una"];
  for (const [i, p] of pasos.entries()) {
    const y = Y0 + i * 0.68;
    await circulo(s, null, X0, y, 0.48, "paso-" + i);
    texto(s, String(i + 1), { x: X0, y, w: 0.48, h: 0.48, fontSize: 16, bold: true, align: "center", valign: "middle", color: COL.blanco });
    texto(s, p, { x: X0 + 0.7, y: y + 0.02, w: 4.7, h: 0.46, fontSize: 14, valign: "middle" });
  }
  tarjeta(s, X0, 4.0, 5.4, 1.62, "solucion", COL.azul);
  texto(s, "LA SOLUCIÓN", { x: X0 + 0.28, y: 4.15, w: 4.9, h: 0.28, fontSize: 11, bold: true, color: COL.turquesa, charSpacing: 2 });
  texto(s, "Un cerrojo por fecha con synchronized. Contar e insertar van dentro de la misma transacción, y el índice único queda como última defensa.",
    { x: X0 + 0.28, y: 4.48, w: 4.9, h: 1.05, fontSize: 13.5, color: COL.blanco });
  // Las barras son rectángulos y no un gráfico de PowerPoint, porque el iPhone pinta los gráficos
  // como PDF. La escala va de 0 a 6 citas para que la barra de 5 no toque el título.
  const barras = [["Cupo 1, sin cerrojo", 5, COL.rojo], ["Cupo 1, con cerrojo", 1, COL.turquesa], ["Cupo 5, misma hora, sin cerrojo", 1, COL.turquesa]];
  const xGrafico = 6.65, anchoGrafico = 5.85, base = 4.45, porCita = (base - 2.3) / 6;
  const columna = anchoGrafico / barras.length, anchoBarra = columna / 1.6;
  texto(s, "Citas creadas con 5 peticiones a la vez", { x: xGrafico, y: 1.85, w: anchoGrafico, h: 0.35, fontSize: 13, color: COL.azul, align: "center", valign: "middle" });
  barras.forEach(([caso, citas, color], i) => {
    const x = xGrafico + i * columna + (columna - anchoBarra) / 2, alto = citas * porCita;
    s.addShape(pres.shapes.RECTANGLE, { x, y: base - alto, w: anchoBarra, h: alto, fill: { color }, line: { type: "none" }, objectName: "grafico-barra-" + i });
    texto(s, String(citas), { x, y: base - alto - 0.36, w: anchoBarra, h: 0.32, fontSize: 15, bold: true, align: "center", valign: "bottom" });
    texto(s, caso, { x: xGrafico + i * columna + 0.05, y: base + 0.1, w: columna - 0.1, h: 0.55, fontSize: 11, color: COL.texto2, align: "center" });
  });
  s.addShape(pres.shapes.RECTANGLE, { x: xGrafico, y: base, w: anchoGrafico, h: 0.015, fill: { color: COL.celeste }, line: { type: "none" }, objectName: "grafico-base" });
  texto(s, "Prueba automática: 5 hilos que salen a la vez con un CountDownLatch y un repositorio que tarda 300 ms en contar.",
    { x: 6.65, y: 5.22, w: 5.85, h: 0.4, fontSize: 11, color: COL.texto2 });
  s.addNotes("Esta es la parte más difícil del proyecto. Si dos clientes piden a la vez la última plaza del día, cada petición cuenta las citas, ve que queda sitio e inserta. El índice único no lo evita porque las horas son distintas, y ningún índice puede contar las citas de un día. Lo resolví con un cerrojo por fecha y una transacción que cuenta e inserta junta. La prueba lo demuestra: sin cerrojo entran cinco citas en un día con cupo uno, y con cerrojo solo una.");

  // Pruebas
  s = contenido(DESARROLLO, "Pruebas: 144 pruebas automáticas en verde");
  const cifras = [
    ["97", "pruebas de la API", "11 clases de JUnit 6 con una base de datos aparte y un reloj que se puede mover"],
    ["26", "pruebas de la app", "Modelos, calendario, lista de horas, formatos y validaciones del registro"],
    ["21", "pruebas del esquema", "Restricciones, índice único y datos de ejemplo en MySQL"],
  ];
  const anchoCifra = (ANCHO - 2 * 0.25) / 3;
  cifras.forEach(([n, que, det], i) => {
    const x = X0 + i * (anchoCifra + 0.25);
    tarjeta(s, x, Y0, anchoCifra, 1.95, "cifra-" + n);
    texto(s, n, { x: x + 0.28, y: Y0 + 0.1, w: 1.6, h: 0.8, fontSize: 46, fontFace: TITULAR, color: COL.azul });
    texto(s, que, { x: x + 0.28, y: Y0 + 0.92, w: anchoCifra - 0.56, h: 0.32, fontSize: 15, bold: true });
    texto(s, det, { x: x + 0.28, y: Y0 + 1.27, w: anchoCifra - 0.56, h: 0.6, fontSize: 12, color: COL.texto2 });
  });
  const manuales = [
    ["FaMobileAlt", "Recorrido en el emulador", "10 casos, del registro con código a la cita que el barbero mete a mano, todos correctos"],
    ["FaTerminal", "Comprobaciones con curl", "8 respuestas de la API revisadas a mano: 200, 401, 404, 405 y 400 con su código de error"],
  ];
  const anchoMan = (ANCHO - 0.25) / 2;
  for (const [i, [ic, nombre, desc]] of manuales.entries()) {
    const x = X0 + i * (anchoMan + 0.25);
    tarjeta(s, x, 4.1, anchoMan, 1.5, "manual-" + i);
    await circulo(s, ic, x + 0.25, 4.3, 0.6, "manual-" + i);
    texto(s, nombre, { x: x + 1.1, y: 4.27, w: anchoMan - 1.35, h: 0.34, fontSize: 15, bold: true, color: COL.azul });
    texto(s, desc, { x: x + 1.1, y: 4.65, w: anchoMan - 1.35, h: 0.8, fontSize: 12.5, color: COL.texto2 });
  }
  s.addNotes("En total hay ciento cuarenta y cuatro pruebas automáticas y todas pasan. Las de la API van contra una base de datos aparte y con un reloj que puedo mover, para comprobar reglas como que no se puede pedir una hora pasada. Además recorrí la app entera en el emulador siguiendo diez casos de prueba y comprobé a mano las respuestas de error de la API.");

  // Demostración
  s = contenido(DESARROLLO, "Demostración: la app funcionando en el emulador");
  const demo = [["A1-inicio-sesion", "Entrar"], ["C2-pedir-cita", "Pedir cita"], ["D1-solicitudes", "Solicitudes"], ["D2-agenda", "Agenda"],
    ["D5-cita-manual", "Cita a mano"], ["D3-horarios", "Horarios"]];
  const altoDemo = 3.3, anchoDemo = altoDemo * PROP_CAPTURA + 0.14, huecoDemo = (ANCHO - demo.length * anchoDemo) / (demo.length - 1);
  for (const [i, [img, nombre]] of demo.entries()) {
    const x = X0 + i * (anchoDemo + huecoDemo);
    await movil(s, captura(img), x, Y0, altoDemo, PROP_CAPTURA, "demo-" + img);
    // Cada rótulo ocupa su móvil y la mitad del hueco a cada lado, así no pisa al de al lado
    texto(s, (i + 1) + " · " + nombre, { x: x - huecoDemo / 2, y: 5.32, w: anchoDemo + huecoDemo, h: 0.3, fontSize: 13, bold: true, color: COL.azul, align: "center" });
  }
  s.addNotes("Ahora lo enseño funcionando en el emulador, con la API y MySQL en marcha en el ordenador.\n\n1. Entro como cliente y pido una cita para el día libre que elegiste antes.\n2. Cierro sesión y entro como barbero. En «Solicitudes» aparece la cita nueva.\n3. La confirmo moviéndola media hora con el selector de hora final.\n4. Voy a «Agenda» y la cita sale en esa semana como confirmada.\n5. Desde la misma agenda pulso «Nueva cita» y meto la de un cliente que ha llamado por teléfono. Se guarda ya confirmada y la agenda salta a su día.\n6. Termino en «Horarios», donde se fijan el tramo y el cupo de cada día.\n\nSi la demo fallara, paso a la diapositiva siguiente, que está oculta y tiene el vídeo de respaldo.");

  // Vídeo de respaldo. Va en una diapositiva oculta: en la presentación se la salta, y solo se
  // llega a ella a propósito si la demo en directo falla.
  s = contenido(DESARROLLO, "Demostración: vídeo de respaldo");
  const altoVideo = 3.72, anchoVideo = altoVideo * 720 / 1600;
  const xVideo = (13.333 - anchoVideo) / 2;
  // El mismo marco de móvil que las capturas, dibujado detrás del vídeo
  await movil(s, null, xVideo - BORDE_MOVIL, Y0 - 0.04 - BORDE_MOVIL, altoVideo, 720 / 1600, "video-marco");
  s.addMedia({ type: "video", path: VIDEO, x: xVideo, y: Y0 - 0.04, w: anchoVideo, h: altoVideo,
    cover: "image/png;base64," + fs.readFileSync(captura("A1-inicio-sesion")).toString("base64"), objectName: "video-demo" });
  texto(s, vineta(["Cliente pide cita el martes 20 a las 11:00", "El barbero la confirma a las 11:30", "Sale en la agenda y se ven los horarios"]),
    { x: X0, y: 2.6, w: 3.6, h: 2.0, fontSize: 13, color: COL.texto2 });
  texto(s, "Vídeo: 66 segundos, sin sonido. Cita manual: captura del diálogo.",
    { x: 8.95, y: 1.95, w: 3.55, h: 0.75, fontSize: 13, italic: true, color: COL.texto2 });
  s.addImage({ path: captura("D5-cita-manual"), x: 9.75, y: 2.85, w: 1.1025, h: 2.45, objectName: "respaldo-cita-manual" });
  s.addNotes("Este vídeo muestra la solicitud del cliente, la confirmación del barbero y la agenda. Dura 66 segundos. A la derecha se ve además una captura de la cita manual, que se añadió después de grabarlo. En ese diálogo el barbero elige un cliente aprobado, el día y la hora, y la cita se guarda confirmada.");
  const diapositivaOculta = numero;

  // Conclusiones
  s = contenido(CONCLUSIONES, "Objetivos cumplidos y lo que falta");
  const anchoCol = (ANCHO - 2 * 0.25) / 3;
  tarjeta(s, X0, Y0, anchoCol, 3.72, "col-cumplido", COL.azul);
  texto(s, "5 / 5", { x: X0 + 0.3, y: Y0 + 0.12, w: 3.0, h: 0.85, fontSize: 46, fontFace: TITULAR, color: COL.blanco });
  texto(s, "objetivos cumplidos", { x: X0 + 0.3, y: Y0 + 0.98, w: 3.0, h: 0.36, fontSize: 17, bold: true, color: COL.turquesa });
  texto(s, vineta(["Prototipo P1 completo, más la cita a mano de la P2", "Los 30 requisitos funcionales programados", "Recorrido completo documentado con capturas"]),
    { x: X0 + 0.3, y: Y0 + 1.5, w: anchoCol - 0.55, h: 2.1, fontSize: 13, color: COL.blanco });
  const columnas = [
    ["FaExclamationTriangle", "Limitaciones", ["Sin HTTPS, solo apto para uso local", "El cerrojo vive en la memoria de una única API", "Las solicitudes pendientes no caducan solas", "Sin pruebas de interfaz ni copia automática"]],
    ["FaRocket", "Trabajo futuro", ["Resto de la P2, como la caducidad de las solicitudes", "Avisos al cliente", "Resumen mensual de ingresos", "API publicada con HTTPS"]],
  ];
  for (const [i, [ic, nombre, lista]] of columnas.entries()) {
    const x = X0 + (i + 1) * (anchoCol + 0.25);
    tarjeta(s, x, Y0, anchoCol, 3.72, "col-" + nombre);
    await circulo(s, ic, x + 0.28, Y0 + 0.22, 0.6, "col-" + nombre);
    texto(s, nombre, { x: x + 1.05, y: Y0 + 0.3, w: anchoCol - 1.3, h: 0.45, fontSize: 18, bold: true, color: COL.azul, valign: "middle" });
    texto(s, vineta(lista), { x: x + 0.28, y: Y0 + 1.05, w: anchoCol - 0.55, h: 2.55, fontSize: 13 });
  }
  s.addNotes("Los cinco objetivos están cumplidos y el prototipo cubre todo el ciclo de la cita. De la segunda fase ya está hecha la cita a mano. Tiene límites claros: no usa HTTPS, así que solo vale en local, el cerrojo del cupo solo funciona con una copia de la API y las solicitudes no caducan solas. Como trabajo futuro queda el resto de la segunda fase, los avisos al cliente y publicar la API con HTTPS. La parte que más aporta al aprendizaje es comprobar con una prueba por qué el índice único no basta para proteger el cupo diario.");

  await pres.writeFile({ fileName: CONTENIDO });

  // Lo que montar.ps1 escribe en las dos diapositivas que se quedan de la plantilla.
  const textos = {
    portada: {
      titulo: "CLANDESTINO",
      subtitulo: "App Android de citas para barberos que trabajan desde casa",
      datos: ["Jhonnatan Ospina Henao", "Tutor: Alfonso García", "Curso 2025-2026", "CFGS Desarrollo de Aplicaciones Multiplataforma"],
      movil: await movilSuelto(captura("C2-pedir-cita"), 10.55, 1.46, 4.94, "movil-portada"),
      notas: "Buenos días. Soy Jhonnatan Ospina y presento Clandestino, mi trabajo de fin de grado del ciclo de DAM. Corto el pelo en casa y la hice para mi propio uso y para otros barberos en mi situación. Es una app Android de citas en la que solo entran los clientes que invitas. Detrás de la app hay una API REST escrita en Java y una base de datos MySQL. Sigo los apartados de la plantilla: introducción, objetivos, diseño, metodología, desarrollo y conclusiones.",
    },
    agradecimientos: {
      lineas: ["GRACIAS POR SU ATENCIÓN", "El código, la memoria y los anexos de instalación están en github.com/Jhonnatanospina/clandestino-tfg"],
      movil: await movilSuelto(captura("C1-mis-citas"), 10.28, 1.32, 4.58, "movil-agradecimientos"),
      notas: "Esto es todo. Gracias por la atención. El código, la memoria y los anexos de instalación están en el repositorio.",
    },
    secciones: [{ nombre: "Portada", desde: 1 }, ...secciones, { nombre: "Agradecimientos", desde: numero + 1 }],
    oculta: diapositivaOculta,
    ola: OLA,
  };
  fs.writeFileSync(TEXTOS, JSON.stringify(textos, null, 2), "utf8");

  execFileSync("powershell.exe", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", path.join(__dirname, "montar.ps1"),
    "-Plantilla", PLANTILLA, "-Contenido", CONTENIDO, "-Textos", TEXTOS, "-Salida", SALIDA, "-Pdf", SALIDA_PDF], { stdio: "inherit" });
  // Los ficheros de paso solo sirven para el montaje
  for (const f of [CONTENIDO, TEXTOS, OLA, textos.portada.movil.ruta, textos.agradecimientos.movil.ruta]) fs.rmSync(f);
  console.log("Escritos " + SALIDA + " y " + SALIDA_PDF);
}

construir().catch((e) => { console.error(e); process.exit(1); });
