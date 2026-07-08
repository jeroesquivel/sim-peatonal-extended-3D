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

---

## D11 — Ruteo multiplanta end-to-end: 4 fixes que expuso el escenario Escuela

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 8 (escenario Escuela; cierra integración de pasos 4–6)

**Contexto.** Al correr el primer escenario real de 2 plantas (Escuela), **ningún agente subía a
P1** (`maxz=0`) pese a que geometría, plan y grafo se cargaban bien por separado. La depuración
(grafo aislado vs. simulación completa) reveló **cuatro** gaps de integración del soporte 3D, cada
uno enmascarando al siguiente:

**Decisión (fixes aplicados).**
1. **`Task` location-group multiplanta** (`core/Task`, `scenario/AgentAssembler`): la `z` del Task
   se tomaba del **primer** candidato del grupo (`candidates.get(0).z()`). Un grupo "AULA" con aulas
   en PB y P1 quedaba clavado en `z=0`. → El Task ahora lleva la **planta por candidato**
   (`candidateZs`) y `z()` devuelve la del candidato **resuelto**.
2. **Selección por índice** (`core/LocationTargetSelector`, `core/Task`, `agent/statemachine`):
   las aulas de PB y P1 comparten `(x,y)` (una sobre otra), así que los candidatos `Vec2` tienen
   **duplicados** y `indexOf(resuelto)` colapsaba la planta a la primera (PB). → Se agrega
   `chooseIndex(...)` que devuelve el **índice** elegido y `Task.resolveLocationTarget(int)` resuelve
   la planta por índice (sin ambigüedad de `(x,y)`).
3. **Grafo: cruce de la arista de escalera** (`environment/graph/NavigationGraph`): `nextVisibleHop`
   ruteaba hasta el **pie** de la escalera y se quedaba ahí (el tope, en otra planta, "no es visible",
   y el FVP no avanzaba → hop == pos, estancado); además un agente **ya sobre** la escalera no veía
   ningún nodo. → (a) el FVP **avanza al tope** cuando el agente llega al pie (`STAIR_FOOT_REACH`); (b)
   un agente sobre la huella de una escalera (z entre plantas) recibe como hop el **extremo hacia la
   planta del target** (`stairTraversalHop`). Se precomputan aristas de escalera (nodos en plantas
   distintas) y niveles de planta.
4. **OM: arranque de la interpolación de z** (`agent/om/CpmOperationalModel`): `locateStair` exigía
   `z` **estrictamente entre** plantas, pero al pisar el pie el agente está en `z=0` (= nivel de
   planta) → no se detectaba "en escalera" y la `z` nunca arrancaba (el agente cruzaba la huella a
   `z=0`). → `locateStair(state, footTarget)` detecta "recorriendo" si está sobre la huella **y** (su
   `z` ya está entre plantas **o** su `footTarget` apunta a otra planta). El footTarget cruzando de
   planta distingue "subir/bajar" de "cruzar la huella en horizontal".

**Resultado.** En la Escuela (~100 agentes, plan AULA→EXIT con aulas en ambas plantas), ~60% resuelve
un aula de P1, sube por una escalera (z interpolada 0→3), asiste la clase y baja a una salida.

**Motivo.** Los pasos 4 (grafo), 5 (CIM) y 6 (CPM) estaban verdes en aislamiento pero su integración
multiplanta recién se ejercitó con un escenario real de 2 plantas; estos 4 fixes la cierran.

---

## D12 — Escenario Escuela: Formato B + builder paramétrico

- **Fecha:** 2026-06-30
- **Estado:** vigente
- **Paso del plan:** 8

**Contexto.** Hay que construir el escenario Escuela (≥2 plantas, aulas, pasillos, escaleras, recreo
con kiosco) y, sobre él, los sub-escenarios Evacuación e Ingreso.

**Decisión.**
- **Formato B** (`parameters.json`) — como el `scenarios/ejemplo-aeropuerto` de referencia: habilita
  `exit_selection` (elegir salida), `objective_selection` (CLOSEST/RANDOM por grupo) y targets con
  `attending_time` (aulas como dwell), todo necesario para el escenario. Geometría en los CSV con `z`
  + `STAIRS.csv`; params/planes en JSON (dt = default, `output_delta_time`/`max_time` del JSON).
- **Builder paramétrico** `tools/scenarios-builders/build_escuela.py` (emite los CSV + JSON) en vez de
  escribir a mano ~55 paredes en 2 plantas; permite variar dimensiones/cantidades para los
  sub-escenarios (8.2) sin reescribir todo.
- **Geometría** (mapa cuadrado 60×60): recreo izquierda (`x 0..30`, z=0, kiosco en esquina + salida
  RECREO); edificio derecha (`x 30..60`, PB z=0 / P1 z=3) alargado en y, pasillo vertical central que
  separa 4 aulas a cada lado por planta (16 aulas), **dos escaleras** en las puntas (`y≈0` e `y≈60`)
  conectando PB↔P1, salida EDIFICIO_PB en PB, y comunicación PB↔recreo por las dos esquinas. La P1 no
  tiene salida propia (se evacúa bajando).
- **Aulas** = TARGETS con `attending_time` (dwell de clase). **Kiosco** = server `queue`
  (`KIOSCO_1_SERVER` + `KIOSCO_1_QUEUE000`). El plan baseline es AULA→EXIT (el kiosco queda en la
  geometría para los sub-escenarios, fuera del plan baseline para no ser un cuello de botella de un
  único server con todos los agentes).

**Pendiente (paso 8.2):** sub-escenarios Evacuación (N agentes adentro → distribución de tiempos de
evacuación) e Ingreso (caudal en 1/5/10 min → población vs tiempo antes de la escalera principal), con
sus barridos y gráficos.

---

## D13 — Baseline "día escolar" con generación FINITA (burst matinal) + puertas de aula holgadas

- **Fecha:** 2026-07-01
- **Estado:** vigente
- **Paso del plan:** 8.1 (afinado del baseline para una animación representativa)

**Contexto.** Al correr el baseline del D12 tal cual (`active_time=40, inactive_time=0`, `max_time=200`)
la animación mostraba dos problemas: (1) **el edificio nunca se vaciaba** —población ~82/84 al final,
sólo 18/100 evacuaban— y (2) **congestión severa** en el pasillo central. La causa de (1) es la
semántica del `ConfigurablePedestrianGenerator`: `(active_time, inactive_time)` definen un **ciclo que
se repite**; con `inactive_time=0` el generador **re-arranca indefinidamente** (`advancePhase` →
`startNewCycle`), es decir spawnea durante toda la simulación (~5 agentes cada 10 s por siempre). No
es un total de agentes sino un caudal perpetuo. Se validó (corrida de control con generación finita)
que con menos agentes **el ruteo multiplanta funciona de punta a punta**: el edificio se llena y se
vacía, y los agentes que suben a P1 bajan por las escaleras a una salida (≈100% de bajada). O sea, el
problema era de **configuración del escenario, no de los pasos 4–6**.

**Decisión.** El baseline pasa a modelar un **día escolar con llegada finita**:
- **Ventana de ingreso finita:** un único burst de llegada. En el builder (`build_escuela.py`):
  `ARRIVAL_WINDOW = 60 s`, `inactive_time = 1e6` (centinela ≫ `max_time` ⇒ el generador entra en
  INACTIVE tras el primer ciclo y no vuelve a spawnear). Caudal sin cambios (`period=3`,
  `quantity∈[1,2]` ⇒ ~30 p/min/entrada, recortado por ancho de puerta a 18 y 12) ⇒ **~30 agentes**.
- **`max_time = 240 s`** (antes 200): deja tiempo para que, tras la clase, el edificio **se vacíe**
  (evacuación completa como parte del baseline).
- **Puertas de aula holgadas: `DOOR = 2.4 m`** (antes 1.6). Mejora el flujo de evacuación y **mitiga
  parcialmente** el atasco en jamba descripto en **D14** (no lo elimina).

