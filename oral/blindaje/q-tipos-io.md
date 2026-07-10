# Blindaje — Tipos base, Input/Output y Reproducibilidad

Q&A de defensa oral sobre la raíz del cambio a 3D: los tipos de posición/velocidad, cómo
entra la coordenada `z` por el input, cómo sale por el output, y la reproducibilidad. Cada
respuesta cita el código real (`archivo:línea`), el [informe](../../informe/informe.tex) o la
decisión ([D1](../../.claude/DECISIONES.md)..[D23](../../.claude/DECISIONES.md)). Los
snippets verbatim están en [`../codigo/01-tipos-base-io.md`](../codigo/01-tipos-base-io.md).

---

### P: ¿Por qué crearon un `Vec3` y no reescribieron todo el simulador a 3D?
Porque la física peatonal es **planar** y la `z` sólo cambia en la escalera: migrar todo en
cascada arriesgaba que la altura se moviera por una fuerza horizontal. El híbrido (D1) usa
`Vec3` para posición/velocidad y **conserva** `Vec2` para la dinámica horizontal (puente
`Vec3.xy()` / `Vec2.withZ()`), y expone `distanceTo` euclídea 3D para el A* (`Vec3.java:41`)
y `horizontalDistanceTo` (`Vec3.java:46`) — es lo que recomienda el enunciado.

### P: ¿No es más limpio que todo sea `Vec3` y listo? ¿No quedó un tipo "de más"?
Es un trade-off consciente: `Vec3` es la posición del agente y `Vec2` el plano de la física,
cada uno con un rol nítido. La alternativa (todo `Vec3`) se descartó en D1 por invasiva y
riesgosa: 835 ocurrencias de `Vec2` en 84 archivos y una `z` colándose en cada suma de fuerzas.

### P: ¿Por qué no hay velocidad vertical `vz`?
Porque la `z` no es un grado de libertad dinámico (D2): en la escalera la altura está
**determinada** por el avance a lo largo del tramo, no acelera hacia arriba. Por eso
`AgentState` guarda `x, y, z, vx, vy` pero no `vz` (`AgentState.java:18-25`) y la `z` se
interpola: `zAt = foot.z + (top.z - foot.z)*progressAt` (`Stairs.java:90-92`).

### P: Entonces, ¿cómo se calcula exactamente la `z` de un agente en la escalera?
Se proyecta la posición planar sobre el eje de la escalera recortando a `[0,1]` (`progressAt`,
`Stairs.java:75-82`) y se interpola `z = lerp(foot.z, top.z, progress)` (`zAt`,
`Stairs.java:90-92`). Clave: el `setPosition(x,y)` de dos argumentos que llama el CPM cada paso
**conserva** la `z` a propósito (`AgentState.java:105-114`); sólo la cambia `setZ` o el
`setPosition` de tres argumentos.

### P: ¿Por qué la `z` va por elemento de geometría y no por extremo?
Porque un muro, salida o aula **pertenecen a una planta**: son planos (D3), y esa planaridad
la necesitan el CIM por planta y el anti-tunneling 2D. Los CSV traen `z1` y `z2` por
compatibilidad, pero para un elemento plano deben coincidir: `parseFloorZ` las colapsa y, si
difieren, avisa y toma `z1` (`CsvParse.java:24-34`). El único `z1≠z2` legítimo es la escalera.

### P: ⚠️ ¿Y si alguien pone `z1≠z2` en un muro por error? ¿Se rompe la simulación?
**⚠️ Punto débil (defendible).** No se rompe pero tampoco falla ruidosamente: `parseFloorZ`
sólo avisa por log y usa `z1` (`CsvParse.java:27-33`). Lo defendemos porque es un dato mal
formado (no un caso de uso) y tomar `z1` degrada razonable en vez de abortar; en cambio donde
`z1≠z2` sí importa —la escalera— el invariante es duro: el constructor tira excepción si
`foot.z == top.z` (`Stairs.java:32-35`) y el reader la re-emite con número de línea
(`StairsCsvReader.java:52-54`).

### P: ¿Por qué un `STAIRS.csv` nuevo y no un atributo o layer en `WALLS`?
Por dos razones (D4): no romper los CSV existentes, y la **inequívocidad** de saber qué extremo
es el pie y cuál el tope (un rectángulo con `z_from`/`z_to` es ambiguo). La representación como
eje —`block_name, x1,y1,z1, x2,y2,z2, width`, extremo 1 = pie, extremo 2 = tope— es mínima y
opcional: los escenarios de una planta no la tienen (`StairsCsvReader.java:25-66`).

### P: ¿Qué representa una fila de `STAIRS.csv` y cómo se calibra la velocidad?
Es el eje del tramo pie→tope, con ancho y un `speed_factor` opcional
(`StairsCsvReader.java:26-44`); si falta se usa `Stairs.DEFAULT_SPEED_FACTOR = 0.5`
(`Stairs.java:22-23`). En la Escuela la escalera mide 2.6 m de ancho y usa `speed_factor=0.38`,
dando ≈0.59 m/s. Cómo el CPM aplica el factor está en
[`../codigo/02-fisica-cpm.md`](../codigo/02-fisica-cpm.md).

