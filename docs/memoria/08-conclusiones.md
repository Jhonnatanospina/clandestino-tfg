# 8. Conclusiones y proyección a futuro

## 8.1 Cumplimiento de los objetivos

Los cinco objetivos específicos se han cumplido en lo esencial.

| Objetivo | Grado de cumplimiento |
|---|---|
| 1. Base de datos que impida la doble reserva | Cumplido. La columna generada `hora_ocupada` con su índice único bloquea dos citas activas a la misma hora, y las 21 pruebas del esquema lo confirman. |
| 2. API REST en Java sin frameworks, con autenticación y roles | Cumplido. Tiene 24 rutas, contraseñas con PBKDF2, token firmado con HMAC y comprobación de rol y estado en cada petición. |
| 3. Cupo diario seguro ante peticiones simultáneas | Cumplido. La prueba con 5 hilos crea 5 citas en un día con cupo 1 si se quita el `synchronized` y solo 1 si se mantiene. |
| 4. App Android con MVVM que no bloquee el hilo principal | Cumplido. Tiene todas las pantallas de cliente y barbero, más el diálogo de la cita a mano, y toda la red va en hilos secundarios. |
| 5. Comprobación con pruebas y en el emulador | Cumplido. Las 97 pruebas de la API y las 26 de la app pasan, y el recorrido completo está documentado con capturas. |

*Tabla 8. Grado de cumplimiento de los objetivos. Fuente: elaboración propia.*

## 8.2 Limitaciones

El prototipo funciona solo en local. Antes de usarlo con mis clientes y de ofrecerlo a otros barberos habría que resolver varias cosas:

- **No hay HTTPS.** La app habla con la API por HTTP sin cifrar, algo aceptable en el emulador pero no en una red real. Habría que poner la API detrás de un certificado TLS.
- **El cerrojo del cupo vive en la memoria de la API.** Con una sola copia del servidor funciona, pero con dos habría que pasar el bloqueo a la base de datos.
- **Faltan comprobaciones de calidad previstas en el plan.** No hay pruebas instrumentadas de la interfaz, no he medido el rendimiento con `EXPLAIN` ni con `StrictMode`, y la app no tiene animaciones ni un diseño pensado para horizontal o tableta.
- **Las solicitudes pendientes no caducan solas.** Si el barbero no responde, la cita sigue ocupando su hueco hasta que la rechaza.
- **Dos citas pueden solaparse media hora.** El índice único solo impide la misma hora exacta, así que al confirmar a otra hora o al meter una cita a mano puede quedar una a las 10:00 y otra a las 10:30. Es la regla del original (DP-04), y el barbero lo ve en la agenda antes de decidir.
- **No hay copia de seguridad automática** de la base de datos.

## 8.3 Trabajo futuro

Las líneas de mejora siguen el orden de prioridad del proyecto. De la segunda fase ya está hecha la cita que el barbero crea para los clientes que llaman por teléfono, así que lo siguiente serían las demás funciones P2 especificadas (cambio de @usuario, borrado de clientes, caducidad automática de solicitudes y copia de seguridad con `mysqldump`) y, por último, las que dejé fuera del alcance al principio:

- Avisos al cliente cuando el barbero confirma o rechaza su cita.
- El resumen mensual de ingresos y citas atendidas, que ya tiene los datos necesarios porque el precio se guarda en cada cita.
- Publicar la API en un servidor con HTTPS.
- Que otro barbero pueda montar su propia copia, con su nombre y sus colores, sin tocar el código.

## 8.4 Valoración personal

Este proyecto me ha servido para entender lo que hay debajo de las herramientas que uso a diario. En la versión web Supabase me lo daba casi todo hecho, y aquí he tenido que programar a mano las rutas, la autenticación y las transacciones. Lo que más me costó fue el cupo diario. Pensaba que el índice único bastaba, y hasta que la prueba con cinco hilos no metió cinco citas en un día con cupo de una no entendí de verdad qué es una condición de carrera.

Si empezara de nuevo, probaría la app en el emulador mucho antes, porque varios fallos solo aparecieron al usarla con datos reales, y prepararía el HTTPS desde el principio. Mi idea es usar Clandestino con mis clientes en cuanto esté publicada y, si me funciona, ofrecérsela a otros barberos que trabajan en casa como yo.