Resultado (≈30 agentes, `max_time=240`): 30/30 asisten clase, ~22 suben a P1 y **bajan por escalera**,
~21/30 evacúan y la población sube a 30 (t≈60–90) y baja a ~14 (t=200). Animaciones: vista 3D apilada
(`tools/visualize_simulation_3d.py`, stride 5) y 2D por planta (`tools/visualize_simulation.py --floor`,
ahora con `--stride`).

**Alternativas descartadas:**
- *Mantener generación perpetua*: el edificio nunca se vacía → no se puede mostrar la evacuación ni
  medir tiempos; además la congestión colapsa el throughput del único pasillo central.
- *Cambiar la semántica del generador (que `active_time` sea un total)*: tocaría un módulo estable con
  tests; el centinela `inactive_time` logra el burst finito sin tocar código.

**Motivo.** Da una animación con narrativa completa (ingreso → clase → evacuación por escaleras) y
prepara el terreno del 8.2 (el burst finito es justo el input "caudal" del sub-escenario Ingreso; una
ocupación inicial en aulas sería el de Evacuación).

---

## D14 — Atasco en jamba de puerta (livelock CPM): diagnóstico e intento de fix revertido

- **Fecha:** 2026-07-01
- **Estado:** diagnóstico vigente — **resuelto en D17** (contacto de pared a rmin)
- **Paso del plan:** 8.1 (comportamiento raro detectado en la animación)

**Contexto.** En la animación afinada (D13) ~1–2 de 30 agentes quedan **oscilando pegados a la pared del
pasillo (x=48), en la jamba de una puerta de aula**, tras la clase, sin llegar nunca a la salida
(WALKING con desplazamiento neto ~0 durante 60–90 s). Reproducible en esa geometría (puertas del lado
derecho), aunque *qué* agentes caen varía porque la generación usa `new Random()` sin seed.

**Diagnóstico (con `-Dsimped.hopLog` + inspección de `vx,vy`).** **No es un bug de ruteo:** el hop es
estable y visible (`visJava=1`) y apunta correctamente al pasillo-sur camino a la salida. La causa está
**aguas arriba, en el modelo de fuerzas del CPM**: el agente sale del aula hacia una salida lejana casi
**colineal con la pared**, así que su dirección deseada es casi paralela al muro y queda a ~0.2 m de la
jamba. Como `0.2 m < radius` (hasta `rmax=0.3`), el CPM lo clasifica como **contacto con pared** y aplica
la **velocidad de escape perpendicular** (lo aleja del muro, hacia el centro de la puerta); esa fuerza
**pelea contra el pull del target** (que lo trae de vuelta hacia la jamba) ⇒ la velocidad alterna entre
"escape" (aleja) y "target" (acerca) y el agente **oscila sin cruzar** el hueco.

**Intento de fix (revertido).** Se probó reforzar `CpmOperationalModel.moveWithWallCheck` (fan de escape
anti-deadlock y luego un "fan con score por proyección" que elige la dirección libre de menor desvío
respecto a la deseada, en vez del slide al sentido opuesto). **No resuelve** el caso: el atasco ocurre
*antes* del anti-tunneling —la velocidad de escape del contacto-pared ya es un paso válido (perpendicular,
hacia el hueco), así que `moveWithWallCheck` ni siquiera llega a sus fallbacks—. Como el cambio tocaba
código tuneado/testeado sin resolver el problema objetivo, **se revirtió** (el CPM quedó idéntico al
original; los 149 tests siguen verdes). La **mitigación parcial** aplicada es de escenario: puertas más
anchas (D13, `DOOR=2.4`), que bajan la incidencia pero no la eliminan.

**Opciones de fix real (a decidir con el usuario, para el paso de afinado):**
1. **Ruteo con waypoint en el centro de la puerta:** que el FVP no "vea a través" del hueco hasta un
   punto lejano casi-colineal con la pared, sino que primero apunte al centro del vano (cruce
   perpendicular) y recién después al destino. Ataca la causa (dirección deseada casi paralela al muro).
2. **Contacto-pared con umbral `rmin` (núcleo duro) en vez de `radius`/`rmax`:** que la pared cuente como
   "contacto" (y dispare escape) sólo cuando el cuerpo físico penetraría, no dentro del espacio personal.
   Es un cambio de calibración del CPM (afecta cuán cerca de los muros caminan los agentes).
3. **Aulas como server `CLASSROOM`** (ver pendientes de afinado): cambia la geometría/flujo de salida y
   podría evitar el patrón de salida colineal con la pared.

**Motivo de registrarlo.** Es el "comportamiento raro" pedido; el diagnóstico (contacto-pared vs. target
en jamba) es material directo para el informe y la elección del fix conviene consensuarla porque toca
física (CPM) o ruteo (Graph), no sólo el escenario.


---

## D15 — Aulas como server `CLASSROOM` + planes diferenciados PB/P1 + timbre único

- **Fecha:** 2026-07-01
- **Estado:** vigente
- **Paso del plan:** 8.1 (afinado de servers/planes)

**Contexto.** El baseline modelaba las aulas como **TARGETs capacidad-1** (un punto por aula, dwell
individual). Problemas: (a) un aula alojaba **un solo alumno** (irreal), y (b) como las aulas de PB se
saturaban primero, los agentes rebotaban a P1 → **sesgo ~77% a P1**. El código ya tiene el tipo de
server `CLASSROOM` ("recinto colectivo con sesiones; libera a todos los presentes en `t_init+t_mean`").

**Decisión.**
- **Cada aula = un server `CLASSROOM`** (rectángulo del recinto, sin filas `_QUEUE`, `type:"CLASSROOM"`
  explícito). 8 en PB (base `AULA_PB`, z=0) y 8 en P1 (base `AULA_P1`, z=3). Cada base es un **grupo
  lógico**: el módulo reparte a sus 8 miembros por softmax (carga+distancia). Los alumnos ya no son
  capacidad-1 → un aula aloja muchos.
- **Un base por planta** (no un único grupo `AULA` con ambas): evita el gotcha del foot-target de grupo
  (que toma la `z` del primer miembro). Para repartir población entre plantas se usan **planes
  diferenciados**: `CLASE_PB` (→ grupo `AULA_PB`) y `CLASE_P1` (→ grupo `AULA_P1`), ambos con
  `exit_selection: RANDOM` para la salida. El generador recibe el **pool** `"CLASE_PB|CLASE_P1"` y elige
  uno al azar por agente (~50/50). Esto **elimina el sesgo** (el reparto ya no depende de saturación) y
  cumple el pedido de "planes diferenciados".
- **Timbre único (una sesión).** Formato B sólo admite **una** sesión por classroom (`sessionStarts =
  [start_time]`): el aula libera a todos **una vez** en `start_time + t_mean`, y cualquier agente
  delegado **después** queda atrapado. Como el aula es el **primer** paso del plan, los alumnos se
  delegan al spawnear (dentro de la ventana de ingreso), así que con el **timbre después de esa ventana**
  (`start_time=0`, `t_mean=CLASS_SESSION=140` ⇒ dismissal en t=140, ventana de ingreso 0–60) **ninguno
  queda atrapado**. El modelo resultante es un **período de clase con dismissal sincronizado** (suena el
  timbre → todos evacúan a la vez), realista y una buena antesala del sub-escenario Evacuación.
- `max_time = 250` (para vaciar tras el timbre). Los alumnos figuran **`QUEUEING`** mientras están en el
  aula (es el estado que el engine asigna "en el puesto"); en la animación se ven como el color de
  QUEUEING dentro del recinto.

**Resultado.** ~30 alumnos, ~50/50 PB/P1: **13/30 suben a P1 (y bajan por escalera), 27/30 asisten**,
timbre limpio en t=140 (t=139: 29 en aula → t=142: 0), **29/30 evacúan**. Sin sesgo de planta, sin
agentes atrapados.

