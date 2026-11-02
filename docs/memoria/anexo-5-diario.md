# Anexo V. Diario de desarrollo

Este diario resume los hitos que constan en el registro técnico. Las dificultades y sus soluciones se han contrastado con ese registro. El proyecto se ha compaginado con el trabajo, con unas 3 horas al día entre semana y entre 2 y 4 horas en total por fin de semana. Las horas por bloque son un reparto aproximado de esa disponibilidad, no un registro de tiempo medido. Los bloques agrupan tareas de varias sesiones; la fecha del hito no significa que todas sus horas se concentraran en ese día.

## Inicio y definición del alcance · 30 de septiembre y 1 de octubre de 2026

**Etapa:** inicio. **Dedicación estimada:** 6 horas.

El trabajo partió de la versión web de Clandestino y de las necesidades de un barbero que trabaja desde casa. Se delimitó una primera versión con acceso por invitación, aprobación de clientes, solicitudes de cita, agenda y horarios. Las métricas y los avisos quedaron fuera para poder terminar el ciclo de reserva completo.

La preparación del entorno exigió comprobar la compatibilidad entre Android Studio, Gradle y el JDK. También hubo que ajustar el consumo de memoria para trabajar con MySQL y el emulador en el mismo equipo. MySQL se instaló de forma portable para arrancarlo solo durante las pruebas. El siguiente paso fue definir los datos y las reglas antes de empezar la API.

## Base de datos y diseño · 1 de octubre de 2026

**Etapa:** diseño. **Dedicación estimada:** 14 horas.

Se prepararon el esquema de cinco tablas, los casos de uso, los requisitos, el contrato de la API y los mockups. Las decisiones sobre el horizonte de reserva, los estados de la cita y las invitaciones quedaron por escrito para que la app y el servidor siguieran las mismas reglas.

MySQL no permitía trasladar directamente el índice único parcial de la versión original. Se utilizó una columna generada que conserva la hora mientras la cita está activa y toma el valor nulo cuando deja de estarlo. Las 21 pruebas del esquema comprobaron las restricciones y la liberación del hueco al cancelar.

La revisión del diseño encontró diferencias entre documentos y pantallas, entre ellas un mockup que mostraba el precio al cliente. También hubo que dividir diagramas grandes para mejorar su lectura. Estas correcciones se hicieron antes de programar; el diseño quedó como referencia para la API.

## Convenciones y contraseñas · 9 de octubre de 2026

**Etapa:** desarrollo. **Dedicación estimada:** 4 horas.

Se unificaron los nombres de clases, métodos y paquetes en español y se preparó la clase de contraseñas como referencia de estilo. Se comprobó que una misma contraseña producía huellas distintas y que la verificación rechazaba claves incorrectas y datos dañados.

El coste de PBKDF2 se midió en el equipo de desarrollo. Con 600 000 rondas, el tiempo dependía de la carga del ordenador y de si la máquina virtual acababa de arrancar. Se mantuvo el valor previsto para el uso normal y se redujo en las pruebas para que estas no fueran innecesariamente lentas. Después se continuó con los servicios y las rutas de la API.

## API y reservas simultáneas · hasta el 14 de octubre de 2026

**Etapa:** desarrollo. **Dedicación estimada:** 16 horas.

Se completaron las capas HTTP, de servicios y de acceso a MySQL, junto con el registro, el inicio de sesión y las operaciones de cliente y barbero. Al utilizar el servidor HTTP del JDK hubo que resolver las rutas, los errores y el reparto de peticiones entre hilos.

La dificultad principal fue proteger el cupo diario. El índice único impedía repetir una hora exacta, pero dos peticiones a horas distintas podían contar la misma plaza libre. La solución combinó un bloqueo por fecha con una transacción que comprueba el cupo e inserta la cita antes de liberar el bloqueo.

La prueba con cinco hilos mostró la diferencia entre ambas situaciones. Sin el bloqueo podían entrar cinco citas con un cupo de una, mientras que con él solo entraba una. También se corrigió la lectura de fechas para rechazar días inexistentes. La API terminó esta fase con 88 pruebas correctas y quedó preparada para conectarla con Android.

## App Android y recorrido completo · hasta el 15 de octubre de 2026

**Etapa:** desarrollo. **Dedicación estimada:** 16 horas.

Se completaron las pantallas de acceso, cliente y barbero con vistas XML y la separación entre vistas, modelos de vista y repositorios. Las peticiones de red se ejecutan fuera del hilo principal. En el emulador se recorrió el proceso desde la invitación hasta la confirmación de una cita, con la API y MySQL en funcionamiento.

Las pruebas de uso revelaron problemas que no aparecían en las pruebas unitarias. El teclado tapaba el código recién generado, algunos botones desactivados parecían activos y el calendario usaba el idioma del emulador. Se ajustaron el foco, los estilos y la configuración del idioma.

La alineación de las horas requirió revisar el manifiesto. Android ignoraba la alineación del texto porque el soporte de escritura de derecha a izquierda estaba desactivado. Al corregir esa opción, las etiquetas quedaron centradas. La fase terminó con 25 pruebas unitarias correctas, lint sin errores y capturas del recorrido. Los diagramas se actualizaron con las clases y métodos del código final.

## Memoria y preparación de la defensa · desde el 16 de octubre de 2026

**Etapa:** cierre. **Dedicación estimada para la fase completa:** 18 horas, incluidos el ensayo y la grabación pendientes.

Se redactaron los capítulos de la memoria, los manuales de instalación y usuario y los anexos de diseño y pruebas. Cada descripción se comparó con la aplicación y sus capturas. Se corrigieron referencias a una vista mensual de la agenda que no existe y a estados visuales que no coincidían con las pantallas.

La presentación se montó sobre la plantilla oficial, con notas para la exposición y una demostración de respaldo. Hubo que ajustar textos que se partían o se superponían al Gantt y comprobar la numeración de figuras y tablas de la memoria.

Esta fase sigue abierta. Quedan la revisión del documento definitivo, el ensayo con cronómetro y la grabación final de la defensa. El vídeo de respaldo también debe actualizarse para incluir la cita manual añadida después.

## Cita creada por el barbero · 17 de octubre de 2026

**Etapa:** desarrollo, ampliación P2. **Dedicación estimada:** 4 horas.

Se añadió la creación de una cita para un cliente aprobado desde la agenda del barbero. La API comprueba que la hora no haya pasado y que no exista otra cita activa a la misma hora. La cita se guarda confirmada, sin pasar por una solicitud del cliente.

Al girar la pantalla, los desplegables solo mostraban el elemento elegido porque Android restauraba el texto y filtraba la lista. Se guardó la selección de forma explícita y se restauró sin filtrar. También se recuperó el listener del calendario tras la recreación de la pantalla.

Se añadieron nueve pruebas a la API y una a la app, con lo que los conjuntos llegaron a 97 y 26 pruebas respectivamente. La función se comprobó en el emulador y se incorporó a los manuales, requisitos y diagramas. Las demás mejoras P2 permanecen fuera del alcance terminado.

## Balance del trabajo

La estimación central suma 78 horas: 6 de inicio, 14 de diseño, 40 de desarrollo y 18 de cierre. Corresponde a unas 18 horas semanales, con 3 horas por día laborable y unas 3 horas en todo el fin de semana. Una parte del cierre todavía está prevista. El registro muestra que las comprobaciones de concurrencia y el uso del emulador permitieron detectar errores que no se veían al revisar las pantallas o el esquema por separado. En un proyecto posterior convendría conectar antes una pantalla sencilla con la API y registrar los cambios en commits más pequeños.
