# DECISIONES.md — Registro de decisiones de arquitectura (ampliación 3D)

Este archivo es la **fuente de verdad de las decisiones de diseño** del TP de ampliación
a 3D. **Consultarlo antes de cualquier cambio arquitectónico** y **registrar acá toda
decisión nueva o modificación de una existente** (ver la regla en `CLAUDE.md`).

Formato de cada entrada: fecha, contexto, decisión, alternativas descartadas y motivo.

---

## D1 — Representación de la coordenada `z`: híbrido `Vec3` + `Vec2` planar

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 1 (cambio base `Vec2`→`Vec3` + `AgentState.z`)

**Contexto.** El simulador es 2D porque el tipo base de posición/velocidad es `core/Vec2`
y se usa en cascada en todos los módulos (835 ocurrencias en 84 archivos). La física del
CPM (`CpmOperationalModel`) es íntegramente planar: construye `new Vec2(state.x(), state.y())`
y opera todas las fuerzas en el plano; los muros son 2D.

**Decisión.** Enfoque **híbrido**:
- Se crea `core/Vec3(x,y,z)` como **tipo de posición/velocidad**. `AgentState.position()`
  devuelve `Vec3`.
- **`Vec2` NO se elimina**: sigue siendo el tipo planar de toda la dinámica horizontal y la
  geometría de muros. La física CPM lee la posición planar con `Vec3.xy()` (proyección) y
  trabaja en `Vec2`.
- `Vec3.distanceTo` es euclídea 3D (heurística/costos del A* multiplanta);
  `Vec3.horizontalDistanceTo` ignora `z`.

**Alternativas descartadas:**
- *Reemplazo total `Vec2`→`Vec3`*: la física pasaría a arrastrar `z` en cada fuerza. Más
  invasivo y arriesgado (la `z` se movería por fuerzas planares por error).
- *Solo campos escalares `z`/`vz`*: sin tipo `Vec3`; mínima fricción de tipos pero la
  "integralidad" queda implícita y la heurística 3D del A* se arma a mano.

---

## D2 — Sin velocidad vertical `vz`: la `z` se interpola en la escalera

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 1 (y se aplica en el 6, CPM en escaleras)

**Contexto.** Al ampliar a 3D hay que decidir si la velocidad tiene componente vertical.

**Decisión.** **No se modela `vz`.** La `z` **no es un grado de libertad dinámico**: se
actualiza por **interpolación del progreso del agente a lo largo del tramo de escalera**
(`z = lerp(z0, z1, progreso_xy)`), no por integración de fuerzas verticales.
- `AgentState` mantiene velocidad planar (`vx, vy`); no se agrega `vz`.
- `AgentState.setPosition(x, y)` (2 args) **conserva la `z`** a propósito — es la que llama
  el CPM cada `dt`. La `z` solo la cambian `setZ` / `setPosition(x, y, z)` (3 args) en la
  escalera.

**Alternativas descartadas:**
- *`vz` completo*: agregar componente vertical real a la velocidad e integración vertical.
  El enunciado no lo pide y complica el CPM.

**Motivo.** El CPM es planar; la dinámica vertical no aporta al modelo y agrega riesgo.

---

## D3 — Geometría: una "planta" `z` por elemento (no `z` por extremo)

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 2 (input deja de descartar `z`)

**Contexto.** Los CSV de geometría (`WALLS`, `EXITS`, `GENERATORS`, `TARGETS`, `SERVERS`)
traen `z1` y `z2` por extremo, hoy parseados y descartados. En geometría plana `z1==z2`
(todos los puntos de un muro/salida están en la misma planta).

**Decisión.** Cada elemento de geometría lleva **una única coordenada de planta `z`**
(un `double`), conservando su forma **planar en `Vec2`**:
- `Wall(Vec2 p1, Vec2 p2, double z)`, `Exit(blockName, Segment, double z, …)`,
  `Location(blockName, Shape, double z, …)`, `GeneratorZone(…, double z)`,
  `ServerZone(…, double z)`.