**Alternativas descartadas:**
- *Seguir con TARGETs capacidad-1*: irreal y con sesgo a P1.
- *Un único grupo `AULA` con las 16 aulas (ambas plantas)*: el foot-target del grupo colapsa a una
  planta (gotcha conocido) → se usan dos grupos + dos planes.
- *Múltiples sesiones / recreos* (varios timbres): requiere Formato A (`SERVER_PARAMS.csv`, una fila por
  `t_init`), incompatible con el `parameters.json` (Formato B) del escenario. Queda para el futuro si
  hace falta un día con varias clases.

---

## D16 — Migración 3D del módulo de Servers: `ServerZone.z` propagada + target fino en la planta del server

- **Fecha:** 2026-07-01
- **Estado:** vigente
- **Paso del plan:** 8.1 (habilitar aulas-server en P1; completa la migración 3D del módulo de servers)

**Contexto.** Al modelar las aulas como servers (D15), **ningún** agente de `CLASE_P1` subía a P1
(`maxz=0`). Diagnóstico (debug de targets + inspección): el **módulo de Servers nunca se migró a 3D** —
no hacía falta porque el único server (kiosco) estaba en PB y las aulas eran TARGETs (que sí son 3D).
Dos gaps encadenados:
1. **`GeometryAssembler.buildServers` tiraba la `z`**: construía el `ServerZone` con el overload de
   conveniencia que fija `z=0` (`ServerZone(base, id, area, queues, type, params)`), ignorando
   `serverRow.z()` (que el `ServersCsvReader` sí parseaba bien, =3 para P1). ⇒ todas las aulas quedaban
   en z=0.
2. **`ServersWiring` ubicaba el target fino en la `z` del AGENTE**: el relay del `TargetSink` (I13b)
   hacía `target.withZ(impl.state().z())` con un comentario "Fase A: una sola planta → z del agente". Un
   alumno de `CLASE_P1` caminando aún por PB (z=0) recibía el target del aula con z=0 → nunca subía.

**Decisión (fixes).**
- **`GeometryAssembler`**: pasar `serverRow.z()` al `ServerZone` (usar el ctor de 7 args). Ahora
  `ServerZone.z()` = planta real del server (3 para P1).
- **`ServersWiring`**: el target fino se ubica en la **planta del server delegado**, no en la del agente.
  El módulo (planar en `xy`) expone `delegatedServerId(agentId)`; el wiring arma un mapa `serverId → z`
  (de `ServerZone.z()`) y hace `target.withZ(zDelServer)` (fallback: `z` del agente). El
  `ServersModule` **sigue planar** — sólo el target fino recupera la planta; `steppedOn`/posiciones
  siguen en `xy` (la separación por delegación evita la ambigüedad PB/P1 que comparten `(x,y)`).

**Alternativa descartada.** *Migrar todo el módulo de Servers a `Vec3`* (`TargetSink`, modelo `Server`,
`positions`, tests): mucho más invasivo por una `z` que sólo hace falta en el target fino. El enfoque
elegido es mínimo (2 archivos de producción) y deja los 149 tests verdes.

**Motivo.** Completa la migración 3D para servers multiplanta (necesario para las aulas de P1) con el
menor churn posible.

---

## D17 — CPM: contacto con pared al núcleo duro (rmin), no al espacio personal (rmax)

- **Fecha:** 2026-07-01
- **Estado:** vigente
- **Paso del plan:** 8.1 (mejora del CPM pedida por el usuario; resuelve D14)

**Contexto.** El D14 diagnosticó el atasco en esquinas/jambas: el CPM trataba la pared como **contacto**
en cuanto el agente estaba dentro de su radio (que crece hasta `rmax=0.3`), y aplicaba la **velocidad de
escape perpendicular** (`escapeVelocity`, magnitud `ve`), que **ignora el objetivo** y lo empuja lejos
del muro. Cerca de una jamba, ese escape (perpendicular, ~1.5 m/s) alternaba con el pull del target
(hacia el hueco) → **oscilación en el lugar**. Un intento previo de arreglarlo en el anti-tunneling
(`moveWithWallCheck`) se revirtió porque la causa estaba aguas arriba (en las fuerzas), no en el
anti-tunneling.

**Decisión.** En `CpmOperationalModel.collectContactDirections`, la pared cuenta como **contacto**
(dispara la velocidad de escape) sólo cuando el **cuerpo físico** del agente la tocaría, es decir
`d ≤ rmin` (núcleo duro), en vez de `d ≤ radius` (espacio personal expandido, hasta `rmax`). En la franja
`rmin..rmax` la **repulsión suave de pared** (`n_w_c = Aw·exp(-d/Bw)`, rama de avoidance del A-CPM) ya
aparta al agente sin anular el pull al objetivo → **dobla esquinas y cruza puertas** en lugar de rebotar.
Es coherente con el criterio de contacto **anisotrópico** que el A-CPM ya usa para agente-agente (el
escape isotrópico duro sólo al radio mínimo). El **anti-tunneling** (`moveWithWallCheck`) queda intacto:
sigue impidiendo atravesar paredes, así que acercarse más al muro no genera tunneling.

**Resultado (escenario Escuela, mismo setup).** El "oscilar-en-la-esquina" (ventanas de ~3 s en estado
de movimiento con desplazamiento neto < 0.5 m) cayó de **20.1% → 5.5%** de las ventanas en movimiento
(**~3.6×**); los agentes atascados al final pasaron de **2/30 → 0/30**; y el tiempo total en estado de
"deambular" bajó ~15%. El 5.5% remanente es congestión legítima (esperar para entrar al aula / hacer
fila detrás de otros), no pegado a la pared. **149 tests verdes** (incluida la parte de anti-tunneling
del `CpmOperationalModelStairTest`).

**Alternativas descartadas:**
- *Reforzar `moveWithWallCheck`* (fan de escape / slide por proyección): ataca el síntoma, no la causa
  (la velocidad deseada ya venía perpendicular por el escape) — se había probado y revertido (D14).
- *Bajar `ve` global o el rango del avoidance de pared (`Aw`,`Bw`)*: descalibra el A-CPM en todos lados;
  el cambio de umbral de contacto es local y respeta la calibración publicada de la repulsión suave.

**Nota (2026-07-07) — dónde persiste el remanente.** Análisis de los 15 `output.csv` de los barridos
finales de Evacuación (N=40/80/120 × seeds 1–5, con D17 y D22 aplicados): quedan 1.4/1.6/1.4
no-evacuados por corrida en promedio, y **ninguno** (0/22) está en jambas de puerta de aula — ese
caso quedó efectivamente resuelto por esta decisión. El livelock del mecanismo D14 (oscilación
escape-vs-objetivo con desplazamiento neto ~0) **persiste en dos geometrías estrechas no cubiertas**:
la **boca de la escalera SUR en P1** (14/22, agente a ~0.2 m de la baranda del hueco en z=3.0, sin
iniciar el descenso) y la **jamba del portón norte de salida** en PB (7/22, x≈30.15, y≈53.6). El caso
restante (1/22, N=120 seed 1) es un borde del criterio operativo: todos evacuaron a t≈143 s y el
último agente en tránsito figura en el último cuadro escrito. El informe y la presentación reflejan
esta atribución corregida ("livelock de contacto con muros", sin mencionar puertas de aula).

---

## D18 — Migración 3D del spawn de generadores: propagar la planta (z) al agente creado

- **Fecha:** 2026-07-02
- **Estado:** vigente
- **Paso del plan:** 8.2 / Task 2 (Evacuación con alumnos ya adentro de aulas de ambas plantas)

