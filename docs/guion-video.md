# Guion del vídeo de defensa

Borrador para reescribir con tus palabras. El texto de cada diapositiva es el mismo que llevan las notas del orador de `presentacion/clandestino-defensa.pptx`, así que si cambias algo aquí, cámbialo también allí. También puedes cambiarlo en `presentacion/_generador/generar.js` y volver a generar con `node generar.js`, que necesita PowerPoint instalado porque monta las diapositivas sobre la plantilla oficial de Universae.

La guía pide un vídeo de 10 minutos como máximo, sin cortes y con las diapositivas en el mismo orden que la memoria. El guion está pensado para unos 9 minutos y deja margen por si la demo se alarga.

## Tiempos

Calculados a unas 130 palabras por minuto, que es un ritmo tranquilo para leer en voz alta.

| # | Diapositiva | Tiempo | Acumulado |
|---|---|---|---|
| 1 | Portada | 0:30 | 0:30 |
| 2 | Introducción | 0:30 | 1:00 |
| 3 | Análisis de mercado | 0:30 | 1:30 |
| 4 | Objetivos | 0:30 | 2:00 |
| 5 | Mockups | 0:25 | 2:25 |
| 6 | Modelo de datos | 0:30 | 2:55 |
| 7 | Casos de uso | 0:30 | 3:25 |
| 8 | Metodología | 0:45 | 4:10 |
| 9 | Arquitectura | 0:40 | 4:50 |
| 10 | Seguridad | 0:30 | 5:20 |
| 11 | Concurrencia | 0:40 | 6:00 |
| 12 | Pruebas | 0:30 | 6:30 |
| 13 | Demostración | 1:50 | 8:20 |
| 14 | Vídeo de respaldo (oculta) | solo si falla la demo | |
| 15 | Conclusiones | 0:40 | 9:00 |
| 16 | Agradecimientos | 0:10 | 9:10 |

Si en el ensayo pasas de 9:30, recorta primero en el análisis de mercado y en los casos de uso, que son las que menos pierden.

## Antes de grabar

- Arranca MySQL con `scripts\mysql.cmd iniciar` y la API con `gradlew run` desde la carpeta `api`.
- Abre el emulador con la app instalada y comprueba que el login responde.
- Deja la app en la pantalla de entrada (A1) y la sesión cerrada.
- Elige de antemano el día libre que vas a pedir en la demo. Tiene que estar dentro de las dos semanas reservables y con cupo.
- Decide también para qué cliente, qué día y a qué hora vas a meter la cita a mano, y comprueba que esa hora no la tenga ya otra cita.
- Ten a mano las contraseñas de prueba del `.env`, pero que no salgan en pantalla mientras las escribes.
- El vídeo de respaldo está en `docs/presentacion/demo-respaldo.mp4` y también dentro de la presentación, en la diapositiva 14, que va oculta, así que al avanzar no aparece. Dura 66 segundos, no tiene sonido y la contraseña sale difuminada. Si algo falla en directo, teclea 14 e Intro para saltar a ella y cuenta encima los pasos que se ven. Se grabó antes que la cita a mano; ese paso se explica con la captura añadida a la derecha de la misma diapositiva.
- Pon PowerPoint en modo moderador para ver las notas sin que salgan en la grabación.

## Texto por diapositiva

### 1. Portada

Buenos días. Soy Jhonnatan Ospina y presento Clandestino, mi trabajo de fin de grado del ciclo de DAM. Corto el pelo en casa y la hice para mi propio uso y para otros barberos en mi situación. Es una app Android de citas en la que solo entran los clientes que invitas. Detrás de la app hay una API REST escrita en Java y una base de datos MySQL. Sigo los apartados de la plantilla: introducción, objetivos, diseño, metodología, desarrollo y conclusiones.

### 2. Introducción

Corto el pelo en casa, y las apps que encontré están pensadas para locales a pie de calle o, como Booksy, son un marketplace que enseña otras barberías a tus clientes. Clandestino es para barberos como yo, que trabajan desde casa y quieren cuidar a su propia clientela. Solo entran clientes invitados, cada día tiene un máximo de citas y la app se queda en lo justo. Los datos públicos dicen que casi todo el mundo usa el móvil a diario y que en este sector predomina el negocio sin empleados, así que tiene sentido una app pensada primero para el teléfono.

### 3. Análisis de mercado

Revisé siete aplicaciones de reservas en sus webs oficiales. Las especializadas, como Booksy, Fresha o Treatwell, giran en torno a un escaparate público que trae clientes nuevos a cambio de cuotas o comisiones y que, de paso, enseña la competencia a los clientes que ya tienes. Las generalistas, como Setmore o Calendly, no entienden de cupos ni de aprobar clientes. Ninguna junta las tres reglas que necesita un barbero que trabaja en casa, y ese es el hueco del proyecto.

### 4. Objetivos

El objetivo general es que un barbero que trabaja en casa lleve sus citas desde el móvil con sus tres reglas y sin reservas duplicadas. Lo concreté en cinco objetivos: una base de datos que bloquee por sí sola las dobles reservas, una API en Java sin frameworks, un cupo que aguante peticiones a la vez, una app Android con MVVM y pruebas que demuestren todo lo anterior.

### 5. Mockups