- Al leer el CSV: si `z1==z2` se usa ese valor; si difieren, **warning** y se toma `z1`
  (los elementos planos no deben tener `z1≠z2`; las escaleras se declaran aparte, ver D4).

**Alternativas descartadas:**
- *Endpoints en `Vec3` (z por extremo)*: permitiría muros inclinados pero rompe la
  planaridad de toda la geometría y complica el CIM por planta y el anti-tunneling 2D del CPM.

**Motivo.** Coherente con D1: un muro/salida pertenece a una planta; la dinámica y la
detección de vecinos operan por planta.

---

## D4 — Escaleras: nuevo `STAIRS.csv`, eje con `z` por extremo + ancho

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 2 (definir y parsear escaleras)

**Contexto.** La escalera es el elemento nuevo que conecta plantas. Hay que decidir cómo
se declara en los escenarios.

**Decisión.** Nuevo archivo **`STAIRS.csv`** (los CSV de geometría existentes quedan
intactos). Cada fila = el **eje** de la escalera como segmento con `z` por extremo:

```
block_name, x1,y1,z1, x2,y2,z2, width[, speed_factor]
MAIN, 10,5,0.0, 10,9,3.0, 2.0, 0.5
```

- Extremo 1 = **pie** (en planta `z1`); extremo 2 = **tope** (en planta `z2`), con `z1≠z2`.
- El agente recorre el eje proyectado `(x,y)` e **interpola** `z = lerp(z1, z2, avance_xy/largo_xy)`.
- `width` = ancho de la escalera. `speed_factor` opcional = factor de velocidad reducida
  (default a una constante global; se usa en el paso 6, CPM). Reusa el formato de 6 columnas
  de geometría (la `z` por extremo recién acá tiene `z1≠z2`).
- Nuevo tipo `core/Stairs`; `Geometry` expone `List<Stairs> stairs()`.

**Alternativas descartadas:**
- *Rectángulo footprint + `z_from/z_to`*: ambiguo qué borde es pie y cuál tope.
- *Dos landings explícitos*: redundante con el eje (mismo contenido, más columnas).

---

## D5 — `Geometry` por planta: consultas `default` keyed por `z` (double)

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 3 (Geometry expone plantas + escaleras)

**Contexto.** El grafo (paso 4) y el CIM (paso 5) operan **por planta**. La geometría ya
expone los elementos con su `z` (D3) y las escaleras (D4); falta una API cómoda para
consultarlos por planta.

**Decisión.** Una planta se identifica por su **valor `z` (double)**, con tolerancia
`Geometry.FLOOR_EPS = 1e-6` para comparaciones/dedup. Las consultas se agregan como
**métodos `default` en el puerto `Geometry`** (las heredan `GeometryImpl` y `StubGeometry`
sin tocarlas):
- `List<Double> floors()` — niveles de planta distintos presentes (de walls/exits/locations/
  generators/servers + extremos de escaleras), ordenados ascendentemente y deduplicados por eps.
- `wallsOn(z)`, `exitsOn(z)`, `locationsOn(z)`, `generatorZonesOn(z)`, `serverZonesOn(z)` —
  elementos cuya planta coincide con `z` (±eps).
- `stairsAt(z)` — escaleras que tocan la planta `z` en su pie **o** su tope (para que el
  grafo una los grafos por planta a través de ellas).

**Alternativas descartadas:**
- *Índice entero de planta (0,1,2…)*: agrega una capa de mapeo índice↔z; los datos ya viven
  en `z`. Si hiciera falta, se deriva de `floors()`.
- *Clase `Floors` separada que precompute*: los consumidores arman sus estructuras una vez en
  init (Geometry es read-only post-init), así que filtrar on-demand alcanza. `GeometryImpl`
  puede overridear para precomputar si la performance lo pide.

**Nota.** `floorOf(z)` para una `z` continua (agente a mitad de escalera) es un concepto de
**runtime** (qué grilla del CIM le corresponde); se resuelve en el paso 5, no acá.