**Contexto.** Al armar el sub-escenario Evacuación (alumnos que arrancan DENTRO de las aulas de PB y
P1, vía `instant_occupation`), **ningún** agente nacía en P1: los 16 que debían spawnear en aulas de
z=3 aparecían en z=0. El **path de generación de agentes nunca se migró a 3D** (mismo tipo de gap que
D16 en servers): la `z` de la zona de `GENERATORS.csv` se perdía en tres lugares encadenados —
(1) `GeometryAssembler.buildGenerators` construía el `GeneratorZone` con el ctor de conveniencia que
fija `z=0`, ignorando `row.z()`; (2) `ConfigurablePedestrianGenerator.buildAgent` hacía
`setPosition(x,y)` (2 args, z queda en el default 0); (3) `WiredPedestrianGenerator.remapId` reconstruía
el `AgentState` al remapear el id y **también** tiraba la z (`setPosition(x,y)`). Como PB y P1 comparten
layout `(x,y)`, los agentes "de P1" quedaban indistinguibles de los de PB y salían sin usar la escalera.

**Decisión (fixes).** Propagar la planta de punta a punta `row.z()` → `GeneratorZone.z()` → generador →
`AgentState`:
1. `GeometryAssembler.buildGenerators`: `new GeneratorZone(base, area, row.z(), gParams)` (ctor con z).
2. `ConfigurablePedestrianGenerator`: nuevo campo/param `double spawnZ` (App le pasa `zone.z()`);
   `buildAgent` hace `st.setZ(spawnZ)` tras `setPosition(x,y)`.
3. `WiredPedestrianGenerator.remapId`: `setPosition(x, y, src.z())` (preserva la z al remapear).
El módulo del generador sigue operando en `xy` (las `spawnZones` son `Rectangle` 2D); sólo se agrega la
planta al crear/copiar el `AgentState`. Ripple mínimo (App + 1 test que pasa `spawnZ=0.0`).

**Resultado.** Evacuación N=40: t=0 con 16 agentes en z=3 (antes 0), los 16 bajan por escalera a una
salida de PB, evacuación completa. **149 tests verdes.**

**Alternativa descartada.** Hacer las `spawnZones` 3D (lista de rect+z) o migrar todo el generador a
`Vec3`: innecesario — la planta de spawn es una sola por generador (App cablea una zona por generador),
así que un `double spawnZ` alcanza.

**Relación.** Es el gemelo de D16 (servers): ambos son el mismo patrón de gap (ctor de conveniencia que
defaultea z=0 + wiring 2D) que la migración 3D original no cubrió porque no había servers/generadores en
plantas altas hasta el escenario Escuela multiplanta.


---

## D19 — Escalera realista (Task 3): switchback con descanso, confinamiento, carriles de contraflujo, peldaños y calibración

- **Fecha:** 2026-07-02
- **Estado:** vigente (ver **D21**: fix posterior del "salto de z" al pie de la escalera —
  exclusión de la huella de la grilla del grafo + `STAIR_FOOT_REACH` 1.5→0.15 medido a lo largo del eje)
- **Paso del plan:** Task 3 (hacer la escalera más realista sobre el escenario Escuela)

**Contexto.** La escalera del baseline (D4/D9) era un **único tramo recto** (eje pie→tope, ancho 2.0,
sin barandas propias más allá de las paredes de las puntas) dibujado como una **línea inclinada
punteada**. La Task 3 pide 5 elementos: (1) switchback con descanso, (2) paredes de confinamiento /
baranda (el agente no se sale de la huella), (3) carriles subida/bajada (contraflujo), (4) peldaños en
el render 3D, (5) velocidad efectiva calibrada (~0.5–0.6 m/s) con `z` monótona. Restricción dura: **149
tests verdes** y **no romper el ruteo multiplanta**.

**Decisión arquitectónica (fijada por el overseer).** El switchback se modela como **DOS `Stairs`
(dos tramos, A y B) unidos por un piso de descanso (landing) a `z = FLOOR_H/2 = 1.5`**, reutilizando
**toda la maquinaria multiplanta existente sin reescribir el core**:
- `Geometry.floors()` deriva `[0, 1.5, 3]` automáticamente al haber paredes del landing a z=1.5.
- El grafo genera una grilla por planta (incluida la del landing) y la une por las aristas de escalera:
  tramo A une piso0↔landing, tramo B une landing↔piso1. A* encadena piso0 → pie A → tope A (landing) →
  grilla-landing → pie B → tope B (piso1). Heurística euclídea 3D ya correcta.
- El CIM (`FloorAwareNeighborsIndex`) clasifica por z (z=0.75→tramo A, z=1.5→grilla landing, z=2.25→B).
- El CPM interpola `z` por tramo; `stairWalls` de cada tramo = unión de las paredes de las dos plantas
  que conecta (el agente sobre el tramo siente sus barandas).

**Cambios (3 piezas disjuntas).**
- **Java core (`core/Stairs`, `agent/om/CpmOperationalModel`, `environment/graph/NavigationGraph`,
  `App`):**
  - `Stairs` gana métodos **derivados** de foot/top/width (sin campos nuevos, no rompe los ~17
    call-sites): `axisDirXy`, `perpXy`, `laneOffset`=width/4, `laneTargetAt(px,py,ascending)` (centro
    del carril asignado, proyectando sobre el eje y desplazando ±laneOffset perpendicular).
  - `CpmOperationalModel`: nuevo factory `fromGeometry(Geometry, boolean stairLanes)` y campo
    `stairLanes`; el `fromGeometry(Geometry)` de 1-arg delega con `false`. **Bias lateral de carril
    gateado:** cuando `onStair != null && stairLanes && onStair.width() >= STAIR_LANE_MIN_WIDTH(2.5)`,
    mezcla en la dirección deseada `e_a` un versor hacia `laneTargetAt(x,y, ascending=footTarget.z>z)`
    con peso saturado `LANE_BIAS_WEIGHT`. **Byte-idéntico con `stairLanes=false`** (los tests usan el
    1-arg / el ctor `List<Wall>` → OFF). `App.java` pasa `true` en runs reales.
  - `NavigationGraph.stairTraversalHop`: **guard de rango-z** — además de la distancia planar al eje,
    exige que `agent.z()` caiga en `[min,max]` de la arista (±FLOOR_EPS) para no matchear el tramo
    equivocado cuando A y B del mismo switchback quedan planar-cercanos.
- **Builder (`tools/scenarios-builders/build_escuela.py`):** `build_stairs()` emite **4 filas** (2
  tramos × 2 puntas SUR/NORTE) con landing a z=1.5; `build_stair_walls()` agrega barandas de cada tramo
  (convención de plantas: tramo A→tag z=0, tramo B→tag z=1.5), perímetro del landing con huecos en las
  dos bocas, y marco de la boca de llegada del tramo B. Geometría SUR final: tramo A `(43.4,7,0)→
  (43.4,3,1.5)`, tramo B `(46.6,3,1.5)→(46.6,7,3)`, landing abierto x∈[42.1,47.9], y∈[0.5,3.0]; NORTE
  espejado en y.
- **Viz (`tools/visualize_simulation_3d.py` + `visualize_simulation.py`):** la línea punteada de
  escalera se reemplaza por un **perfil de peldaños** (tread horizontal + riser vertical, N≈|Δz|/0.18
  por tramo, color `#e67e22`); cada tramo se dibuja independiente (el switchback sale gratis). El 2D por
  planta marca la huella de la escalera como rectángulo tenue.

**Fixes de integración que expuso el switchback (los aplicó el chief, geometría/calibración):**
1. **Landing straddleaba la línea de pies (bug de conectividad).** Con el landing a horcajadas de la
   línea de pies (y∈[1.9,3.1] alrededor de y=2.5), la baranda del tramo B (que arranca en el pie)
   tapiaba la mitad del landing y dejaba sólo un pasillo de cruce de 0.6 m: **8/14 agentes** que subían
   quedaban a z=1.5 y se escapaban al norte por espacio no confinado. → El landing se re-ubicó
   **enteramente del lado de la pared lejana** (arranca EN la línea de pies, `mouth_y==y_landing`, y se
   extiende `STAIR_LANDING_DEPTH=2.5` hacia la pared lejana) → plataforma abierta para girar A→B.
   Bajó a 2/14 atascados.
