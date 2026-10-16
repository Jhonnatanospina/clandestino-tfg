# 3. Introducción

## 3.1 Contexto

Clandestino es una barbería pequeña que trabaja solo con cita previa. La lleva un único barbero, que corta a una persona cada vez, con citas de una hora y un número reducido de citas al día. Para organizarse ya usa una aplicación web propia, también llamada Clandestino, hecha con Next.js y Supabase, en la que los clientes piden cita y el barbero decide qué solicitudes acepta.

Este proyecto vuelve a construir el núcleo de esa aplicación como app nativa de Android, escrita en Java con vistas XML, con una API REST propia también en Java y una base de datos MySQL local. El cambio de tecnología tiene un motivo formativo. En la versión web casi todo el trabajo de servidor lo hace un servicio externo (Supabase), mientras que aquí cada pieza se programa a mano, y eso permite aplicar lo aprendido en los módulos del ciclo de Desarrollo de Aplicaciones Multiplataforma (Real Decreto 450/2010, 2010): programación de dispositivos móviles, servicios y procesos, bases de datos y acceso a datos, entre otros.

## 3.2 Problema que se resuelve

Una barbería de una sola persona tiene necesidades distintas de las de un salón con varios empleados. El barbero quiere controlar quién puede reservar, porque solo atiende a clientes conocidos o recomendados. Necesita limitar cuántas citas acepta cada día, ya que su capacidad es pequeña y varía según la semana. Y no puede permitirse dos citas a la misma hora, porque no hay un segundo sillón que absorba el error.

Con mensajes sueltos por WhatsApp estas reglas dependen de la memoria del barbero, y con las plataformas generalistas se resuelven a medias. Esas plataformas están pensadas para atraer clientes nuevos desde un escaparate público y cobran por ello una cuota mensual o una comisión, algo que una barbería por invitación no necesita (apartado 3.4).

El proyecto ofrece tres cosas a la vez. El acceso es solo por invitación y con aprobación del barbero, el cupo diario lo configura él mismo y el sistema impide la doble reserva incluso cuando dos clientes piden a la vez. El caso de la concurrencia es el más delicado del proyecto, porque ningún índice de la base de datos puede evitar que dos peticiones simultáneas superen el cupo del día, y se explica en el apartado 7.

## 3.3 Alcance

El prototipo cubre el ciclo completo de una cita:

- El barbero genera un código de invitación y el cliente se registra con él.
- El barbero aprueba al cliente (o lo bloquea).
- El cliente consulta los días y las horas libres y pide una cita, que queda pendiente.
- El barbero la confirma, con la hora pedida o con otra, o la rechaza.
- Tras la cita, el barbero marca si el cliente vino o no.
- El barbero configura su horario semanal, el cupo de cada día, las vacaciones y los días sueltos de apertura.

Quedan fuera, por decisión de alcance, las métricas de ingresos, las nóminas y los gastos, los avisos automáticos (notificaciones, recordatorios y mensajes de WhatsApp) y el cobro en la app. Sin avisos, el cliente ve si su cita está confirmada al abrir la aplicación o al deslizar la lista hacia abajo para refrescarla. El apartado 8 recoge estas funciones como líneas de trabajo futuro.

El prototipo funciona en un entorno local: la API y la base de datos se ejecutan en el ordenador de desarrollo y la app se prueba en el emulador de Android Studio. Publicarlo en Internet exigiría, como mínimo, cifrar el tráfico con HTTPS, y así se declara entre las limitaciones.

## 3.4 Análisis de mercado

### Datos del sector

Los datos públicos describen un sector de negocios muy pequeños con clientes que ya usan el móvil a diario.

