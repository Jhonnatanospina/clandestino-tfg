# 8. Conclusiones

## 8.1 Cumplimiento de los objetivos

Los cinco objetivos específicos se han cumplido en lo esencial.

| Objetivo | Grado de cumplimiento |
|---|---|
| 1. Base de datos que impida la doble reserva | Cumplido. La columna generada `hora_ocupada` con su índice único bloquea dos citas activas a la misma hora, y las 21 pruebas del esquema lo confirman. |
| 2. API REST en Java sin frameworks, con autenticación y roles | Cumplido. Tiene 23 rutas, contraseñas con PBKDF2, token firmado con HMAC y comprobación de rol y estado en cada petición. |
| 3. Cupo diario seguro ante peticiones simultáneas | Cumplido. La prueba con 5 hilos crea 5 citas en un día con cupo 1 si se quita el `synchronized` y solo 1 si se mantiene. |
| 4. App Android con MVVM que no bloquee el hilo principal | Cumplido. Tiene las siete pantallas, y toda la red va en hilos secundarios. |
| 5. Comprobación con pruebas y en el emulador | Cumplido. Las 88 pruebas de la API y las 25 de la app pasan, y el recorrido completo está documentado con capturas. |

*Tabla 12. Grado de cumplimiento de los objetivos. Fuente: elaboración propia.*

## 8.2 Limitaciones

El prototipo funciona solo en local. Antes de usarlo en la barbería habría que resolver varias cosas:

- **No hay HTTPS.** La app habla con la API por HTTP sin cifrar, algo aceptable en el emulador pero no en una red real. Habría que poner la API detrás de un certificado TLS.
- **El cerrojo del cupo vive en la memoria de la API.** Con una sola copia del servidor funciona, pero con dos habría que pasar el bloqueo a la base de datos.
- **Faltan comprobaciones de calidad previstas en el plan.** No hay pruebas instrumentadas de la interfaz, no se ha medido el rendimiento con `EXPLAIN` ni con `StrictMode`, y la app no tiene animaciones ni un diseño pensado para horizontal o tableta.
- **Las solicitudes pendientes no caducan solas.** Si el barbero no responde, la cita sigue ocupando su hueco hasta que la rechaza.
- **No hay copia de seguridad automática** de la base de datos.

## 8.3 Trabajo futuro

Las líneas de mejora siguen el orden de prioridad del proyecto. Primero irían las funciones P2 ya especificadas: cita creada por el barbero para clientes que llaman por teléfono, cambio de @usuario, borrado de clientes, caducidad automática de solicitudes y copia de seguridad con `mysqldump`. Después, las que se dejaron fuera del alcance al principio:

- Avisos al cliente cuando el barbero confirma o rechaza su cita.
- El resumen mensual de ingresos y citas atendidas, que ya tiene los datos necesarios porque el precio se guarda en cada cita.
- Publicar la API en un servidor con HTTPS.

## 8.4 Valoración personal

[completar por el alumno: qué ha aprendido, qué le ha costado más, qué haría distinto si empezara de nuevo y cómo encaja el proyecto con su barbería.]