2. **Escape a z=1.5 no confinado.** Un agente que llegaba a z=1.5 y recibía un hop viejo hacia el norte
   caminaba por el plano z=1.5 (sin paredes al norte del landing en la huella del tramo A). → Se
   **taggean las barandas del tramo A también a z=1.5** (además de z=0), simétricas a las del tramo B,
   confinando la huella del tramo A en el landing floor. Con esto **0/14 atascados** y **30/30 evacúan**
   (robusto en seeds 1,2,3,7).
3. **Gate de carriles vs. ancho.** El bias de carril sólo engancha con `width ≥ 2.5`; el ancho 2.4 no
   activaba. → Se **ensancha el tramo a 2.6** (STAIR_HALF_WIDTH=1.3; 2·2.6+gap 0.6 = 5.8 ≤ 6.0 del
   corredor) para que los carriles funcionen.
4. **Peso del bias de carril.** Con `LANE_BIAS_WEIGHT=0.20` los carriles **no se separaban** (medido en
   un caso de contraflujo balanceado: subida y bajada quedaban mezclados, sep ≈ 0). Se **calibró a
   0.45** (chief), que separa **~0.34 m** (subida en +perp, bajada en −perp) **sin estrangular** la
   evacuación densa.

**Calibración final.** `speed_factor = 0.38` en STAIRS.csv (con vd≈1.4 da **v efectiva medida 0.59 m/s**
en los tramos, dentro del rango 0.5–0.6); `STAIR_WIDTH = 2.6`; `LANE_BIAS_WEIGHT = 0.45`;
`STAIR_LANE_MIN_WIDTH = 2.5`; `laneOffset = width/4 = 0.65`.

**Resultados verificados (seed=1).**
- **149/149 tests verdes** (8 skipped) con todo integrado.
- **Baseline** (~30 agentes): 14 suben a P1 por el switchback (z 0→1.5→3) y bajan, **0 atascados en el
  landing**, 29/30 evacúan (el 1 restante es un rezagado lento a z=0 cerca de una salida, no del
  landing). v_flight = 0.59 m/s.
- **Contraflujo** (escenario dedicado `scenarios/contraflujo`, flujos balanceados subida/bajada por el
  switchback SUR): medido en el frame exacto del CPM (offset perpendicular firmado por el flag
  `ascending`), **subida = +0.149 m, bajada = −0.186 m, separación = +0.335 m con el signo correcto** →
  carriles separados.
- **Evacuación N=120** (56 agentes arrancan en P1, deben bajar el switchback): **118/120 evacúan**,
  tiempo medio 70.2 s, máximo 153 s (sin regresión respecto de N=40 de D18; los 2 no-evacuados son
  congestión, no atasco de escalera).
- **Render:** vista 3D apila `[0, 1.5, 3]` y dibuja **peldaños** (tread+riser) por tramo; corre sin
  excepción sobre el switchback de 4 tramos.

**Alternativas descartadas.**
- *Un tramo recto con carriles* (sin descanso): no cumple el elemento (1) switchback+descanso ni da la
  vuelta en L realista.
- *Reescribir el core para un tipo `Switchback` de primera clase*: innecesario — dos `Stairs` + landing
  reutilizan grafo/CIM/CPM tal cual (sólo cambios de builder + un guard de grafo + carriles gateados).
- *Landing a horcajadas de la línea de pies*: bug de conectividad (barandas tapian el cruce), ver fix 1.
- *Confinar el plano z=1.5 con una caja cerrada al norte*: rompería la entrada/salida legítima de los
  tramos (el agente que baja el tramo A pasa por y=7 al pie); en su lugar se confina la huella del
  tramo A con barandas z=1.5 (fix 2).
- *Bias de carril como target-replace duro*: pelearía con el pull al objetivo y estrangularía el flujo
  denso; se usa una corrección perpendicular gentil, saturada, gateada por ancho.

**Archivos tocados.** `core/Stairs.java`, `agent/om/CpmOperationalModel.java`,
`environment/graph/NavigationGraph.java`, `App.java` (Java core); `tools/scenarios-builders/
build_escuela.py` (builder); `tools/visualize_simulation_3d.py`, `tools/visualize_simulation.py` (viz).
Escenarios de verificación: `scenarios/escuela` (baseline), `scenarios/escuela_evac` (N=120),
`scenarios/contraflujo` (dedicado al chequeo de carriles).

---

## D20 — Sub-escenario Ingreso (Task 4): caudal a Nmax fijo, planes con kiosco y zona observable en el kiosco

- **Fecha:** 2026-07-02
- **Estado:** vigente
- **Paso del plan:** Task 4 (sub-escenario Ingreso sobre el escenario Escuela)

**Contexto.** El enunciado pide un sub-escenario **Ingreso**: los `Nmax=120` alumnos **llegan**
(caudal variable) y se dirigen a sus aulas; el input del barrido es el **caudal** = los 120
repartidos en **1 / 5 / 10 min** (Nmax FIJO, varía la ventana), el observable es **población vs.
tiempo** en una zona y el escalar es la **ocupación máxima/promedio** vs. caudal. El builder
`build_escuela.py` ya tenía `baseline` (D13/D15) y `evacuacion` (D18); `build_parameters(mode=
'ingreso')` tiraba `NotImplementedError`. `sweep_run.py`/`sweep_lib.py`/`plot_evacuacion.py` (Task 2)
son la infra reusada; se agregó `plot_ingreso.py`.

**Decisión.**

1. **Semántica del caudal (Nmax=120 FIJO en las 3 ventanas).** El generador CALM
   (`ConfigurablePedestrianGenerator`) traduce el par `(period, quantity)` del JSON a un caudal
   `p/min = quantity/period*60` (`App.effectiveFlowRatePerMin`) y su cupo por ventana es
   `round(quantity/period * W)` (W = ventana en s). Fijando **`period = quantity * W / cupo`** se
   obtiene el cupo EXACTO para cualquier W. Se reparten los 120 en **3 accesos dedicados**
   (`INGRESO_ENTRADAS`): `INGRESO_RECREO` (cupo 60), `INGRESO_EDIF_SUR` (30), `INGRESO_EDIF_NORTE`
   (30). `active_time = W`, `inactive_time = NEVER_REACTIVATE` (un solo burst).

2. **Zonas de spawn DEDICADAS y anchas (no las del baseline).** El generador recorta el caudal al
   tope de densidad de puerta `MAX_PEOPLE_PER_METER = 3 p/min/m` sobre `doorWidth = max(ancho,alto)`
   del rectángulo de spawn. Con las 2 zonas chicas del baseline (3×6 y 3×4 ⇒ topes 18 y 12 p/min) la
   ventana de 1 min (que necesita 120 p/min) **quedaba recortada a ~30 agentes** ⇒ el barrido salía
   **INVERTIDO** (más caudal → MENOS agentes, observable sin sentido). Se dimensionaron las zonas
   para que su tope supere el caudal de la ventana más corta: RECREO 10×30 (doorWidth 30 ⇒ tope 90 ≥
   60), EDIF_SUR y EDIF_NORTE 12×7 (doorWidth 12 ⇒ tope 36 ≥ 30). Ubicación: RECREO en el recreo
   abierto (x∈[3,13], y∈[12,42], libre del kiosco y la salida), EDIF_SUR/EDIF_NORTE en las zonas
   abiertas de PB en las dos puntas del pasillo (x∈[48,60], y∈[0.5,7.5] y y∈[52.5,59.5]).
   **Verificado: N efectivo = 120 en las 3 ventanas** (contando ids únicos en el output).

