// Genera docs/presentacion/clandestino-defensa.pptx con pptxgenjs.
// Las diapositivas siguen el orden de la memoria, que es lo que pide la guía del vídeo.
// Ejecutar desde esta carpeta: node generar.js

const path = require("path");
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const fa = require("react-icons/fa");
const { applyTheme } = require("./apply_theme.js");

const RAIZ = path.resolve(__dirname, "../../..");
const SALIDA = path.resolve(__dirname, "../clandestino-defensa.pptx");
const captura = (n) => path.join(RAIZ, "docs/capturas", n + ".png");
const mockup = (n) => path.join(RAIZ, "docs/mockups", n + ".png");
const diagrama = (n) => path.join(RAIZ, "docs/diagramas", n + ".png");

// Los colores son los de colors.xml de la app, así la presentación y la app se ven iguales.
const THEME = {
  name: "Clandestino",
  headFontFace: "Impact",
  bodyFontFace: "Calibri",
  colors: {
    dk1: "121212", lt1: "FFFFFF", dk2: "5A5A5E", lt2: "F2F2F4",
    accent1: "DD0426", accent2: "C40320", accent3: "8E8E93",
    accent4: "1D4ED8", accent5: "FCE6E9", accent6: "DADADF",
    hlink: "DD0426", folHlink: "C40320",
  },
};
// Tarjetas sobre fondo negro: no hay hueco para este gris en el tema, va en hexadecimal.
const TARJETA_OSCURA = "1E1E21";
const MARCO_MOVIL = "1E1E21";
const TITULAR = "+mj-lt"; // fuente de títulos del tema (Impact)

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13,333 × 7,5 pulgadas
pres.title = "Clandestino · Defensa del TFG";
pres.author = "[Nombre del alumno]";
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
const C = pres.SchemeColor;

// ---------- Layouts ----------

function marcoComun(oscuro) {
  return [
    { placeholder: { options: { name: "ceja", type: "body", x: 0.6, y: 0.42, w: 12.1, h: 0.35,
      fontSize: 13, bold: true, color: C.accent1, charSpacing: 3, margin: 0, valign: "top" }, text: "" } },
    { placeholder: { options: { name: "title", type: "title", x: 0.6, y: 0.78, w: 12.1, h: 0.85,
      fontSize: 36, color: oscuro ? C.background1 : C.text1, margin: 0, valign: "top", align: "left" }, text: "" } },
    { text: { text: "Clandestino · TFG DAM · Universae 2025-2026",
      options: { x: 0.6, y: 6.98, w: 6, h: 0.3, fontSize: 10, color: C.accent3, margin: 0 } } },
  ];
}
const numeroPagina = { x: 12.1, y: 6.98, w: 0.6, h: 0.3, fontSize: 10, color: C.accent3, align: "right", margin: 0 };

pres.defineSlideMaster({ title: "PORTADA", background: { color: C.text1 } });
pres.defineSlideMaster({ title: "CONTENIDO", background: { color: C.background1 },
  objects: marcoComun(false), slideNumber: numeroPagina });
pres.defineSlideMaster({ title: "OSCURO", background: { color: C.text1 },
  objects: marcoComun(true), slideNumber: numeroPagina });

// ---------- Ayudas ----------

async function icono(nombre, color, tam = 256) {
  const svg = ReactDOMServer.renderToStaticMarkup(
    React.createElement(fa[nombre], { color: "#" + color, size: tam }));
  const png = await sharp(Buffer.from(svg)).png().toBuffer();
  return "image/png;base64," + png.toString("base64");
}

// Icono blanco dentro de un círculo rojo: es el motivo que se repite en todas las diapositivas.
async function circulo(slide, nombre, x, y, d, id) {
  slide.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: C.accent1 }, line: { type: "none" }, objectName: id + "-circulo" });
  const m = d * 0.26;
  slide.addImage({ data: await icono(nombre, "FFFFFF"), x: x + m, y: y + m, w: d - 2 * m, h: d - 2 * m, objectName: id + "-icono" });
}

// Captura de pantalla dentro de un marco oscuro que hace de móvil.
function movil(slide, imagen, x, y, alto, proporcion, id, sombra = true) {
  const borde = 0.07;
  const ancho = alto * proporcion;
  const opciones = { x, y, w: ancho + 2 * borde, h: alto + 2 * borde, fill: { color: MARCO_MOVIL },
    line: { type: "none" }, rectRadius: 0.16, objectName: id + "-marco" };
  if (sombra) opciones.shadow = { type: "outer", color: "000000", opacity: 0.25, blur: 10, offset: 4, angle: 90 };
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, opciones);
  slide.addImage({ path: imagen, x: x + borde, y: y + borde, w: ancho, h: alto, objectName: id + "-imagen" });
  return ancho + 2 * borde;
}

function tarjeta(slide, x, y, w, h, id, color = C.background2) {
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, fill: { color }, line: { type: "none" },
    rectRadius: 0.12, objectName: id });
}

function texto(slide, contenido, opciones) {
  slide.addText(contenido, Object.assign({ isTextBox: true, margin: 0, valign: "top", color: C.text1 }, opciones));
}

function vineta(lineas, opciones = {}) {
  return lineas.map((l, i) => ({ text: l, options: Object.assign({ bullet: { indent: 16 }, paraSpaceAfter: 8,
    breakLine: i < lineas.length - 1 }, opciones) }));
}

function contenido(seccion, layout, ceja, titulo) {
  const s = pres.addSlide({ masterName: layout, sectionTitle: seccion });
  s.addText(ceja, { placeholder: "ceja" });
  s.addText(titulo.toUpperCase(), { placeholder: "title" });
  return s;
}

