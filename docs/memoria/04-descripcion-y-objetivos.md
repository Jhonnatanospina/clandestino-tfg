# 4. Descripción y objetivos

## 4.1 Descripción del prototipo

El prototipo tiene tres piezas que se comunican por la red local del ordenador de desarrollo. La **app Android**, en Java con vistas XML y Material 3, sigue el patrón MVVM, funciona desde Android 8.0 (API 26) y es lo único que ven los usuarios. La **API REST**, en Java 21 sobre el servidor HTTP del JDK y sin frameworks, recibe peticiones en JSON, aplica las reglas del negocio y es la única que entra en la **base de datos MySQL 9.7**, que guarda usuarios, invitaciones, horarios, excepciones de calendario y citas en cinco tablas.

La app reconoce dos tipos de usuario y muestra a cada uno su panel con una barra de navegación inferior. El **cliente** entra con su @usuario y su contraseña, y mientras no está aprobado ve una pantalla de espera. Después tiene tres pestañas. En «Mis citas» ve sus citas con su estado y cancela las que siguen activas, en «Pedir cita» elige un día de las dos próximas semanas y una hora libre, y en «Ajustes» cambia el tema y cierra la sesión.

El **barbero** tiene cuatro pestañas. En «Solicitudes» confirma cada cita pendiente con la hora pedida o con otra, o la rechaza. En «Agenda» ve las citas confirmadas por semanas, marca si el cliente vino y mete a mano la cita de quien llama por teléfono. En «Horarios» fija el tramo y el cupo de cada día y cierra días por vacaciones, y en «Clientes» genera códigos de invitación y aprueba o bloquea a cada cliente.

Solo las citas pendientes y confirmadas ocupan hueco y cuentan para el cupo, así que cancelar o rechazar una cita libera su hora en ese momento. El diagrama de estados completo está en la figura III.19 del anexo III.

## 4.2 Objetivo general

Desarrollar una aplicación Android, con su API y su base de datos, que permita a un barbero que trabaja desde casa gestionar las citas de su propia clientela con acceso por invitación, cupo diario y sin riesgo de reservas duplicadas, de forma más sencilla que las plataformas del mercado.

## 4.3 Objetivos específicos

1. Diseñar una base de datos relacional en MySQL que impida por sí misma dos citas activas a la misma hora del mismo día.
2. Programar una API REST en Java, sin frameworks, que autentique a los usuarios con contraseñas protegidas y tokens firmados y que separe lo que puede hacer cada rol.
3. Asegurar que el cupo diario se respeta aunque varios clientes pidan cita a la vez, y demostrarlo con una prueba automática de hilos concurrentes.
4. Construir una app Android en Java con arquitectura MVVM que cubra las pantallas de cliente y barbero sin bloquear el hilo principal con la red.
5. Comprobar el funcionamiento con pruebas unitarias y de integración y con un recorrido completo en el emulador, documentado con capturas.