---

## D6 — Grafo 3D: footTarget a `Vec3` en el pipeline + grafo por planta unido por escaleras + cableado desde `Geometry`

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 4 (Graph 3D)

**Contexto.** Hoy el `footTarget` es `Vec2` de punta a punta (StateMachine → PreOM → OM) y
el grafo es uno solo (una planta), con nodos `Vec2`, A* con heurística 2D, y se arma en App
re-parseando `WALLS.csv`/`SERVERS.csv` (descartando `z`), sin pasar por `Geometry`. Para
rutear entre plantas el grafo necesita la planta (`z`) del agente **y del target**; la del
target solo la conoce la StateMachine (arma el footTarget desde un Location/Exit/Server con `z`).

**Decisión.**
- **`footTarget` pasa a `Vec3`** en el pipeline del agente: `StateMachine.currentFootTarget()`,
  `PreOM.activate(Vec3)`/`resolvedFootTarget():Vec3`/`onServerTarget(Vec3)`,
  `OperationalModel.integrate(..., Vec3 footTarget, ...)`. El OM proyecta con `footTarget.xy()`
  para la física planar (sin cambio de comportamiento 2D).
- **`Graph.nextVisibleHop(Vec3 agentPosition, Vec3 target) : Vec3`** (floor-aware).
- **Grafo interno 3D:** los nodos del `NavigationGraph` pasan a `Vec3`; el A* usa
  `Vec3.distanceTo` (heurística euclídea 3D, como pide el enunciado). Los costos de arista
  intra-planta son la distancia planar (= 3D, misma `z`); las aristas de escalera tienen el
  costo del tramo inclinado (distancia 3D pie↔tope).
- **Grafo por planta unido por escaleras:** se corre el generador automático
  (`GridNodeReducer`) **una vez por planta** (con las paredes de esa planta, `wallsOn(z)`), y
  luego se **unen** los subgrafos agregando, por cada escalera, nodos en el pie y el tope y una
  arista entre ellos (`stairsAt`). La visibilidad y el FVP son **por planta** (un agente no
  "ve" nodos de otra planta salvo a través de la arista de escalera).
- **Cableado desde `Geometry`:** se implementa `GraphBuilder.fromGeometry(Geometry)` (hoy lanza
  `UnsupportedOperationException`) y App pasa a construir el grafo desde `Geometry` (única
  fuente con `z` + escaleras + `floors()`), en vez de re-parsear los CSV.

**Queda para el paso 6 (no en el 4):** la **física de z en la escalera** (interpolar
`z = lerp` por progreso y aplicar `speedFactor`). En el paso 4 el ruteo en `xy` ya recorre la
huella de la escalera; la `z` del agente todavía no se actualiza al subir.

**Alternativa descartada:**
- *Pipeline en `Vec2` + `z` por params en el puerto Graph* (`nextVisibleHop(Vec2 xy, double z, …)`):
  menos cascada ahora pero deja el puerto más feo y el footTarget igual debe pasar a `Vec3` en
  el paso 6 (trabajo duplicado).

**Ejecución en 2 fases (build verde en cada una):**
- *Fase A* — plumbing `Vec3` en el pipeline (puertos + impls + stubs + servers + AgentImpl). La
  `z` del footTarget se adjunta en el getter `StateMachine.currentFootTarget()` usando
  `agentState.z()` (en una sola planta = 0, comportamiento 2D idéntico); el OM proyecta `.xy()`.
  El grafo sigue siendo de una planta internamente (StubGraph adapta `Vec3↔Vec2`).
- *Fase B* — internals 3D del grafo (generación por planta, cosido por escaleras, heurística 3D,
  `fromGeometry`, App desde Geometry) + la `z` del footTarget pasa a salir del **target real**
  (`Task`/`TaskStep` cargan la planta del destino), no de `agentState.z()`.

---

