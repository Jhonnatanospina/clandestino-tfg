# Guion del vídeo de defensa

Borrador para reescribir con tus palabras. El texto de cada diapositiva es el mismo que llevan las notas del orador de `presentacion/clandestino-defensa.pptx`, así que si cambias algo aquí, cámbialo también allí (o en `presentacion/_generador/generar.js` y vuelve a generar).

La guía pide un vídeo de 10 minutos como máximo, sin cortes y con las diapositivas en el mismo orden que la memoria. El guion está pensado para unos 9 minutos y deja margen por si la demo se alarga.

## Tiempos

Calculados a unas 130 palabras por minuto, que es un ritmo tranquilo para leer en voz alta.

| # | Diapositiva | Tiempo | Acumulado |
|---|---|---|---|
| 1 | Portada | 0:25 | 0:25 |
| 2 | Recorrido | 0:25 | 0:50 |
| 3 | Introducción | 0:30 | 1:20 |
| 4 | Análisis de mercado | 0:30 | 1:50 |
| 5 | Objetivos | 0:30 | 2:20 |
| 6 | Mockups | 0:25 | 2:45 |
| 7 | Modelo de datos | 0:30 | 3:15 |
| 8 | Casos de uso | 0:30 | 3:45 |
| 9 | Metodología | 0:30 | 4:15 |
| 10 | Arquitectura | 0:40 | 4:55 |
| 11 | Seguridad | 0:30 | 5:25 |
| 12 | Concurrencia | 0:40 | 6:05 |
| 13 | Pruebas | 0:30 | 6:35 |
| 14 | Demostración | 1:30 | 8:05 |
| 15 | Conclusiones | 0:40 | 8:45 |
| 16 | Gracias | 0:10 | 8:55 |

Si en el ensayo pasas de 9:30, recorta primero en las diapositivas 2 y 8, que son las que menos pierden.

## Antes de grabar

- Arranca MySQL con `scripts\mysql.cmd iniciar` y la API con `gradlew run` desde la carpeta `api`.
- Abre el emulador con la app instalada y comprueba que el login responde.
- Deja la app en la pantalla de entrada (A1) y la sesión cerrada.
- Elige de antemano el día libre que vas a pedir en la demo. Tiene que estar dentro de las dos semanas reservables y con cupo.
- Ten a mano las contraseñas de prueba del `.env`, pero que no salgan en pantalla mientras las escribes.
- Graba antes un vídeo corto de la demo por si algo falla en directo.
- Pon PowerPoint en modo moderador para ver las notas sin que salgan en la grabación.

## Texto por diapositiva

### 1. Portada

Buenos días. Soy [nombre] y presento Clandestino, mi trabajo de fin de grado del ciclo de DAM. Es una app Android para gestionar las citas de una barbería real que solo atiende con cita y a clientes invitados. Detrás de la app hay una API REST escrita en Java y una base de datos MySQL.

### 2. Recorrido

La presentación sigue el mismo orden que la memoria. Empiezo por el problema y el mercado, sigo con los objetivos y el diseño, la metodología y el desarrollo, y me paro en lo más difícil, que es el cupo diario con peticiones simultáneas. Termino con las pruebas, una demostración y las conclusiones.

### 3. Introducción

Clandestino es una barbería de una sola persona. Trabaja siempre con cita, solo acepta clientes que vienen invitados y cada día tiene un máximo de citas. Los datos públicos dicen que casi todo el mundo usa el móvil a diario y que en este sector predomina el negocio sin empleados, así que tiene sentido una app pensada primero para el teléfono.

### 4. Análisis de mercado

Revisé siete aplicaciones de reservas en sus webs oficiales. Las especializadas, como Booksy, Fresha o Treatwell, giran en torno a un escaparate público que trae clientes nuevos a cambio de cuotas o comisiones. Las generalistas, como Setmore o Calendly, no entienden de cupos ni de aprobar clientes. Ninguna junta las tres reglas que necesita este negocio, y ese es el hueco del proyecto.

### 5. Objetivos

El objetivo general es que la barbería gestione sus citas desde el móvil con sus tres reglas y sin reservas duplicadas. Lo concreté en cinco objetivos: una base de datos que bloquee por sí sola las dobles reservas, una API en Java sin frameworks, un cupo que aguante peticiones a la vez, una app Android con MVVM y pruebas que demuestren todo lo anterior.

