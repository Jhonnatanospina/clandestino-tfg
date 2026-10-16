# Convenciones de código

Norma del proyecto: el código tiene que parecer escrito por quien lo defiende y tiene que poder explicarse línea a línea en el vídeo. Para eso, **todo lo que ponemos nosotros va en español**, y los comentarios son una parte importante del trabajo, no un adorno.

## 1. Nombres

| Qué | Cómo | Ejemplos |
|---|---|---|
| Clases propias | Español, en `PascalCase`, sin tildes ni eñes | `Enrutador`, `Reloj`, `ServicioCitas`, `RepositorioUsuarios`, `ErrorNegocio` |
| Interfaces | Igual, sin prefijo `I` | `RepositorioCitas` (la implementación es `RepositorioCitasJdbc`) |
| Actividades, fragmentos y modelos de vista (Android) | Prefijo en español según el tipo | `ActividadInicioSesion`, `FragmentoMisCitas`, `ModeloVistaPedirCita`, `AdaptadorCitas` |
| Métodos | Verbo en español, en `camelCase` | `pedir`, `confirmar`, `marcarAsistencia`, `estaOcupado` |
| Variables y parámetros | Español, `camelCase`, con sentido | `horaFinal`, `citasActivas`, `cliente` (no `c`, no `tmp`) |
| Constantes | `MAYUSCULAS_CON_GUION_BAJO` en español | `MAXIMO_INTENTOS`, `DIAS_DE_CADUCIDAD` |
| Paquetes | Español, en minúsculas | API: `http`, `servicio`, `repositorio`, `seguridad`, `modelo`. App: `ui.acceso`, `ui.cliente`, `ui.admin`, `modelovista`, `datos.remoto`, `datos.repositorio`, `modelo`, `utilidades` |
| Tests | Frase que dice lo que se comprueba | `pedirUnaHoraOcupadaDevuelveConflicto()` |

Sin tildes ni `ñ` en los identificadores (`contrasena`, `sesion`, `anio`): Java las admite, pero dan problemas con algunas herramientas de Windows. En los textos y comentarios sí van con tilde.

**Lo que se queda en inglés** es lo que no es nuestro: las clases del JDK y de Android (`Activity`, `Fragment`, `ViewModel`, `RecyclerView`, `HttpExchange`, `LocalDate`, `PreparedStatement`...), las anotaciones (`@Override`) y las palabras reservadas. Nuestras clases pueden extenderlas y usarlas, pero se llaman en español.

## 2. Comentarios

Los comentarios explican **por qué**, no repiten lo que ya dice la línea. Se escriben en español natural, como los escribiría alguien que explica su trabajo: impersonal o en plural («aquí comprobamos...», «lo hacemos así porque...»), y a veces en primera persona cuando es una medición o una decisión suya («en mi ordenador tarda...»).

Qué se comenta siempre:

- **Cada clase**, con un Javadoc corto: qué hace, y la decisión de diseño más importante.
- **Las reglas de negocio**, con la regla en palabras (`// el cupo cuenta pendientes y confirmadas, no las canceladas`).
- **Las decisiones no evidentes** y la alternativa descartada (`// no usamos equals(): ...`).
- **Las trampas**, marcadas con `OJO:`, sobre todo si salieron de un problema real. Si el problema está en el diario de desarrollo, se menciona.
- **Los números que no son obvios** (por qué 600 000 iteraciones, por qué 8 hilos).

Qué no se comenta:

- Lo que se lee sin esfuerzo (`// suma uno al contador`).
- Código muerto: se borra, no se deja comentado.
- Cabeceras decorativas, firmas con rayas o bloques de licencia inventados.

Ejemplo, de `Contrasenas.java`:

```java
// OJO: no comparamos con equals(). Un equals normal se para en el primer byte
// distinto, y midiendo cuánto tarda se podría ir adivinando la huella.
// MessageDigest.isEqual tarda siempre lo mismo.
return MessageDigest.isEqual(huellaNueva, huellaGuardada);
```

## 3. Estilo

- Métodos cortos, que hagan una cosa. Si un método pasa de unas 40 líneas, se divide.
- Sangría de 4 espacios, llaves siempre (aunque el `if` tenga una sola línea), líneas de unos 100 caracteres como máximo.
- Sin patrones ni capas que no se necesiten, sin librerías de inyección de dependencias, sin genéricos rebuscados. Si no se puede explicar en una frase, se simplifica.
- Los mensajes de error y los textos de la interfaz van en español; los códigos de error de la API (`hueco_ocupado`, `dia_lleno`) también, en `snake_case`.
- Cada decisión que se aparte de lo que enseña el temario se anota como **ampliación justificada** (ver plan §9).