3. **Planes con el kiosco (realista).** El kiosco está en el recreo ⇒ **sólo el que entra por el
   recreo pasa por él** antes del aula; los dos accesos del edificio van directo. 4 planes:
   `ING_KIOSCO_PB` / `ING_KIOSCO_P1` (KIOSCO→AULA→EXIT, pool de `INGRESO_RECREO`) y `ING_DIR_PB` /
   `ING_DIR_P1` (AULA→EXIT, pool de los dos accesos del edificio). ~50/50 PB/P1 por acceso. Aula =
   server `CLASSROOM` con el **timbre puesto después de `max_time`** (`_classroom_ingreso`,
   sesión = max_time+50) para que absorba a los que llegan sin dismissal (en Ingreso interesa la
   llegada, no la salida). `max_time = W + 250 s`. Kiosco con `max_capacity=30` y servicio corto
   `GAUSSIAN(mean=4, std=1)`.

4. **Zona observable = frente del kiosco del recreo (NO el corredor pre-escalera).** El contrato
   proponía `(42,8,48,14)` (corredor antes de la escalera SUR), pero al medir con Nmax=120 fijo la
   **congestión no se forma en la escalera**: el switchback (Task 3, D19) es ancho (2 carriles) y sólo
   ~la mitad de los alumnos suben a P1 (47–58/120), repartidos entre las escaleras SUR y NORTE y a lo
   largo de la ventana ⇒ el pie de escalera queda casi vacío (pico 2–3, sin tendencia clara). La
   congestión real del Ingreso se forma en el **kiosco** (los ~60 alumnos del recreo se agolpan frente
   al kiosco antes de clase — el "recreo con kiosco" del enunciado). Se fijó **`ZONA = (2,42,14,52)`**
   (z=0) en `plot_ingreso.py`, con `--zone` para overridear (y `ZONA_ESCALERA_SUR` documentada). El
   enunciado dice "una zona (p. ej. antes de la escalera)": el "p. ej." habilita medir donde
   efectivamente hay congestión.

**Resultado verificado (seed=1, barrido 1/5/10 min, `--mode ingreso`).**
- **N efectivo = 120 / 120 / 120** (ids únicos en cada `output.csv`).
- **Suben a P1 por la escalera:** 47 (1 min) / 58 (5 min) / 57 (10 min). El output muestra agentes en
  todos los `z` del switchback (tramo A z∈[0.5,1.4], landing z≈1.5, tramo B z∈[1.6,2.6], P1 z≈3).
- **Observable (ocupación en la zona del kiosco), pico | promedio | t_pico:**
  - 1 min:  **pico 51 | prom 29.43 | t_pico 73.6 s**
  - 5 min:  **pico 16 | prom  6.44 | t_pico 268.8 s**
  - 10 min: **pico  6 | prom  1.53 | t_pico 184.6 s**
  **Tendencia correcta: el pico y el promedio DECRECEN al alargar la ventana** (caudal más lento ⇒
  menos aglomeración simultánea), monótona y fuerte (pico 51→16→6, ~8.5× de caída).
- Gráficos: `out/ingreso_poblacion.png` (población vs. tiempo, una curva por caudal) y
  `out/ingreso_scalar.png` (máx/prom vs. ventana).

**Alternativas descartadas.**
- *Reusar las 2 zonas chicas del baseline* (lo que decía el contrato B4.1): el tope de densidad de
  puerta recorta la ventana de 1 min a 30 agentes ⇒ barrido invertido. Se necesitan zonas anchas.
- *2 accesos en vez de 3*: ninguna zona abierta de la PB del edificio llega a doorWidth 20 (las puntas
  del pasillo son de ~12 m; el corredor central de 6 m contaminaría el observable), así que un solo
  acceso de edificio no alcanza el cupo 60 sin recorte. Se parte en dos accesos de 30 (SUR y NORTE),
  que además alimentan naturalmente las dos escaleras.
- *Modo BATCH / `instant_occupation`*: no tiene tope de densidad, pero coloca todo en t=0 ⇒ destruye
  la semántica de "ventana de llegada" (1/5/10 min). Se mantiene CALM.
- *Observable en el corredor antes de la escalera SUR* `(42,8,48,14)` u otras variantes de
  corredor/escalera (norte, boca del corredor): probadas todas, señal plana o no monótona (picos 2–5)
  porque la escalera ancha no congestiona. Se documenta como hallazgo (la escalera Task 3 absorbe bien
  el flujo de Ingreso) y se mueve el observable al kiosco, que sí es informativo.
- *Timbre del baseline (t=140)*: expulsaría a los alumnos de vuelta a la zona durante la ventana; en
  Ingreso interesa la llegada, así que el timbre se corre más allá de `max_time`.

**Archivos tocados.** `tools/scenarios-builders/build_escuela.py` (rama `ingreso` de
`build_generators` + `build_parameters`, `INGRESO_ENTRADAS`/`INGRESO_ZONES`,
`_build_parameters_ingreso`, `_classroom_ingreso`); `tools/plot_ingreso.py` (nuevo). Java sin
cambios (no se corrió `mvn test`: Task 4 no toca Java; `mvn -q compile` OK). Salidas de verificación:
`out/sweeps/ingreso/v{1,5,10}/seed1/output.csv`, `out/ingreso_poblacion.png`, `out/ingreso_scalar.png`.

---

## D21 — Fix del "salto de z" al pie de la escalera (teletransporte a mitad de tramo)

- **Fecha:** 2026-07-02
- **Estado:** vigente
- **Paso del plan:** bug visual/físico sobre la escalera switchback (D19)

**Síntoma.** Agentes que llegaban a una escalera **saltaban de `z=0` a `z≈0.55` en un solo frame**
de salida (parecían teletransportarse a la mitad del tramo). Medido: `max |Δz|/frame ≈ 0.56` en
baseline y `≈0.57` en ingreso, todos con el patrón `z: 0 → ~0.55` a `y≈5.5` (SUR) / `y≈56.9`
(NORTE), es decir a mitad de la huella del tramo A.

**Causa raíz (overshoot).** El tramo A SUR (pie `(43.4,7,0)`, tope/descanso `(43.4,3,1.5)`) tiene el
**pie en el extremo LEJANO** (y=7, lado corredor) y su huella (tubo de barandas x∈[42.1,44.7],
y∈[3,7]) está **abierta arriba y abajo** (sin peldaños que ocluyan). Como el `GridNodeReducer` ponía
**nodos de piso a ras del suelo DENTRO del tubo**, y el nodo-pie se conectaba a un nodo al **SUR**
(bajo el descanso) *a través* del tubo, el A* ruteaba al pie **por dentro de la huella**: los agentes
que llegaban desde el sur (entrada) **subían el tubo a `z=0`** (su footTarget era el pie,
`headingAcross=false` ⇒ el OM no interpola z). Al llegar a **1.5 m del pie** el grafo
(`furthestVisibleHopOnPath`) cambiaba el hop del pie al tope; con `STAIR_FOOT_REACH=1.5` eso pasaba a
avance ≈0.37 (tramo de 4 m, Δz=1.5), donde `zAt≈0.56`: `headingAcross` pasaba a `true`, el OM
enganchaba `setZ(zAt)` y la z **saltaba 0 → 0.56**.

**Decisión final (dos cambios chicos; NO tocan el core, el switchback D19, el builder ni la viz).**
1. **Grafo — EXCLUIR de la grilla los nodos de piso dentro de la huella** (`GridNodeReducer`, nuevo
   `stairsExclude`): en `buildGrid`, `free[g] = ... && !insideAnyStairFootprint(celda)` con
   `Stairs.containsXy`. Se aplica en **todas las plantas** (la huella es el tubo de la escalera, no piso
   caminable). `GraphBuilder.fromGeometry` pasa `geometry.stairs()` al reducer. Con esto **desaparecen
   los nodos-basura del tubo**, el A* deja de rutear al pie por dentro y el ruteo del descanso queda
   limpio (ver más abajo: es lo que arregla el *turnback*).