## D7 — Eliminar SFM (`SfmaOperationalModel`); usar siempre CPM

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 4 (limpieza durante el cascade Vec3)

**Contexto.** El `CLAUDE.md` (regla 3) ya autoriza ignorar/eliminar SFM y usar siempre CPM. Al
pasar `OperationalModel.integrate` a `Vec3` (D6), `SfmaOperationalModel` y su test (~30 call
sites) sumaban cascada por un modelo que no se usa.

**Decisión.** Se eliminan `agent/om/SfmaOperationalModel.java` y
`SfmaOperationalModelTest.java`. App usa **siempre CPM** (el default de `om` pasa de `sfm` a
`cpm`; cualquier valor no-`cpm` cae igual a CPM). Queda `StubOperationalModel` (placeholder del
puerto, sin lógica) y `CpmOperationalModel` como único modelo real.

**Motivo.** Alinea con la regla 3, baja el churn del cascade y corrige el default a CPM.

---

## D8 — CIM por planta: facade `FloorAwareNeighborsIndex` + puente brute-force por escalera + ids de pared globales

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 5 (CIM por planta)

**Contexto.** El `CimNeighborsIndex` es **una sola grilla 2D** `(x,y)`: indexa paredes
(`neighbors.Wall`, 2D) en el ctor y agentes vía `update`, y devuelve vecinos por **distancia
planar**. App lo cablea aplanando **todas** las paredes de **todas** las plantas a una lista
2D (`toNeighborsWalls`), perdiendo la `z`. El enunciado pide que **la detección de vecinos sea
independiente por planta, salvo en las escaleras**, y que las paredes (punto más cercano) y sus
vértices se sigan detectando como vecinos. El puerto `NeighborsIndex` ya recibe `AgentState`
(que ya tiene `z()`), así que no hace falta tocar el puerto.

**Restricción dura encontrada.** El `Neighbor` de tipo `WALL` lleva payload `null` y un `id` que
es el **índice de la pared**; el `CpmOperationalModel` resuelve `walls.get(neighbor.id())` contra
**su propia lista** (la misma lista plana que App le pasa hoy en el ctor). Es decir, **el id de
pared es un espacio global compartido entre índice y OM**. Si cada grilla por planta usara
`wallsOn(z)` con ids locales, esos ids dejarían de coincidir con la lista del OM y romperían la
repulsión de paredes (incluso `IllegalStateException` en CPM:535).

**Decisión.**
- **Facade nuevo `environment/neighbors/FloorAwareNeighborsIndex implements NeighborsIndex`**;
  `CimNeighborsIndex` queda **intacto** (sigue siendo la grilla 2D de una planta).
- **Una `CimNeighborsIndex` por planta** de `geometry.floors()`, cada una con `wallsOn(z)`.
- **Visibilidad en escalera = índice "puente" por escalera** (brute-force, pocos agentes): cada
  escalera tiene su propio bucket de agentes. **Acople en los descansos:** un agente *en planta*
  consulta su grilla **+** los puentes de las escaleras que aterrizan en esa planta; un agente
  *en escalera* consulta su puente **+** las dos grillas de planta de sus extremos. Los conjuntos
  de agentes de cada contenedor son **disjuntos** (un agente vive en exactamente uno) → no hace
  falta deduplicar.
- **Clasificación runtime (`floorOf`, el pendiente que D5 dejó para este paso):** si `z` ≈ una
  planta discreta (±`FLOOR_EPS`) → esa planta; si `z` está **entre** plantas → escalera cuyo
  footprint (proyección sobre el eje `axisXy()` con `perp ≤ width/2` y `t∈[0,1]`) contiene `(x,y)`
  y cuyo rango `z` la abarca; fallback a planta más cercana. (En pisos planos la `z` del agente
  siempre coincide con una planta; sólo el avance por escalera la pone entre plantas.)
