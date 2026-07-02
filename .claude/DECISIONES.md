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