| Dato | Valor | Fuente |
|---|---|---|
| Empresas cuya actividad principal es «otros servicios personales» (división 96 de la CNAE 2009, que incluye peluquería y estética, pero también lavanderías o servicios funerarios), a 1 de enero de 2025 | 155.724 empresas, de las que el 60,2 % no tiene asalariados y el 84,7 % son personas físicas | INE (s. f.) |
| Personas de 16 a 74 años que usaron Internet en los tres últimos meses | 96,3 % (el 92,5 % a diario, es decir, al menos cinco días por semana) | INE (2025) |
| Hogares que solo tienen teléfono móvil, sin fijo | 50,3 % | INE (2025) |
| Internautas que usan WhatsApp de forma habitual | 94,6 % | CNMC (2026) |

*Tabla 1. Datos del sector y del uso del móvil en España. Fuente: elaboración propia a partir de INE (2025, s. f.) y CNMC (2026).*

La cifra de empresas no es exclusiva de las barberías, porque la CNAE las incluye con otros servicios personales, pero muestra que en ese grupo predomina el negocio de una sola persona, que es el caso de Clandestino. El uso del móvil justifica que la app se diseñe pensando primero en el teléfono.

### Aplicaciones existentes

Se han revisado siete aplicaciones de reserva de citas en sus webs oficiales. Las cuatro primeras están especializadas en belleza y barbería, y las tres últimas sirven para cualquier negocio de servicios.

| Aplicación | Para quién | Modelo y precio (según su web oficial, consultada el 30-09-2026) | Diferencia con Clandestino |
|---|---|---|---|
| Booksy | Barberías, peluquerías y estética, con escaparate público para clientes | 34,99 € + IVA al mes y 8 € por empleado adicional, más comisiones por pagos | Escaparate público y cobro digital; no limita el acceso a clientes invitados |
| Fresha | Salones y barberías de cualquier tamaño, con escaparate público | Plan individual de 19,95 € al mes, comisiones por pagos y una tarifa por cada cliente nuevo que llega desde su escaparate | Plataforma completa (cobros, inventario, marketing); pensada para captar clientes |
| Treatwell | Salones de belleza y bienestar que quieren clientes nuevos | Comisión del 25 % en la primera reserva de cada cliente nuevo y contrato mínimo de 12 meses | Su negocio es captar clientes nuevos, justo lo que Clandestino no busca |
| AgendaPro | Barberías y salones, sobre todo de Latinoamérica | Plan individual de 10 € al mes (sin indicar si incluye IVA); planes de 19 €, 59 € y 199 € | Gestiona comisiones del personal, inventario y caja |
| Square Appointments | Negocios de servicios en general | Plan gratuito y planes de 19 € y 49 € + IVA al mes, con comisiones por cada cobro | El plan gratuito se sostiene con su pasarela de pago |
| Setmore | Pequeños negocios de servicios en general | Gratis hasta 4 usuarios; plan Pro de 5 $ por usuario al mes en pago anual | Generalista; las funciones útiles están en el plan de pago |
| Calendly | Reuniones profesionales (ventas, consultoría) | Gratis con un tipo de evento; plan Standard de 10 $ por usuario al mes | No conoce el concepto de cupo diario ni el de aprobar clientes |

*Tabla 2. Comparativa de aplicaciones de reserva de citas. Fuente: elaboración propia a partir de las webs oficiales de Booksy (s. f.), Fresha (s. f.), Treatwell (s. f.), AgendaPro (s. f.), Square (s. f.), Setmore (s. f.) y Calendly (s. f.).*

Ninguna de las siete combina las tres reglas que necesita el negocio: acceso cerrado por invitación con aprobación, cupo diario configurable y ausencia de cuotas y comisiones. Las especializadas giran en torno a un escaparate público que trae clientes nuevos a cambio de dinero, y las generalistas no entienden de cupos ni de aprobaciones.

Clandestino ocupa un hueco pequeño, el de la barbería de una persona que ya tiene su clientela y solo quiere ordenarla. Esa misma simplicidad es su límite, ya que no sirve para un salón con varios empleados, no cobra y no busca clientes nuevos. Para el negocio al que va dirigida, eso no es un inconveniente.