- **Espacio de ids de pared global:** el facade construye **una lista global de paredes**
  (concatenación determinística de `wallsOn(z)` sobre `floors()`), guarda un mapa `local→global`
  por planta y **reescribe** el `id` de cada vecino `WALL` (record inmutable → nuevo `Neighbor`)
  de local a global antes de devolverlo. App pasa `FloorAwareNeighborsIndex.globalWalls(geometry)`
  al `CpmOperationalModel` (misma lista/orden) para que los ids resuelvan. Los puentes no tienen
  paredes propias (paso 5): el agente en escalera obtiene las paredes de los descansos vía las dos
  grillas de planta.
- **Distancia planar** en todos los contenedores (igual que el CIM actual), incluido el puente.

**Paridad 1 planta.** `floors()=[z]` → una grilla con todas las paredes, mapa identidad, sin
escaleras: comportamiento idéntico al CIM 2D de hoy (la lista global = `geometry.walls()` en su
orden original, igual que la que recibía el OM).

**Alternativas descartadas:**
- *Snap a la planta más cercana (sin puente)*: lo más simple, pero dos agentes en la misma
  escalera cerca del punto medio caen en grillas distintas y **no se ven** (discontinuidad). El
  enunciado pide explícito el caso escalera → se eligió el puente.
- *Modificar `CimNeighborsIndex` para tener las grillas por planta adentro*: acopla la lógica de
  plantas a la grilla y reescribe la clase 2D ya andando; se prefirió el facade (menor riesgo de
  regresión en el caso 2D).
- *Grilla 3D única*: las celdas verticales no aportan (los pisos son planos y la separación entre
  plantas es discreta); complica sin beneficio.

**Pendiente para el paso 6 (no en el 5):** el `CpmOperationalModel` recibe hoy la lista global de
paredes para su anti-tunneling, pero **filtra por planta del agente** queda para el paso 6 (paredes
de la planta actual). Posible cleanup futuro: que el `Neighbor` de pared lleve la geometría en el
payload y el OM deje de depender de un espacio de ids compartido.

---

## D9 — CPM 3D: OM cableado desde `Geometry` (paredes por planta + escaleras), velocidad reducida e interpolación de z en la escalera

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 6 (CPM: z en escaleras + velocidad reducida + paredes de la planta actual)

**Contexto.** El `CpmOperationalModel` recibía sólo una `List<Wall>` (global, plana, sin `z`) y
la usaba para todo: (a) resolver el `wallId` de los vecinos `WALL` (`walls.get(id)`), y (b) el
anti-tunneling geométrico (`moveWithWallCheck`/`isStepClear`/`nearestWall`, que iteran **todas**
las paredes). El enunciado pide que el anti-tunneling use **sólo las paredes de la planta actual**,
que en la **escalera la velocidad sea menor**, y que al subir se **actualice la `z`** interpolando
por el avance. El OM no conocía plantas ni escaleras.

**Decisión.**
- **Nuevo factory `CpmOperationalModel.fromGeometry(Geometry)`** (App lo usa). Precomputa:
  - `floorWalls` = paredes **por planta** (`wallsOn(z)` por cada `floors()`), `floorLevels` paralelo;
  - lista **global** de paredes = concatenación de `wallsOn(z)` sobre `floors()` — **mismo orden/ids
    que `FloorAwareNeighborsIndex` (D8)**, para que `walls.get(neighborId)` resuelva;
  - `stairs` (de `geometry.stairs()`) y, por escalera, la **unión de paredes de las dos plantas que
    conecta** (anti-tunneling del agente sobre la escalera, cerca de cualquiera de los dos descansos).
  - Se **conserva** el ctor `CpmOperationalModel(List<Wall>)` para 1 planta / tests: sin info de
    plantas ni escaleras → anti-tunneling sobre la lista global y sin física de escalera
    (comportamiento idéntico al 2D previo).
- **Resolución de id de pared:** sigue contra la lista **global** (`this.walls`); esos vecinos ya
  vienen filtrados por planta desde el CIM (D8). **Sólo** el anti-tunneling geométrico pasa a usar
  las paredes de la planta actual (`floorWalls.get(floorOf(z))`), o la unión de las dos plantas si
  el agente está sobre una escalera.