2. **Grafo — cambiar el hop del pie al tope pegado al extremo, medido a lo largo del eje.**
   `STAIR_FOOT_REACH` pasa de **1.5 → 0.15** y se mide como **distancia a lo largo del eje** del tramo
   (nuevo helper `alongAxisDistFromNear`), no euclídea. Es la **misma proyección que usa `Stairs.zAt`**:
   al cambiar el hop, `zAt(agente) ≈ zAt(extremo cercano) = z de la planta`, así que la z engancha desde
   el nivel del piso **sin salto**. Sirve para subida (extremo cercano = pie, z=0) y bajada (extremo =
   tope de B, z=3): en ambos `zAt(extremo)` = z actual del agente. Ignora el desvío perpendicular a
   propósito y, si el agente se **pasa** del extremo, el helper recorta el avance a 0 (robusto al
   overshoot). Cota del salto: `≤ 0.375·(0.15 + avance-de-1-frame) ≈ 0.10`.

**El fix 2 SOLO regresaba el ruteo; hizo falta el fix 1 para arreglarlo.** Con reach chico pero **con la
grilla vieja** (nodos dentro del tubo), aparecía un **"turnback"**: agentes que llegaban al descanso
(z=1.5) **bajaban sin cruzar al tramo B** (tan visible como el teletransporte). Medido con reach=0.15 y
grilla vieja: **turnback=4, P1=10** (objetivo: turnback≤1, P1≥14). La **exclusión de la huella (fix 1)**
limpia el ruteo del descanso y **elimina el turnback**: con exclusión + reach 0.15 ⇒ **turnback=0,
P1=15**. La exclusión es lo que da el ruteo bueno; el reach chico es lo que evita el salto de z.

**Alternativas probadas y descartadas (el camino tuvo varias vueltas).**
- *Reach chico SOLO (sin exclusión)*: arregla el salto pero **regresa el ruteo** (turnback=4, P1=10),
  porque el A* sigue teniendo nodos dentro del tubo y el cruce del descanso queda flojo. Descartado.
- *Reach generoso (1.5) SOLO*: enganche confiable (turnback=0) pero **reintroduce el salto** (0.56),
  porque el cambio de hop ocurre a mitad de huella. Descartado.
- *Ocluir la huella en la visibilidad de piso* (reducer `seesOver` + `NavigationGraph.isVisible` +
  `connectToFloor`, con `Stairs.crossesFootprint`): intentaba que el pie se conectara por la boca/norte.
  En la planta baja lo lograba (pie→vestíbulo), pero **rompía el cruce del DESCANSO** (z=1.5): al ocluir
  ahí, los descansos SUR y NORTE quedaban desconectados y el cruce tramo A→B se caía (**P1 2/30**).
  Gatearlo sólo a la planta baja tampoco creaba el nodo del vestíbulo en esta geometría (el pie seguía
  conectándose al sur). **Toda la maquinaria de oclusión se quitó** (dead code) al ver que la simple
  exclusión + reach chico cumple todo.
- *Escalar el escape de contacto por `speedFactor`* / *rate-limiter de z por paso en el OM*: se probaron
  para un salto residual `~0.14` de un empujón sobre el tramo. Con la solución final (exclusión + reach)
  ese salto ya no aparece (Δz ingreso ≤ 0.11), así que **ambos se descartaron**: el rate-limiter además
  desincronizaba la z de la clasificación por planta (`isOnFloor`) y bajaba P1/evac. El `setZ(zAt)` y el
  escape quedan **exactos, idénticos al original**.
- *Amurallar la huella a `z=0`*: descartado por diseño — el anti-tunneling de un agente SOBRE el tramo
  usa `stairWalls` = unión de las dos plantas (D19), así que una pared z=0 en la huella bloquearía al que
  sube (chequeo planar).

**Resultados verificados (seed=1).**
- **149/149 tests verdes** (8 skipped), incl. `GraphMultiFloorTest` y `CpmOperationalModelStairTest`.
- `max |Δz|/frame`: **baseline 0.56 → 0.081**; **ingreso 0.57 → 0.087 (v5)**, 0.11 (v1, ventana densa),
  0.095 (v10). **0 frames > 0.12**.
- **Ruteo igualado/mejorado** vs. el switchback previo (P1≈14 / turnback 0 / evac≈29): baseline **P1=15,
  turnback=0, evac=28**, 15 llegan al descanso; ingreso **P1=57 (v5/v10), 42 (v1)**. El switchback se
  recorre completo.
- Observable de ingreso **intacto**: kiosco pico **51 / 16 / 6** para ventanas de 1 / 5 / 10 min
  (`sweep_lib.zone_population(f, 2,42,14,52, zlevel=0.0)`).

**Archivos tocados (mínimos).** `environment/graph/GridNodeReducer.java` (campo `stairsExclude` +
`insideAnyStairFootprint` + exclusión en `buildGrid`; overload de `reduce`); `environment/graph/
GraphBuilder.java` (pasa `geometry.stairs()` al reducer); `environment/graph/NavigationGraph.java`
(`STAIR_FOOT_REACH` 1.5→0.15 a-lo-largo-del-eje + helper `alongAxisDistFromNear`); `agent/om/
CpmOperationalModel.java` (sólo doc del `setZ`; física idéntica). Sin cambios de `core`, builder ni viz.

## D22 — Modo crisis en Evacuación: perfil físico por generador (`max_velocity` honrado)

- **Fecha:** 2026-07-04
- **Estado:** vigente
- **Paso del plan:** Fase 2 (auditoría vs wiki) — hallazgo del verificador código-vs-enunciado

**Contexto.** El enunciado afirma que "el simulador ya cuenta con un modo para representar el
comportamiento de agentes en situación de crisis", pero la verificación adversarial mostró que
**no existía**: `App` asignaba a TODOS los agentes de TODOS los escenarios el mismo perfil
hardcodeado (`CpmParameters.baglietoParisiSet1()`, vd=1.55), y el `max_velocity` que el builder
escribía en el Formato B se parseaba (`GeneratorRawParams.maxVelocity`) pero **nunca se consumía**
(código muerto): la Evacuación corría con física idéntica al baseline.

**Decisión.** Perfil físico **por generador**, derivado del `max_velocity` del Formato B:
1. `App.zoneProfile(gp, base)`: si la zona declara `max_velocity` > 0 y ≠ vd del default, se crea un
   `AgentProfile` igual al default pero con `vd = ve = max_velocity`. Si no, `null` (default).
2. `ConfigurablePedestrianGenerator` acepta un `profileOverride` opcional (constructor nuevo; el
   viejo delega con `null`) y lo setea en el `AgentState` al nacer. `AgentAssembler.wireAgent` ya
   respetaba el perfil pre-seteado (solo aplica el default si `profile == null` — el hook estaba
   documentado y sin usar).
3. El `dt` efectivo se acota con el perfil **más rápido** del escenario
   (`om.recommendedDt(fastest)`), no solo el default: con vd=2.0 el dt baja a 0.0375 s.
4. `build_escuela.py`: **evacuación → `max_velocity: 2.0`** (vd de emergencia, "modo crisis");
   baseline e ingreso → `1.55` (el vd del perfil default, para no cambiar lo ya validado — antes
   decían 1.4 pero era código muerto).

**Verificado.** Corrida de evacuación N=120 seed 1: velocidad mediana/p90 en el plano = **2.00 m/s**
exactos, 119/120 evacuados, t_prom 63.1 s, t_max 137.8 s. Suite completa: **143 tests, 0 fallos**
(2 tests nuevos del override en `ConfigurablePedestrianGeneratorTest`).

**Alternativas descartadas.** (a) No hacer nada y documentar "evacuación = caminata normal" en el
informe — dejaba el requisito del enunciado incumplido y el `max_velocity` muerto/engañoso.
(b) Un flag global `-Dsimped.crisis` — menos expresivo que el knob por zona que el Formato B ya
tenía; el per-generador además arregla la inconsistencia del código muerto.