### 6. Mockups

Antes de programar dibujé once mockups en draw.io, uno por pantalla. A la izquierda está el de «Pedir cita» y a la derecha cómo quedó en la app. Se parecen mucho. El cambio principal es que el calendario solo enseña las dos semanas que se pueden reservar, así que sobraban las flechas para cambiar de mes.

### 7. Modelo de datos

La base de datos tiene cinco tablas. La pieza más interesante está en «citas». Una columna generada, hora_ocupada, vale la hora de la cita solo mientras está pendiente o confirmada, y si no vale nulo. Con un índice único sobre la fecha y esa columna, MySQL rechaza por sí mismo una segunda cita activa a la misma hora, sin depender de la API.

### 8. Casos de uso

Hay tres actores y diez casos de uso. El visitante solo puede registrarse con un código. El cliente consulta los días libres, pide cita y gestiona las suyas, y el barbero lleva las invitaciones, los clientes, las solicitudes, la agenda y los horarios. De ahí salieron treinta requisitos funcionales, y la memoria tiene una tabla que relaciona cada caso con su pantalla y sus rutas.

### 9. Metodología

Seguí un ciclo incremental. Primero diseñé todo el sistema y después lo construí por piezas que se podían probar solas: la base de datos, la API y por último la app. De Scrum tomé la lista de trabajo por prioridades, P1, P2 y P3, y los ciclos cortos con revisión al final. El Gantt muestra las cuatro etapas. Lo macizo ya está hecho y lo discontinuo es el cierre.

### 10. Arquitectura

El sistema tiene tres piezas. La app no se conecta nunca a MySQL. Si lo hiciera, las credenciales irían dentro del APK y cualquiera podría sacarlas. La app habla por HTTP con la API, enviando JSON y un token, y la API es la única que entra en la base de datos. La API no usa ningún framework. Va sobre el servidor HTTP que trae el JDK, y las rutas, el JSON y las transacciones están escritos a mano para poder explicarlos.

### 11. Seguridad

La seguridad la revisé con las categorías del estándar OWASP MASVS. Las contraseñas se guardan con PBKDF2 y seiscientas mil iteraciones. La sesión es un JWT que firmo yo con HMAC, y en el móvil se guarda cifrado con una clave del Keystore de Android. Además hay un límite de intentos de acceso y todas las consultas van con PreparedStatement, así que no cabe la inyección SQL.

### 12. Concurrencia

Esta es la parte más difícil del proyecto. Si dos clientes piden a la vez la última plaza del día, cada petición cuenta las citas, ve que queda sitio e inserta. El índice único no lo evita porque las horas son distintas, y ningún índice puede contar las citas de un día. Lo resolví con un cerrojo por fecha y una transacción que cuenta e inserta junta. La prueba lo demuestra: sin cerrojo entran cinco citas en un día con cupo uno, y con cerrojo solo una.

### 13. Pruebas

En total hay ciento treinta y cuatro pruebas automáticas y todas pasan. Las de la API van contra una base de datos aparte y con un reloj que puedo mover, para comprobar reglas como que no se puede pedir una hora pasada. Además recorrí la app entera en el emulador siguiendo siete casos de prueba y comprobé a mano las respuestas de error de la API.

### 14. Demostración (unos 90 segundos)

Ahora lo enseño funcionando en el emulador, con la API y MySQL en marcha en el ordenador.

1. Entro como cliente y pido una cita para el día libre que elegiste antes.
2. Cierro sesión y entro como barbero. En «Solicitudes» aparece la cita nueva.
3. La confirmo moviéndola media hora con el selector de hora final.
4. Voy a «Agenda» y la cita sale en esa semana como confirmada.
5. Termino en «Horarios», donde se fijan el tramo y el cupo de cada día.

Si la demo fallara, tengo grabado un vídeo corto de respaldo.

### 15. Conclusiones

Los cinco objetivos están cumplidos y el prototipo cubre todo el ciclo de la cita. Tiene límites claros: no usa HTTPS, así que solo vale en local, el cerrojo del cupo solo funciona con una copia de la API y las solicitudes no caducan solas. Como trabajo futuro quedan las funciones de la segunda fase, los avisos al cliente y publicar la API con HTTPS. [Una frase de valoración personal.]

### 16. Gracias

Esto es todo. Gracias por la atención. El código, la memoria y los anexos de instalación están en el repositorio.
