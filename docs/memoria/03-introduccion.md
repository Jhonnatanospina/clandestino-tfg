# 3. Introducción

## 3.1 Contexto

Corto el pelo en mi casa, con cita previa y a una persona cada vez, casi siempre a clientes que ya conozco o que vienen recomendados. No tengo local a pie de calle ni lo quiero, y como yo hay muchos barberos que empiezan así o que han decidido quedarse trabajando desde casa.

Para organizarme ya uso una aplicación web propia, también llamada Clandestino, hecha con Next.js y Supabase. En este proyecto he reconstruido su núcleo como app nativa de Android en Java con vistas XML, con una API REST propia y una base de datos MySQL local. Lo que en la web resuelve Supabase aquí lo he programado yo, y así he aplicado lo aprendido en los módulos del ciclo de Desarrollo de Aplicaciones Multiplataforma (Real Decreto 450/2010, 2010), que la tabla III.14 del anexo III relaciona con cada parte del proyecto.

## 3.2 Problema que se resuelve

Cuando busqué una aplicación para llevar mis citas, todas daban por hecho un negocio a pie de calle, con su dirección pública y a menudo varios empleados. Las más conocidas, como Booksy, son además un marketplace, así que el cliente entra para reservar contigo y la propia app le enseña otras barberías de la zona. A quien trabaja en casa eso no le interesa, porque vive de una clientela pequeña y fiel que necesita cuidar, no poner delante de la competencia.

Ese barbero quiere decidir quién reserva, porque no va a abrir su puerta a cualquiera, limitar cuántas citas acepta cada día y no tener nunca dos a la misma hora, porque no hay un segundo sillón que absorba el error. Con mensajes sueltos por WhatsApp todo eso depende de su memoria.

Clandestino es la app que me habría gustado encontrar. Solo entran los clientes que el barbero invita y aprueba, él fija el cupo de cada día y el sistema impide la doble reserva aunque dos clientes pidan a la vez (apartado 7.4). Lo demás lo he dejado fuera para que la app sea simple.

## 3.3 Alcance

El prototipo cubre el ciclo completo de una cita. El barbero invita al cliente con un código y lo aprueba o lo bloquea, y el cliente pide cita en un hueco libre. El barbero la confirma (a esa hora o a otra) o la rechaza, marca después si el cliente vino y configura su horario semanal, el cupo de cada día, las vacaciones y los días sueltos de apertura.

Quedan fuera las métricas de ingresos, las nóminas, los gastos, los avisos automáticos y el cobro en la app. El prototipo funciona en local, con la API y la base de datos en mi ordenador y la app en el emulador, y publicarlo exigiría cifrar el tráfico con HTTPS.

## 3.4 Análisis de mercado

### 3.4.1 Datos del sector

Según el Instituto Nacional de Estadística (INE), a 1 de enero de 2025 había en España 155.724 empresas de «otros servicios personales» (división 96 de la CNAE 2009, que incluye la peluquería). El 60,2 % no tiene asalariados y el 84,7 % son personas físicas (INE, s. f.), así que predomina el negocio de una sola persona, como el del barbero que trabaja en casa. Además, el 92,5 % de las personas de 16 a 74 años usa Internet a diario (INE, 2025) y el 94,6 % de los internautas usa WhatsApp (Comisión Nacional de los Mercados y la Competencia [CNMC], 2026), lo que justifica una app pensada para el móvil.

### 3.4.2 Aplicaciones existentes

He revisado siete aplicaciones de reserva en sus webs oficiales (tabla 1). Las cuatro primeras están especializadas en belleza y barbería, y las tres últimas sirven para cualquier negocio de servicios.

Ninguna combina el acceso por invitación, el cupo diario y la ausencia de cuotas y comisiones. Las especializadas viven de un escaparate que enseña la competencia a tus propios clientes, y las generalistas piden configurar mucho de lo que un barbero solo no necesita. Clandestino es para quien trabaja en casa y quiere ordenar y conservar la clientela que ya tiene, no para un salón con empleados.

| Aplicación | Precio (web oficial, 30-09-2026) | Por qué no encaja |
|---|---|---|
| Booksy | 34,99 € + IVA al mes y comisiones | Enseña otras barberías a tus clientes |
| Fresha | 19,95 € al mes y comisiones | Escaparate para captar clientes |
| Treatwell | 25 % de cada cliente nuevo | Vive de traer clientes nuevos |
| AgendaPro | Desde 10 € al mes | Personal, inventario y caja |
| Square | Gratis, 19 € o 49 € al mes | Vive de su pasarela de pago |
| Setmore | Gratis o 5 $ por usuario y mes | Sin aprobación de clientes |
| Calendly | Gratis o 10 $ por usuario y mes | Sin cupo diario ni aprobaciones |

*Tabla 1. Comparativa de aplicaciones de reserva de citas. Fuente: elaboración propia a partir de las webs oficiales de Booksy (s. f.), Fresha (s. f.), Treatwell (s. f.), AgendaPro (s. f.), Square (s. f.), Setmore (s. f.) y Calendly (s. f.).*