### P: La escalera de la Escuela es un switchback (con descanso), pero `Stairs` es un solo tramo recto. ¿Cómo se concilia?
El switchback se modela como **dos** `Stairs` rectos encadenados por el descanso a media altura
(D19), reutilizando toda la maquinaria multiplanta (grafo, CIM, CPM) sin reescribir el núcleo.
El tipo `Stairs` es el ladrillo mínimo; la geometría compuesta se arma en el builder.

### P: ¿Por qué la `z` quedó después de `y` en el output y no al final?
Para agrupar la posición 3D `(x,y,z)` y dejar `(vx,vy)` junta: el formato es
`tout; x; y; z; vx; vy; state; id` (`OutputSinkImpl.java:59-67`, D10). Ponerla al final
partiría la posición y rompería el invariante "id último"; como controlamos todos los
consumidores (los scripts Python), no había costo en elegir el orden canónico. No se emite `vz`
porque no se modela (D2).

### P: ¿Cómo saben que los scripts de animación leen bien la nueva columna?
Los scripts parsean por índice de columna y se actualizaron junto con el formato, que fija
`OutputSinkImpl.formatRow` (`OutputSinkImpl.java:59-67`, sep `; `, `Locale.US`, sin header). Se
agregó un test Java nuevo que verifica la fila emitida; la suite queda en **143 tests, 0 fallos,
0 omitidos**.

### P: La semilla reproducible, ¿no rompe los tests o cambia los resultados históricos?
No: la semilla es **opt-in** (D23). Sin `-Dsimped.seed`, `Seeds.rng(salt)` devuelve un `Random`
sin sembrar (`Seeds.java:37-48`) y `Seeds.mixOr(fallback, salt)` devuelve la constante histórica
(`Seeds.java:62-72`), así que tests y corridas sueltas recaen en las secuencias de siempre.
Verificado: **143 tests, 0 fallos**, sin tocar ningún test existente.

### P: ¿Qué garantiza que dos réplicas del barrido sean realmente independientes?
Primero, que la semilla llega a **todos** los streams: antes tiempos de servicio y selecciones
usaban `new Random(0)` fijo, así que las réplicas repetían elecciones; D23 lo propaga vía
`mixOr` (`Seeds.java:62-72`). Segundo, que el `^ salt.hashCode()` (`Seeds.java:42` y `:67`)
descorrelaciona streams con salts distintos. Todos los resultados usan 5 realizaciones
(semillas 1–5) por punto.

### P: ⚠️ ¿La semilla garantiza reproducibilidad bit a bit entre máquinas distintas?
**⚠️ Punto débil (honesto).** Garantizamos determinismo dado un mismo binario/JDK (mismo
escenario + misma `simped.seed` ⇒ mismo output, porque todo `Random` se siembra desde
`simped.seed ^ salt.hashCode()`), no reproducibilidad bit a bit entre JVMs o arquitecturas. Para
el objetivo del TP —réplicas independientes y corridas re-ejecutables— es suficiente.

### P: ¿Qué es el bug de los "defaults `z=0`" y cómo lo encontraron?
Al correr el primer escenario real de dos plantas **ningún agente subía**, sin error en runtime
(D11, D16, D18): varios caminos de datos perdían la `z` en silencio con un default `z=0`. En IO
fueron los **constructores de conveniencia** de `GeometryAssembler`, que fijaban `z=0`, hundiendo
las aulas-server de P1 (`GeometryAssembler.java:153-158`) y los spawns de P1
(`GeometryAssembler.java:86-87`) pese a que el reader sí parseaba la `z`. Lo hallamos depurando
el síntoma "`maxz=0`", comparando el grafo aislado (que cruzaba de planta) contra la simulación.

### P: ¿Cómo lo arreglaron y qué lección sacaron?
Se propagó la planta de punta a punta: `serverRow.z()` al `ServerZone`
(`GeometryAssembler.java:157-158`) y `row.z()` al `GeneratorZone` (`GeometryAssembler.java:87`).
La lección: al agregar una coordenada hay que auditar **todos** los caminos que copian o
reconstruyen estado, porque los defaults silenciosos producen simulaciones que corren pero son
erróneas — y los tests por módulo no reemplazan un escenario de integración multiplanta real.

### P: ⚠️ Si los tests unitarios estaban todos verdes y aun así fallaba, ¿no falta cobertura?
**⚠️ Punto débil (reconocido, con mitigación).** Sí: el gap era de **integración** y los tests
por módulo cargaban una sola planta o mockeaban el vecino. Lo mitigamos con (1) el escenario
Escuela como test de integración de facto (~60% sube a P1 y baja, D11) y (2) cada fix
documentado en su decisión (D11/D16/D18) para reconocer el patrón si reaparece.

---

## Navegación

- Snippets verbatim de este tema: [`../codigo/01-tipos-base-io.md`](../codigo/01-tipos-base-io.md)
- Física del CPM (uso de `Stairs`, velocidad reducida, interpolación de `z`): [`../codigo/02-fisica-cpm.md`](../codigo/02-fisica-cpm.md)
- Resumen de la implementación: [`../resumenes/02-implementacion.md`](../resumenes/02-implementacion.md)
- Decisiones de arquitectura: [`../../.claude/DECISIONES.md`](../../.claude/DECISIONES.md) (D1, D2, D3, D4, D10, D11, D16, D18, D23)