**Impacto.** Los resultados del sub-escenario Evacuación cambian (t_evac más cortos, más congestión
en puertas); los barridos se re-corrieron completos con la física nueva. Baseline e Ingreso quedan
iguales (1.55 ≡ default efectivo previo). El Formato A no declara `max_velocity` (OptionalDouble
vacío) ⇒ sin cambios.

## D23 — Semilla global propagada a TODOS los streams aleatorios (`Seeds.mixOr`)

- **Fecha:** 2026-07-04
- **Estado:** vigente
- **Paso del plan:** Fase 2 (auditoría vs wiki) — hallazgo del verificador código-vs-enunciado

**Contexto.** `-Dsimped.seed` (D del barrido reproducible) solo alimentaba 2 streams vía
`Seeds.rng(salt)`: el generador de peatones y la aleatorización de hops del navgraph. El resto
usaba **constantes fijas** que ignoraban la semilla global: `ServersWiring` (`new Random(0)` para
tiempos de servicio), `ServersModule` (softmax assigner con `new Random(0)`), y los RNGs de
selección de `AgentAssembler` (`selectionRng`/`dwellRng`/`groupedLocationSeed`, sembrados por
constantes + agentId + hashes). Consecuencia: entre réplicas del barrido cada agente elegía **la
misma salida** (`exit_selection`), el **mismo aula** del grupo y los **mismos tiempos de
servicio/dwell** — las "5 realizaciones" eran menos independientes de lo que aparentaban.

**Decisión.** Nuevo `Seeds.mixOr(fallback, salt)`: si `simped.seed` está seteada devuelve
`seed ^ salt.hashCode()` (el stream varía por réplica); si no, devuelve el **fallback = la
constante histórica** (comportamiento idéntico al de siempre para tests y corridas sueltas).
Aplicado en: `ServersWiring` (service-time sampler y softmax assigner, ahora por el constructor
completo de `ServersModule` replicando los defaults del de conveniencia) y `AgentAssembler`
(XOR del mix en los seeds de `selectionRng`, `dwellRng` y `groupedLocationSeed` — XOR con 0 es
identidad, así que sin semilla global nada cambia).

**Verificado.** Suite completa **143 tests, 0 fallos** sin tocar ningún test existente (el fallback
preserva las secuencias históricas). Con `simped.seed` distinta, las selecciones de salida/aula y
los tiempos de servicio ahora difieren entre réplicas.

**Alternativas descartadas.** Reemplazar las constantes por `Seeds.rng(salt)` directo — rompía el
determinismo por defecto (sin `simped.seed` devolvía `Random` sin sembrar) y cambiaba resultados de
tests que dependen de las secuencias históricas.

**Impacto.** Las réplicas del barrido son realizaciones genuinamente más independientes (spawn +
ruteo + salida + aula + servicio + dwell varían todos con la semilla). Barridos re-corridos.

## D24 — Fix del deadlock de boca de escalera: descansos mallados + gate lateral + boca ancha

- **Fecha:** 2026-07-08
- **Estado:** vigente
- **Paso del plan:** post-entrega — hallazgo del stress-testing (`REPORTE_STRESS.md`)

**Contexto.** El stress-test encontró que con N≥300 la evacuación se clava: un arco estable de
agentes en la boca superior de UNA escalera detiene el flujo por completo (N=500: 168 atrapados,
flujo 0 durante 600 s; N=800: 280). El diagnóstico con el grafo real (probe + Dijkstra) encontró
tres defectos encadenados:
1. **`keepMainFreeComponent` descartaba los descansos**: en el plano z=1.5 la región libre más
   grande era la franja *fantasma* entre los dos descansos (artefacto del bounding box), así que
   la malla del descanso real se tiraba y los extremos de escalera se encadenaban por el vacío
   con aristas de 49–54 m (wormhole cruzando el edificio a z=1.5).
2. Con ese grafo, el único camino de bajada pasaba por una sola escalera (A* sin alternativas:
   `assemblePlanar` construye un **árbol**) → toda la planta alta se embudaba en una boca.
3. En la boca, el cambio de hop al extremo lejano ignoraba el **desvío lateral** (solo medía la
   distancia a lo largo del eje, D21): en multitud, los agentes que cruzaban la línea de la boca
   corridos lateralmente recibían "bajá" y quedaban clavados contra las barandas desde afuera,
   taponando el hueco; el resto convergía isotrópicamente al **nodo puntual** del tope → arco.

**Decisión (tres cambios coordinados).**
- **`GridNodeReducer`**: (a) `keepMainFreeComponent` conserva las componentes **ancladas por un
  extremo de escalera de esa planta** (radio 1 m) y descarta el resto; sin anclas, la mayor como
  siempre (escenarios sin escaleras: comportamiento idéntico). (b) `walkLine` y la cobertura
  exigen **misma componente transitable** (`gridComp`): verse a través del vacío no es caminar ni
  cubrir. (c) Fase de densificación en `assemblePlanar`: tras el árbol se agregan las aristas
  visibles restantes que no crucen y respeten el espacio personal (rutas alternativas).
- **`GraphBuilder`**: pasa los tramos con su semiancho (`NavigationGraph.StairSpan`) al grafo.
- **`NavigationGraph`**: el flip del hop al extremo lejano exige además **desvío perpendicular ≤
  semiancho** (gate lateral); y mientras el agente se acerca, el hop es su **proyección sobre el
  segmento de la boca** (± semiancho − 0.35 m de hombro), no el nodo puntual — cada agente apunta
  a su propio punto de entrada, como en una puerta ancha. Sin `StairSpan` (mocks/CSV) cae en el
  comportamiento histórico (tolerancia `STAIR_AXIS_TOL`).

**Cuarto cambio (misma D):** pase de **espaciado máximo de nodos** (`enforceNodeSpacing`,
`MAX_NODE_SPACING=12 m`, tras la fusión): la cobertura por visibilidad dejaba áreas abiertas
grandes (el recreo, 30×60 m) con 2–3 nodos y las distancias del grafo quedaban distorsionadas en
decenas de metros (otro motor del embudo). Solo se agregan celdas con línea de vista a un nodo
existente (sin eso, un bolsillo alcanzable por una rendija generaba nodos aislados que capturaban
la consulta de nodo-más-cercano).

**Verificado.** Suite **143 tests, 0 fallos**. Stress re-corrido (seed 1): **N=500 evacúa 499/500
y el edificio queda vacío a t=310 s (antes: 168 atrapados para siempre)**; N=300: 298/300, 0
problemas; N=120 mejora t_evac medio 63.1→50.3 s (−20%) y máx 137.8→90.6 s (−34%), dist mínima
entre pares 0.306 m (antes 0.228) y reparto de escaleras 12 N / 44 S (antes ~todo por una). N=800
(6.7× el máximo del informe): ya no se clava (flujo nunca nulo, 631/800) pero se arrastra — límite
conocido. El grafo de la Escuela ya no tiene aristas fantasma a z=1.5, los descansos tienen nodos
propios y el recreo pasó de 3 a ~15 nodos.

**Alternativas descartadas.** (i) Conectar los extremos del mismo descanso "a mano" con una arista
directa — atravesaba el murete divisor del descanso; el camino real lo aporta la malla del descanso.
(ii) Desempate aleatorio del A* para repartir escaleras — no ataca la causa (los caminos no eran
alternativas comparables sino un único camino defectuoso) y rompe reproducibilidad.
(iii) Ruteo sensible a congestión — cambio de arquitectura mayor, innecesario una vez que la boca
no se clava.

**Impacto / deuda.** El ruteo sigue prefiriendo mayormente UNA escalera (la sur, por la métrica
del árbol por planta y la salida EDIFICIO al sureste): ya no produce deadlock pero la otra escalera
queda subusada — balancear la carga (malla más densa en áreas abiertas o costo por congestión)
queda como mejora opcional. **Los observables del informe cambian** (t_evac baja ~13% en N=120):
si se adopta este fix para la entrega hay que re-correr los barridos de 5 semillas y actualizar
informe + presentación.