Antes de programar dibujé once mockups en draw.io, uno por pantalla. A la izquierda está el de «Pedir cita» y a la derecha cómo quedó en la app. Se parecen mucho. El cambio principal es que el calendario solo enseña las dos semanas que se pueden reservar, así que sobraban las flechas para cambiar de mes.

### 6. Modelo de datos

La base de datos tiene cinco tablas. La pieza más interesante está en «citas». Una columna generada, hora_ocupada, vale la hora de la cita solo mientras está pendiente o confirmada, y si no vale nulo. Con un índice único sobre la fecha y esa columna, MySQL rechaza por sí mismo una segunda cita activa a la misma hora, sin depender de la API.

### 7. Casos de uso

Hay tres actores y diez casos de uso. El visitante solo puede registrarse con un código. El cliente consulta los días libres, pide cita y gestiona las suyas, y el barbero lleva las invitaciones, los clientes, las solicitudes, la agenda y los horarios. De ahí salieron treinta requisitos funcionales, y la memoria tiene una tabla que relaciona cada caso con su pantalla y sus rutas.

### 8. Metodología

Seguí un ciclo incremental. Primero diseñé todo el sistema y después lo construí por piezas que se podían probar solas: la base de datos, la API y por último la app. De Scrum tomé la lista de trabajo por prioridades, P1, P2 y P3, y los ciclos cortos con revisión al final. El Gantt muestra las cuatro etapas. Lo macizo ya está hecho y lo discontinuo es el cierre. La última barra del desarrollo es la cita a mano, de la segunda fase. He compaginado el proyecto con el trabajo, con unas tres horas por día entre semana y entre dos y cuatro horas en todo el fin de semana. La estimación total es de 78 horas, incluido el cierre pendiente.

### 9. Arquitectura

El sistema tiene tres piezas. La app no se conecta nunca a MySQL. Si lo hiciera, las credenciales irían dentro del APK y cualquiera podría sacarlas. La app habla por HTTP con la API, enviando JSON y un token, y la API es la única que entra en la base de datos. La API no usa ningún framework. Va sobre el servidor HTTP que trae el JDK, y las rutas, el JSON y las transacciones están escritos a mano para poder explicarlos.

### 10. Seguridad

La seguridad la revisé con las categorías del estándar OWASP MASVS. Las contraseñas se guardan con PBKDF2, que repite el cálculo seiscientas mil veces para dificultar que alguien pruebe contraseñas de forma masiva. La sesión es un JWT que firmo yo con HMAC, y en el móvil se guarda cifrado con una clave del Keystore de Android. Además hay un límite de intentos de acceso y todas las consultas van con PreparedStatement, para separar los datos del código SQL.

### 11. Concurrencia

Esta es la parte más difícil del proyecto. Si dos clientes piden a la vez la última plaza del día, cada petición cuenta las citas, ve que queda sitio e inserta. El índice único no lo evita porque las horas son distintas, y ningún índice puede contar las citas de un día. Lo resolví con un cerrojo por fecha y una transacción que cuenta e inserta junta. La prueba lo demuestra: sin cerrojo entran cinco citas en un día con cupo uno, y con cerrojo solo una.

### 12. Pruebas

En total hay ciento cuarenta y cuatro pruebas automáticas y todas pasan. Las de la API van contra una base de datos aparte y con un reloj que puedo mover, para comprobar reglas como que no se puede pedir una hora pasada. Además recorrí la app entera en el emulador siguiendo diez casos de prueba y comprobé a mano las respuestas de error de la API.

### 13. Demostración (menos de dos minutos)

Ahora lo enseño funcionando en el emulador, con la API y MySQL en marcha en el ordenador.

1. Entro como cliente y pido una cita para el día libre que elegiste antes.
2. Cierro sesión y entro como barbero. En «Solicitudes» aparece la cita nueva.
3. La confirmo moviéndola media hora con el selector de hora final.
4. Voy a «Agenda» y la cita sale en esa semana como confirmada.
5. Desde la misma agenda pulso «Nueva cita» y meto la de un cliente que ha llamado por teléfono. Se guarda ya confirmada y la agenda salta a su día.
6. Termino en «Horarios», donde se fijan el tramo y el cupo de cada día.

Si la demo fallara, paso a la diapositiva siguiente, que está oculta y tiene el vídeo de respaldo.

### 14. Vídeo de respaldo (oculta, solo si falla la demo)

Este vídeo muestra la solicitud del cliente, la confirmación del barbero y la agenda. Dura 66 segundos. A la derecha se ve además una captura de la cita manual, que se añadió después de grabarlo. En ese diálogo el barbero elige un cliente aprobado, el día y la hora, y la cita se guarda confirmada.

### 15. Conclusiones

Los cinco objetivos están cumplidos y el prototipo cubre todo el ciclo de la cita. De la segunda fase ya está hecha la cita a mano. Tiene límites claros: no usa HTTPS, así que solo vale en local, el cerrojo del cupo solo funciona con una copia de la API y las solicitudes no caducan solas. Como trabajo futuro queda el resto de la segunda fase, los avisos al cliente y publicar la API con HTTPS. La parte que más aporta al aprendizaje es comprobar con una prueba por qué el índice único no basta para proteger el cupo diario.

### 16. Agradecimientos

Esto es todo. Gracias por la atención. El código, la memoria y los anexos de instalación están en el repositorio.