- **Detección "sobre escalera" (runtime):** `locateStair(state)` = primera escalera cuya `z` el
  agente tiene **estrictamente entre** los dos niveles del tramo (±`FLOOR_EPS`) y cuya **huella**
  (`Stairs.containsXy`) contiene `(x,y)`. No necesita `floors()`: usa los extremos de la propia
  escalera (coinciden con las plantas que conecta). En el pie/tope exactos el agente es de planta.
- **Velocidad reducida:** sobre la escalera, `speedScale = stair.speedFactor()` multiplica la
  velocidad deseada (en `desiredVelocity` y en el branch LEAVING). El escape por contacto (`ve`) y
  el snapping de cola quedan sin escalar (seguridad física / no aplican en escalera).
- **Interpolación de z (D2):** el `integrate` público es un wrapper que (1) detecta la escalera,
  (2) corre la dinámica **planar** (`integratePlanar`, el cuerpo de antes) y (3) si el agente está
  sobre una escalera, setea `z = stair.zAt(x, y) = lerp(foot.z, top.z, avance_planar)`. La `z` no es
  grado de libertad dinámico: surge del progreso `(x,y)` sobre el eje.
- **Primitivas reutilizables en `core/Stairs`:** `containsXy`, `progressAt`, `zAt` — las usan tanto
  el OM como el `FloorAwareNeighborsIndex` (se eliminó la geometría duplicada `footprintPerp` del
  facade; ahora clasifica con `containsXy`).

**Alternativas descartadas:**
- *Taggear `neighbors.Wall` con `z` y filtrar la lista global por planta en runtime*: evita la
  dependencia de `Geometry` pero el OM **igual** necesita las escaleras (speedFactor + z) → habría
  que pasarlas aparte; `fromGeometry` es la fuente única más limpia.
- *`vz` / integración vertical*: descartado en D2; la `z` se interpola, no se integra.
- *Anti-tunneling del agente en escalera contra una sola planta*: se eligió la **unión** de las dos
  plantas para no tunelear los muros de ninguno de los dos descansos al entrar/salir del tramo.

**Motivo.** Cumple el enunciado (anti-tunneling por planta, velocidad reducida e interpolación de z
en la escalera) manteniendo CPM como único modelo y el espacio de ids de pared consistente con D8.

---

## D10 — Output con `z`: columna nueva tras `y` (`tout; x; y; z; vx; vy; state; id`)

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 7 (output con z + animación 3D)

**Contexto.** El output era `tout; x; y; vx; vy; state; id` (sep `; `, sin header, `Locale.US`).
No tenía `z`, así que la animación no puede ubicar al agente en su planta. Hay que agregar `z`.
Los consumidores (scripts `tools/visualize_simulation.py`, `tools/animate_run.py`) parsean por
**índice de columna**.

**Decisión.** Se inserta `z` **inmediatamente después de `y`**:
`tout; x; y; z; vx; vy; state; id`. Agrupa la posición 3D `(x,y,z)` y deja la velocidad planar
`(vx,vy)` junta. Se actualizan los dos scripts Python (que de todas formas debían cambiar para la
vista 3D). No hay test Java que fije el formato (el test del driver usa un `OutputSink` propio); se
agrega uno nuevo que verifica la fila.

**Alternativas descartadas:**
- *Agregar `z` al final tras `id`* (`…; state; id; z`): no rompe los índices 0-6 de consumidores
  viejos, pero separa `z` de `x,y` (la posición queda partida) y rompe igual el invariante "id al
  final". Como controlamos todos los consumidores, se prefirió el orden canónico.
- *`vz` en el output*: no se modela `vz` (D2); sólo se agrega `z`.

**Motivo.** Representación natural de la posición 3D; el costo de actualizar los scripts es bajo y
ya era necesario para la vista 3D.