const PROP_CAPTURA = 1080 / 2400;
const PROP_MOCKUP = 776 / 1536;

// ---------- Diapositivas ----------

async function construir() {
  // 1. Portada
  pres.addSection({ title: "Portada" });
  let s = pres.addSlide({ masterName: "PORTADA", sectionTitle: "Portada" });
  texto(s, "TRABAJO DE FIN DE GRADO · DAM", { x: 0.7, y: 1.5, w: 7, h: 0.4, fontSize: 14, bold: true, color: C.accent1, charSpacing: 3 });
  texto(s, "CLANDESTINO", { x: 0.7, y: 1.95, w: 7.2, h: 1.45, fontSize: 80, fontFace: TITULAR, color: C.background1 });
  texto(s, "App Android para gestionar las citas de una barbería que trabaja solo por invitación",
    { x: 0.7, y: 3.5, w: 6.6, h: 1.0, fontSize: 22, color: C.accent6 });
  texto(s, "Java 21 · API REST propia · MySQL 9.7 · Android 8 a 16", { x: 0.7, y: 4.6, w: 6.6, h: 0.4, fontSize: 16, color: C.accent3 });
  texto(s, "[Nombre del alumno]", { x: 0.7, y: 5.7, w: 6.6, h: 0.4, fontSize: 18, bold: true, color: C.background1 });
  texto(s, "Tutor: [nombre del tutor] · Universae · Curso 2025-2026", { x: 0.7, y: 6.12, w: 6.6, h: 0.35, fontSize: 14, color: C.accent3 });
  movil(s, captura("C2-pedir-cita"), 8.0, 1.4, 4.8, PROP_CAPTURA, "portada-c2", false);
  movil(s, captura("D2-agenda"), 10.5, 0.9, 4.8, PROP_CAPTURA, "portada-d2", false);
  s.addNotes("Buenos días. Soy [nombre] y presento Clandestino, mi trabajo de fin de grado del ciclo de DAM. Es una app Android para gestionar las citas de una barbería real que solo atiende con cita y a clientes invitados. Detrás de la app hay una API REST escrita en Java y una base de datos MySQL.");

  // 2. Recorrido
  pres.addSection({ title: "Recorrido" });
  s = contenido("Recorrido", "CONTENIDO", "RECORRIDO", "Mismo orden que la memoria");
  const apartados = [
    ["3", "Introducción", "Problema y mercado"], ["4", "Objetivos", "General y específicos"],
    ["5", "Contenidos", "Mockups, datos y casos de uso"], ["6", "Metodología", "Ciclo de vida y planificación"],
    ["7", "Desarrollo", "Arquitectura y seguridad"], ["7.5", "Concurrencia", "El cupo diario"],
    ["7.8", "Pruebas y demo", "Automáticas, manuales y en vivo"], ["8", "Conclusiones", "Límites y trabajo futuro"],
  ];
  const anchoApartado = (12.13 - 3 * 0.3) / 4;
  apartados.forEach(([n, nombre, detalle], i) => {
    const x = 0.6 + (i % 4) * (anchoApartado + 0.3), y = i < 4 ? 1.95 : 4.4;
    tarjeta(s, x, y, anchoApartado, 2.15, "apartado-" + n);
    texto(s, n, { x: x + 0.3, y: y + 0.25, w: anchoApartado - 0.6, h: 0.75, fontSize: 40, fontFace: TITULAR, color: C.accent1 });
    texto(s, nombre, { x: x + 0.3, y: y + 1.05, w: anchoApartado - 0.6, h: 0.45, fontSize: 20, bold: true });
    texto(s, detalle, { x: x + 0.3, y: y + 1.5, w: anchoApartado - 0.6, h: 0.5, fontSize: 14, color: C.text2 });
  });
  s.addNotes("La presentación sigue el mismo orden que la memoria. Empiezo por el problema y el mercado, sigo con los objetivos y el diseño, la metodología y el desarrollo, y me paro en lo más difícil, que es el cupo diario con peticiones simultáneas. Termino con las pruebas, una demostración y las conclusiones.");

  // 3. Introducción
  pres.addSection({ title: "Introducción" });
  s = contenido("Introducción", "CONTENIDO", "3 · INTRODUCCIÓN", "Una barbería de un solo barbero, con cita");
  const rasgos = [
    ["FaCut", "Un solo barbero", "Trabaja siempre con cita y ya tiene su clientela."],
    ["FaTicketAlt", "Clientes por invitación", "Solo entra quien recibe un código y el barbero aprueba."],
    ["FaCalendarCheck", "Cupo diario", "Cada día admite un número máximo de citas."],
  ];
  for (const [i, [ic, cab, desc]] of rasgos.entries()) {
    const y = 1.95 + i * 1.4;
    await circulo(s, ic, 0.6, y, 0.75, "rasgo-" + i);
    texto(s, cab, { x: 1.6, y: y + 0.02, w: 5.2, h: 0.4, fontSize: 20, bold: true });
    texto(s, desc, { x: 1.6, y: y + 0.45, w: 5.2, h: 0.6, fontSize: 16, color: C.text2 });
  }
  texto(s, "Hoy la barbería usa una aplicación web propia. El proyecto rehace su núcleo como app Android nativa.",
    { x: 0.6, y: 6.15, w: 6.2, h: 0.6, fontSize: 14, italic: true, color: C.text2 });
  const datos = [
    ["96,3 %", "de las personas de 16 a 74 años usó Internet en los últimos tres meses", "INE, 2025"],
    ["94,6 %", "de los internautas usa WhatsApp de forma habitual", "CNMC, 2026"],
    ["60,2 %", "de las empresas de servicios personales no tiene asalariados", "INE, DIRCE"],
  ];
  datos.forEach(([cifra, que, fuente], i) => {
    const y = 1.95 + i * 1.55;
    tarjeta(s, 7.4, y, 5.33, 1.35, "dato-" + i);
    texto(s, cifra, { x: 7.7, y: y + 0.27, w: 2.0, h: 0.8, fontSize: 40, fontFace: TITULAR, color: C.accent1, valign: "middle" });
    texto(s, que, { x: 9.75, y: y + 0.18, w: 2.8, h: 0.75, fontSize: 14, valign: "middle" });
    texto(s, fuente, { x: 9.75, y: y + 0.95, w: 2.8, h: 0.25, fontSize: 11, color: C.accent3 });
  });
  s.addNotes("Clandestino es una barbería de una sola persona. Trabaja siempre con cita, solo acepta clientes que vienen invitados y cada día tiene un máximo de citas. Los datos públicos dicen que casi todo el mundo usa el móvil a diario y que en este sector predomina el negocio sin empleados, así que tiene sentido una app pensada primero para el teléfono.");

  // 4. Mercado
  s = contenido("Introducción", "CONTENIDO", "3.4 · ANÁLISIS DE MERCADO", "Siete apps y ninguna cumple las tres reglas");
  const cab = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text1 } } });
  const filas = [
    ["Booksy", "34,99 € + IVA al mes y comisiones", "Escaparate público, sin acceso por invitación"],
    ["Fresha", "19,95 € al mes y tarifa por cliente nuevo", "Pensada para captar clientes"],
    ["Treatwell", "25 % de la primera reserva de cada cliente nuevo", "Su negocio es traer clientes nuevos"],
    ["AgendaPro", "Desde 10 € al mes", "Personal, inventario y caja"],
    ["Square", "Gratis, con comisión por cobro", "Vive de su pasarela de pago"],
    ["Setmore", "Gratis hasta 4 usuarios, Pro a 5 $", "Generalista, sin aprobación de clientes"],
    ["Calendly", "Gratis con un evento, 10 $ por usuario", "Sin cupo diario ni aprobaciones"],
  ].map((f, i) => f.map((t, j) => ({ text: t, options: { bold: j === 0, fill: { color: i % 2 ? C.background1 : C.background2 } } })));
  s.addTable([[cab("App"), cab("Precio"), cab("Por qué no encaja")], ...filas], {
    x: 0.6, y: 1.85, w: 8.5, colW: [1.45, 3.35, 3.7], fontSize: 14, color: C.text1, fontFace: "+mn-lt",
    border: { type: "none" }, margin: [5, 8, 5, 8], valign: "middle", objectName: "tabla-mercado" });
  texto(s, "Fuente: webs oficiales de cada aplicación, consultadas el 30-09-2026.", { x: 0.6, y: 6.5, w: 8.5, h: 0.3, fontSize: 11, color: C.accent3 });
  tarjeta(s, 9.5, 1.85, 3.23, 4.9, "tarjeta-clandestino", C.text1);
  texto(s, "CLANDESTINO", { x: 9.85, y: 2.15, w: 2.6, h: 0.55, fontSize: 28, fontFace: TITULAR, color: C.background1 });
  const reglas = ["Acceso solo por invitación y aprobación", "Cupo diario configurable", "Sin cuotas ni comisiones"];
  for (const [i, r] of reglas.entries()) {
    const y = 2.95 + i * 0.85;
    s.addImage({ data: await icono("FaCheck", THEME.colors.accent1), x: 9.85, y: y + 0.04, w: 0.28, h: 0.28, objectName: "regla-" + i });
    texto(s, r, { x: 10.3, y, w: 2.2, h: 0.75, fontSize: 15, color: C.background1, valign: "top" });
  }
  texto(s, "A cambio, no sirve para salones con varios empleados ni cobra desde la app.",
    { x: 9.85, y: 5.65, w: 2.6, h: 0.9, fontSize: 13, color: C.accent6 });
  s.addNotes("Revisé siete aplicaciones de reservas en sus webs oficiales. Las especializadas, como Booksy, Fresha o Treatwell, giran en torno a un escaparate público que trae clientes nuevos a cambio de cuotas o comisiones. Las generalistas, como Setmore o Calendly, no entienden de cupos ni de aprobar clientes. Ninguna junta las tres reglas que necesita este negocio, y ese es el hueco del proyecto.");

  // 5. Objetivos
  pres.addSection({ title: "Objetivos" });
  s = contenido("Objetivos", "CONTENIDO", "4 · OBJETIVOS", "Objetivos del proyecto");
  tarjeta(s, 0.6, 1.85, 12.13, 1.2, "objetivo-general", C.accent5);
  texto(s, "OBJETIVO GENERAL", { x: 0.9, y: 2.0, w: 11.5, h: 0.3, fontSize: 12, bold: true, color: C.accent2, charSpacing: 2 });
  texto(s, "Desarrollar una app Android, con su API y su base de datos, para que una barbería de un solo barbero gestione sus citas con acceso por invitación, cupo diario y sin reservas duplicadas.",
    { x: 0.9, y: 2.32, w: 11.5, h: 0.65, fontSize: 17 });
  const objetivos = [
    ["FaDatabase", "Base de datos", "MySQL que impide por sí misma dos citas a la misma hora"],
    ["FaServer", "API REST", "Java sin frameworks, con contraseñas protegidas, tokens y roles"],
    ["FaUsers", "Cupo seguro", "Se respeta aunque varios clientes pidan a la vez, y una prueba con hilos lo demuestra"],
    ["FaMobileAlt", "App Android", "MVVM, siete pantallas y la red fuera del hilo principal"],
    ["FaCheckCircle", "Pruebas", "Unitarias, de integración y un recorrido completo en el emulador"],
  ];
  const anchoObj = (12.13 - 4 * 0.25) / 5;
  for (const [i, [ic, nombre, desc]] of objetivos.entries()) {
    const x = 0.6 + i * (anchoObj + 0.25);
    tarjeta(s, x, 3.3, anchoObj, 3.35, "objetivo-" + (i + 1));
    await circulo(s, ic, x + 0.3, 3.6, 0.7, "objetivo-" + (i + 1));
    texto(s, (i + 1) + ". " + nombre, { x: x + 0.3, y: 4.5, w: anchoObj - 0.5, h: 0.4, fontSize: 17, bold: true });
    texto(s, desc, { x: x + 0.3, y: 4.95, w: anchoObj - 0.5, h: 1.5, fontSize: 14, color: C.text2 });
  }
  s.addNotes("El objetivo general es que la barbería gestione sus citas desde el móvil con sus tres reglas y sin reservas duplicadas. Lo concreté en cinco objetivos: una base de datos que bloquee por sí sola las dobles reservas, una API en Java sin frameworks, un cupo que aguante peticiones a la vez, una app Android con MVVM y pruebas que demuestren todo lo anterior.");

  // 6. Mockups
  pres.addSection({ title: "Contenidos" });
  s = contenido("Contenidos", "CONTENIDO", "5.1 · MOCKUPS", "Del mockup a la pantalla real");
  movil(s, mockup("C2-pedir-cita"), 0.9, 1.85, 4.3, PROP_MOCKUP, "mockup-c2");
  s.addImage({ data: await icono("FaArrowRight", THEME.colors.accent1), x: 3.45, y: 3.85, w: 0.5, h: 0.5, objectName: "flecha-mockup" });
  movil(s, captura("C2-pedir-cita"), 4.2, 1.85, 4.3, PROP_CAPTURA, "captura-c2");
  texto(s, "Mockup en draw.io", { x: 0.9, y: 6.35, w: 2.31, h: 0.35, fontSize: 13, color: C.text2, align: "center" });
  texto(s, "App en el emulador", { x: 4.2, y: 6.35, w: 2.08, h: 0.35, fontSize: 13, color: C.text2, align: "center" });
  texto(s, "11", { x: 7.0, y: 1.85, w: 1.3, h: 1.0, fontSize: 60, fontFace: TITULAR, color: C.accent1 });
  texto(s, "mockups dibujados antes de escribir ningún layout, uno por pantalla y variante", { x: 8.3, y: 2.0, w: 4.4, h: 0.8, fontSize: 16, valign: "middle" });
  texto(s, vineta([
    "Identidad de la barbería: rojo de la marca para la acción principal y Anton en los títulos",
    "Componentes de Material 3, con zonas táctiles de 48 dp y tema claro y oscuro",
    "Al construirla, el calendario se quedó en las dos semanas reservables, sin flechas",
    "La agenda del barbero se quedó en vista semanal",
  ]), { x: 7.0, y: 3.2, w: 5.7, h: 3.3, fontSize: 16 });
  s.addNotes("Antes de programar dibujé once mockups en draw.io, uno por pantalla. A la izquierda está el de «Pedir cita» y a la derecha cómo quedó en la app. Se parecen mucho. El cambio principal es que el calendario solo enseña las dos semanas que se pueden reservar, así que sobraban las flechas para cambiar de mes.");

  // 7. Modelo de datos
  s = contenido("Contenidos", "CONTENIDO", "5.2 · MODELO DE DATOS", "Cinco tablas contra la doble reserva");
  const altoER = 4.9;
  s.addImage({ path: diagrama("02-er"), x: 0.6, y: 1.8, w: altoER * 1440 / 2010, h: altoER, objectName: "diagrama-er" });
  ["usuarios", "invitaciones", "horarios_semanales", "excepciones_dia", "citas"].forEach((t, i) => {
    const anchos = [1.25, 1.5, 2.05, 1.8, 0.85];
    const x = 4.6 + anchos.slice(0, i).reduce((a, b) => a + b + 0.15, 0);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.85, w: anchos[i], h: 0.42, fill: { color: t === "citas" ? C.accent1 : C.background2 },
      line: { type: "none" }, rectRadius: 0.21, objectName: "chip-" + t });
    texto(s, t, { x, y: 1.85, w: anchos[i], h: 0.42, fontSize: 13, fontFace: "Courier New", bold: true, align: "center", valign: "middle",
      color: t === "citas" ? C.background1 : C.text1 });
  });
  tarjeta(s, 4.6, 2.6, 8.13, 1.55, "codigo-hora-ocupada", C.text1);
  texto(s, [
    { text: "hora_ocupada TIME GENERATED ALWAYS AS (", options: { breakLine: true } },
    { text: "  IF(estado IN ('pendiente','confirmada'),", options: { breakLine: true } },
    { text: "     COALESCE(hora_final, hora_pedida), NULL))", options: { breakLine: true } },
    { text: "UNIQUE KEY uq_citas_hueco_activo (fecha, hora_ocupada)", options: { color: C.accent1 } },
  ], { x: 4.85, y: 2.78, w: 7.7, h: 1.25, fontSize: 14, fontFace: "Courier New", color: C.background1 });
  texto(s, vineta([
    "Una cita cancelada o rechazada deja la columna a NULL, y el índice único admite varios NULL.",
    "Una segunda cita activa a la misma hora choca con el índice (error 1062) y la API responde 409 hueco_ocupado.",
    "El precio se copia en la cita al marcarla como hecha, así un cambio de tarifa no altera el historial.",
  ]), { x: 4.6, y: 4.45, w: 8.13, h: 2.3, fontSize: 16 });
  s.addNotes("La base de datos tiene cinco tablas. La pieza más interesante está en «citas». Una columna generada, hora_ocupada, vale la hora de la cita solo mientras está pendiente o confirmada, y si no vale nulo. Con un índice único sobre la fecha y esa columna, MySQL rechaza por sí mismo una segunda cita activa a la misma hora, sin depender de la API.");

  // 8. Casos de uso
  s = contenido("Contenidos", "CONTENIDO", "5.3 · CASOS DE USO", "Diez casos de uso, todos programados");
  const altoCU = 4.9;
  s.addImage({ path: diagrama("03-casos-de-uso"), x: 0.6, y: 1.8, w: altoCU * 1674 / 1904, h: altoCU, objectName: "diagrama-casos" });
  const actores = [
    ["FaUserPlus", "Visitante", "UC01 Registrarse con código"],
    ["FaUser", "Cliente", "UC03 Consultar disponibilidad · UC04 Solicitar cita · UC05 Gestionar mis citas"],
    ["FaCut", "Barbero", "UC06 Generar invitación · UC07 Aprobar o bloquear · UC08 Solicitudes · UC09 Agenda · UC10 Horarios"],
  ];
  for (const [i, [ic, actor, casos]] of actores.entries()) {
    const y = 1.85 + i * 1.3;
    tarjeta(s, 5.5, y, 7.23, 1.1, "actor-" + actor);
    await circulo(s, ic, 5.75, y + 0.2, 0.7, "actor-" + actor);
    texto(s, actor, { x: 6.7, y: y + 0.14, w: 5.8, h: 0.35, fontSize: 17, bold: true });
    texto(s, casos, { x: 6.7, y: y + 0.48, w: 5.8, h: 0.55, fontSize: 13, color: C.text2 });
  }
  texto(s, "UC02 Iniciar sesión es común a cliente y barbero.", { x: 5.5, y: 5.8, w: 7.23, h: 0.35, fontSize: 14, italic: true, color: C.text2 });
  texto(s, [
    { text: "30", options: { fontFace: TITULAR, fontSize: 24, color: C.accent1 } }, { text: " requisitos funcionales    ", options: { fontSize: 14 } },
    { text: "53", options: { fontFace: TITULAR, fontSize: 24, color: C.accent1 } }, { text: " no funcionales", options: { fontSize: 14 } },
  ], { x: 5.5, y: 6.2, w: 7.23, h: 0.5, valign: "middle" });
  s.addNotes("Hay tres actores y diez casos de uso. El visitante solo puede registrarse con un código. El cliente consulta los días libres, pide cita y gestiona las suyas, y el barbero lleva las invitaciones, los clientes, las solicitudes, la agenda y los horarios. De ahí salieron treinta requisitos funcionales, y la memoria tiene una tabla que relaciona cada caso con su pantalla y sus rutas.");

  // 9. Metodología
  pres.addSection({ title: "Metodología" });
  s = contenido("Metodología", "CONTENIDO", "6 · METODOLOGÍA", "Ciclo incremental con prácticas de Scrum");
  const etapas = [["Inicio", "30 sep – 1 oct"], ["Diseño", "1 – 9 oct"], ["Desarrollo", "9 – 15 oct"], ["Cierre", "16 – 31 oct"]];
  const anchoEtapa = 2.6, hueco = (12.13 - 4 * anchoEtapa) / 3;
  for (const [i, [et, fechas]] of etapas.entries()) {
    const x = 0.6 + i * (anchoEtapa + hueco);
    tarjeta(s, x, 1.85, anchoEtapa, 0.95, "etapa-" + et, i === 3 ? C.accent5 : C.background2);
    texto(s, et, { x: x + 0.25, y: 1.95, w: anchoEtapa - 0.5, h: 0.4, fontSize: 18, bold: true });
    texto(s, fechas, { x: x + 0.25, y: 2.35, w: anchoEtapa - 0.5, h: 0.35, fontSize: 14, color: C.text2 });
    if (i < 3) s.addImage({ data: await icono("FaChevronRight", THEME.colors.accent3), x: x + anchoEtapa + hueco / 2 - 0.15, y: 2.17, w: 0.3, h: 0.3, objectName: "flecha-etapa-" + i });
  }
  const anchoG = 10.6;
  s.addImage({ path: diagrama("08-gantt"), x: (13.333 - anchoG) / 2, y: 3.05, w: anchoG, h: anchoG * 818 / 2373, objectName: "gantt" });
  s.addNotes("Seguí un ciclo incremental. Primero diseñé todo el sistema y después lo construí por piezas que se podían probar solas: la base de datos, la API y por último la app. De Scrum tomé la lista de trabajo por prioridades, P1, P2 y P3, y los ciclos cortos con revisión al final. El Gantt muestra las cuatro etapas. Lo macizo ya está hecho y lo discontinuo es el cierre.");

  // 10. Arquitectura
  pres.addSection({ title: "Desarrollo" });
  s = contenido("Desarrollo", "CONTENIDO", "7.2 · ARQUITECTURA", "La app nunca toca la base de datos");
  const piezas = [
    ["FaMobileAlt", "APP ANDROID", ["Java 17 y vistas XML", "Material 3", "MVVM con LiveData", "Red en un hilo aparte", "Sesión cifrada con el Keystore"]],
    ["FaServer", "API REST", ["Java 21 y HttpServer del JDK", "Rutas y JSON escritos a mano", "23 rutas y 8 hilos", "Capas http, seguridad, servicio y repositorio"]],
    ["FaDatabase", "MYSQL 9.7", ["5 tablas", "Índice único contra dobles reservas", "Usuario de la API sin permisos sobre las tablas", "Escucha solo en 127.0.0.1"]],
  ];
  const anchoPieza = 3.4, xs = [0.6, 4.97, 9.33];
  for (const [i, [ic, nombre, lista]] of piezas.entries()) {
    tarjeta(s, xs[i], 1.95, anchoPieza, 4.0, "pieza-" + i);
    await circulo(s, ic, xs[i] + 0.3, 2.25, 0.75, "pieza-" + i);
    texto(s, nombre, { x: xs[i] + 1.2, y: 2.38, w: anchoPieza - 1.4, h: 0.5, fontSize: 24, fontFace: TITULAR, valign: "middle" });
    texto(s, vineta(lista), { x: xs[i] + 0.3, y: 3.3, w: anchoPieza - 0.55, h: 2.5, fontSize: 15 });
  }
  [["HTTP + JSON", "token JWT"], ["JDBC", "PreparedStatement"]].forEach(([a, b], i) => {
    const x = xs[i] + anchoPieza + 0.1, w = xs[i + 1] - x - 0.1;
    s.addShape(pres.shapes.LINE, { x, y: 3.95, w, h: 0, line: { color: C.accent1, width: 2.5, endArrowType: "triangle", beginArrowType: "triangle" }, objectName: "enlace-" + i });
    // Las etiquetas invaden un poco el margen de las tarjetas para que «PreparedStatement» quepa en una línea
    texto(s, a, { x: x - 0.35, y: 3.38, w: w + 0.7, h: 0.3, fontSize: 11, bold: true, align: "center", color: C.text2 });
    texto(s, b, { x: x - 0.35, y: 4.07, w: w + 0.7, h: 0.3, fontSize: 10, align: "center", color: C.text2 });
  });
  texto(s, "Solo la API habla con MySQL, así las credenciales de la base nunca viajan dentro del APK.",
    { x: 0.6, y: 6.2, w: 12.13, h: 0.4, fontSize: 15, italic: true, color: C.text2 });
  s.addNotes("El sistema tiene tres piezas. La app no se conecta nunca a MySQL. Si lo hiciera, las credenciales irían dentro del APK y cualquiera podría sacarlas. La app habla por HTTP con la API, enviando JSON y un token, y la API es la única que entra en la base de datos. La API no usa ningún framework. Va sobre el servidor HTTP que trae el JDK, y las rutas, el JSON y las transacciones están escritos a mano para poder explicarlos.");

  // 11. Seguridad
  s = contenido("Desarrollo", "CONTENIDO", "7.4 · SEGURIDAD", "Seguridad revisada con OWASP MASVS");
  const medidas = [
    ["FaKey", "Contraseñas", "PBKDF2-HMAC-SHA256 con 600.000 iteraciones y una sal de 16 bytes"],
    ["FaIdBadge", "Token de sesión", "JWT HS256 firmado a mano con una clave de 32 bytes o más, válido 7 días"],
    ["FaLock", "Sesión en el móvil", "Cifrada con AES-GCM y una clave que vive en el Android Keystore"],
    ["FaUserShield", "Intentos de acceso", "Tras 5 fallos en 15 minutos la API responde 429"],
    ["FaDatabase", "Consultas", "Siempre con PreparedStatement, nunca SQL concatenado"],
    ["FaNetworkWired", "Red y copias", "HTTP en claro solo hacia el emulador en debug, y copias de seguridad de la app desactivadas"],
  ];
  const anchoMed = (12.13 - 2 * 0.3) / 3;
  for (const [i, [ic, nombre, desc]] of medidas.entries()) {
    const x = 0.6 + (i % 3) * (anchoMed + 0.3), y = i < 3 ? 1.9 : 4.35;
    tarjeta(s, x, y, anchoMed, 2.15, "medida-" + i);
    await circulo(s, ic, x + 0.3, y + 0.3, 0.65, "medida-" + i);
    texto(s, nombre, { x: x + 1.15, y: y + 0.4, w: anchoMed - 1.4, h: 0.45, fontSize: 18, bold: true, valign: "middle" });
    texto(s, desc, { x: x + 0.3, y: y + 1.12, w: anchoMed - 0.6, h: 0.9, fontSize: 14, color: C.text2 });
  }
  s.addNotes("La seguridad la revisé con las categorías del estándar OWASP MASVS. Las contraseñas se guardan con PBKDF2 y seiscientas mil iteraciones. La sesión es un JWT que firmo yo con HMAC, y en el móvil se guarda cifrado con una clave del Keystore de Android. Además hay un límite de intentos de acceso y todas las consultas van con PreparedStatement, así que no cabe la inyección SQL.");

  // 12. Concurrencia
  pres.addSection({ title: "Concurrencia" });
  s = contenido("Concurrencia", "OSCURO", "7.5 · CONCURRENCIA", "Dos clientes y una sola plaza libre");
  const pasos = ["Cada petición cuenta las citas del día y ve 0 de 1", "Las dos creen que queda sitio e insertan su cita", "El día acaba con dos citas cuando el cupo era una"];
  pasos.forEach((p, i) => {
    const y = 1.95 + i * 0.85;
    s.addShape(pres.shapes.OVAL, { x: 0.6, y, w: 0.55, h: 0.55, fill: { color: C.accent1 }, line: { type: "none" }, objectName: "paso-" + i });
    texto(s, String(i + 1), { x: 0.6, y, w: 0.55, h: 0.55, fontSize: 18, bold: true, align: "center", valign: "middle", color: C.background1 });
    texto(s, p, { x: 1.4, y: y + 0.02, w: 4.8, h: 0.6, fontSize: 16, color: C.background1, valign: "middle" });
  });
  tarjeta(s, 0.6, 4.6, 5.6, 2.1, "solucion", TARJETA_OSCURA);
  texto(s, "LA SOLUCIÓN", { x: 0.9, y: 4.8, w: 5.0, h: 0.35, fontSize: 13, bold: true, color: C.accent1, charSpacing: 2 });
  texto(s, "Un cerrojo por fecha con synchronized. Contar e insertar van dentro de la misma transacción, y el índice único queda como última defensa.",
    { x: 0.9, y: 5.2, w: 5.0, h: 1.35, fontSize: 15, color: C.background1 });
  s.addChart(pres.charts.BAR, [{ name: "Citas creadas", labels: ["Cupo 1, sin cerrojo", "Cupo 1, con cerrojo", "Cupo 5, misma hora, sin cerrojo"], values: [5, 1, 1] }], {
    x: 6.7, y: 1.85, w: 6.0, h: 4.45, barDir: "col", barGapWidthPct: 60,
    chartColors: [THEME.colors.accent1, THEME.colors.accent3, THEME.colors.accent3],
    showTitle: true, title: "Citas creadas con 5 peticiones a la vez", titleColor: "FFFFFF", titleFontSize: 14, titleFontFace: "+mn-lt",
    showValue: true, dataLabelPosition: "outEnd", dataLabelColor: "FFFFFF", dataLabelFontSize: 16, dataLabelFontFace: "+mn-lt", dataLabelFontBold: true,
    catAxisLabelColor: "DADADF", catAxisLabelFontSize: 12, catAxisLabelFontFace: "+mn-lt", catAxisLineShow: false,
    valAxisHidden: true, valAxisMinVal: 0, valAxisMaxVal: 6, valGridLine: { style: "none" }, catGridLine: { style: "none" },
    showLegend: false, objectName: "grafico-concurrencia",
  });
  texto(s, "Prueba automática: 5 hilos que salen a la vez con un CountDownLatch y un repositorio que tarda 300 ms en contar.",
    { x: 6.7, y: 6.35, w: 6.0, h: 0.45, fontSize: 12, color: C.accent3 });
  s.addNotes("Esta es la parte más difícil del proyecto. Si dos clientes piden a la vez la última plaza del día, cada petición cuenta las citas, ve que queda sitio e inserta. El índice único no lo evita porque las horas son distintas, y ningún índice puede contar las citas de un día. Lo resolví con un cerrojo por fecha y una transacción que cuenta e inserta junta. La prueba lo demuestra: sin cerrojo entran cinco citas en un día con cupo uno, y con cerrojo solo una.");

  // 13. Pruebas
  pres.addSection({ title: "Pruebas y demo" });
  s = contenido("Pruebas y demo", "CONTENIDO", "7.8 · PRUEBAS", "134 pruebas automáticas en verde");
  const cifras = [
    ["88", "pruebas de la API", "11 clases de JUnit 6 con una base de datos aparte y un reloj que se puede mover"],
    ["25", "pruebas de la app", "Modelos, calendario, formatos y validaciones del registro"],
    ["21", "pruebas del esquema", "Restricciones, índice único y datos de ejemplo en MySQL"],
  ];
  const anchoCifra = (12.13 - 2 * 0.3) / 3;
  cifras.forEach(([n, que, det], i) => {
    const x = 0.6 + i * (anchoCifra + 0.3);
    tarjeta(s, x, 1.9, anchoCifra, 2.55, "cifra-" + n);
    texto(s, n, { x: x + 0.35, y: 2.05, w: 2.0, h: 1.1, fontSize: 66, fontFace: TITULAR, color: C.accent1 });
    texto(s, que, { x: x + 0.35, y: 3.15, w: anchoCifra - 0.7, h: 0.4, fontSize: 18, bold: true });
    texto(s, det, { x: x + 0.35, y: 3.58, w: anchoCifra - 0.7, h: 0.75, fontSize: 14, color: C.text2 });
  });
  const manuales = [
    ["FaMobileAlt", "Recorrido en el emulador", "7 casos, del registro con código a la cita confirmada en la agenda, todos correctos"],
    ["FaTerminal", "Comprobaciones con curl", "8 respuestas de la API revisadas a mano: 200, 401, 404, 405 y 400 con su código de error"],
  ];
  const anchoMan = (12.13 - 0.3) / 2;
  for (const [i, [ic, nombre, desc]] of manuales.entries()) {
    const x = 0.6 + i * (anchoMan + 0.3);
    tarjeta(s, x, 4.75, anchoMan, 1.9, "manual-" + i);
    await circulo(s, ic, x + 0.3, 5.0, 0.7, "manual-" + i);
    texto(s, nombre, { x: x + 1.25, y: 5.0, w: anchoMan - 1.55, h: 0.4, fontSize: 18, bold: true });
    texto(s, desc, { x: x + 1.25, y: 5.45, w: anchoMan - 1.55, h: 0.9, fontSize: 14, color: C.text2 });
  }
  s.addNotes("En total hay ciento treinta y cuatro pruebas automáticas y todas pasan. Las de la API van contra una base de datos aparte y con un reloj que puedo mover, para comprobar reglas como que no se puede pedir una hora pasada. Además recorrí la app entera en el emulador siguiendo siete casos de prueba y comprobé a mano las respuestas de error de la API.");

  // 14. Demostración
  s = contenido("Pruebas y demo", "CONTENIDO", "DEMOSTRACIÓN", "La app funcionando en el emulador");
  const demo = [["A1-inicio-sesion", "Entrar"], ["C2-pedir-cita", "Pedir cita"], ["D1-solicitudes", "Solicitudes"], ["D2-agenda", "Agenda"], ["D3-horarios", "Horarios"]];
  const altoDemo = 4.2, anchoDemo = altoDemo * PROP_CAPTURA + 0.14, huecoDemo = (12.13 - 5 * anchoDemo) / 4;
  demo.forEach(([img, nombre], i) => {
    const x = 0.6 + i * (anchoDemo + huecoDemo);
    movil(s, captura(img), x, 1.9, altoDemo, PROP_CAPTURA, "demo-" + img);
    texto(s, (i + 1) + " · " + nombre, { x, y: 6.32, w: anchoDemo, h: 0.35, fontSize: 14, bold: true, align: "center" });
  });
  s.addNotes("Ahora lo enseño funcionando en el emulador, con la API y MySQL en marcha en el ordenador. [Demo, unos 90 segundos] Entro como cliente y pido una cita para un día libre. Cambio al barbero, veo la solicitud y la confirmo moviéndola media hora. La cita aparece en la agenda de esa semana. Por último enseño la pantalla de horarios, donde se fijan el tramo y el cupo de cada día. Si la demo fallara, tengo grabado un vídeo corto de respaldo.");

  // 15. Conclusiones
  pres.addSection({ title: "Conclusiones" });
  s = contenido("Conclusiones", "CONTENIDO", "8 · CONCLUSIONES", "Objetivos cumplidos y lo que falta");
  const anchoCol = (12.13 - 2 * 0.3) / 3;
  tarjeta(s, 0.6, 1.9, anchoCol, 4.8, "col-cumplido", C.text1);
  texto(s, "5 / 5", { x: 0.95, y: 2.1, w: 3.2, h: 1.15, fontSize: 64, fontFace: TITULAR, color: C.accent1 });
  texto(s, "objetivos cumplidos", { x: 0.95, y: 3.25, w: 3.2, h: 0.45, fontSize: 20, bold: true, color: C.background1 });
  texto(s, vineta(["Prototipo P1 completo", "Los 30 requisitos funcionales programados", "Recorrido completo documentado con capturas"]),
    { x: 0.95, y: 3.95, w: 3.2, h: 2.5, fontSize: 15, color: C.accent6 });
  const columnas = [
    ["FaExclamationTriangle", "Limitaciones", ["Sin HTTPS, solo apto para uso local", "El cerrojo vive en la memoria de una única API", "Las solicitudes pendientes no caducan solas", "Sin pruebas de interfaz ni copia automática"]],
    ["FaRocket", "Trabajo futuro", ["Funciones P2: cita creada por el barbero y caducidad de solicitudes", "Avisos al cliente", "Resumen mensual de ingresos", "API publicada con HTTPS"]],
  ];
  for (const [i, [ic, nombre, lista]] of columnas.entries()) {
    const x = 0.6 + (i + 1) * (anchoCol + 0.3);
    tarjeta(s, x, 1.9, anchoCol, 4.8, "col-" + nombre);
    await circulo(s, ic, x + 0.35, 2.2, 0.7, "col-" + nombre);
    texto(s, nombre, { x: x + 1.25, y: 2.3, w: anchoCol - 1.5, h: 0.5, fontSize: 20, bold: true, valign: "middle" });
    texto(s, vineta(lista), { x: x + 0.35, y: 3.2, w: anchoCol - 0.65, h: 3.3, fontSize: 15 });
  }
  s.addNotes("Los cinco objetivos están cumplidos y el prototipo cubre todo el ciclo de la cita. Tiene límites claros: no usa HTTPS, así que solo vale en local, el cerrojo del cupo solo funciona con una copia de la API y las solicitudes no caducan solas. Como trabajo futuro quedan las funciones de la segunda fase, los avisos al cliente y publicar la API con HTTPS. [Una frase de valoración personal.]");

  // 16. Cierre
  pres.addSection({ title: "Cierre" });
  s = pres.addSlide({ masterName: "PORTADA", sectionTitle: "Cierre" });
  texto(s, "GRACIAS", { x: 0.7, y: 2.2, w: 7.5, h: 1.6, fontSize: 96, fontFace: TITULAR, color: C.background1 });
  texto(s, "Clandestino · [Nombre del alumno]", { x: 0.7, y: 4.0, w: 7.5, h: 0.45, fontSize: 20, bold: true, color: C.background1 });
  texto(s, "github.com/Jhonnatanospina/clandestino-tfg", { x: 0.7, y: 4.5, w: 7.5, h: 0.4, fontSize: 16, color: C.accent3 });
  movil(s, captura("C1-mis-citas"), 9.6, 1.0, 5.0, PROP_CAPTURA, "cierre-c1", false);
  s.addNotes("Esto es todo. Gracias por la atención. El código, la memoria y los anexos de instalación están en el repositorio.");

  await pres.writeFile({ fileName: SALIDA });
  await applyTheme(SALIDA, THEME);
  console.log("Escrito " + SALIDA);
}

construir().catch((e) => { console.error(e); process.exit(1); });
