# CLAUDE.md — Guía de desarrollo: ampliación a 3D del simulador peatonal

Este archivo orienta el trabajo sobre **sim-peatonal-extended-3D**. El objetivo del TP
final es **ampliar el simulador (hoy 2D) para soportar mapas con más de una planta y
escaleras**, es decir, llevarlo a 3D. Ver el enunciado completo en
[`.claude/ENUNCIADO.md`](./ENUNCIADO.md).

---

## Reglas de trabajo (leer primero)

1. **PROHIBIDO hacer commits en nombre de Claude.** No ejecutar `git commit` ni `git push`.
   No agregar `Co-Authored-By: Claude`. El usuario es el único que commitea. Claude solo
   edita archivos del working tree.
2. **Ignorar todo rastro del trabajo comunitario anterior dividido por grupos.** El proyecto
   fue construido en clase repartido entre ~10 grupos (referidos como `G0`–`G9`, `Grupo N`,
   `Block H`, `T3/T4/T5/T6`, y un "contract v4" con interfaces `I1`–`I20`). **Esa división de
   responsabilidades ya no aplica**: tenemos control total del repo y podemos modificar
   cualquier módulo. Tratar esas menciones como ruido histórico, no como restricciones de
   propiedad. El inventario de esos rastros está en
   [`.claude/LIMPIEZA_RASTROS_GRUPOS.md`](./LIMPIEZA_RASTROS_GRUPOS.md) para limpiarlos.
3. **Usar siempre CPM** (Contractile Particle Model) como modelo físico. SFM existe pero no
   se usa en este TP (`SfmaOperationalModel` puede ignorarse/eliminarse).
4. **Registrar todas las decisiones en [`.claude/DECISIONES.md`](./DECISIONES.md).** Es la
   fuente de verdad de las decisiones de arquitectura del TP. Reglas:
   - **Consultar `DECISIONES.md` antes de realizar cualquier cambio en la arquitectura del
     proyecto** (tipos base, puertos, formato de escenarios/output, modelo físico, etc.).
   - **Toda decisión tomada debe agregarse** como una entrada nueva (`D<n>`) con fecha,
     contexto, decisión, alternativas descartadas y motivo.
   - **Todo cambio sobre una decisión existente debe quedar registrado** ahí (actualizar su
     estado y dejar constancia del cambio, sin borrar el historial).

---

## Cómo compilar y correr

Requisitos: **Java 21** y **Maven 3.9+**.

```bash
mvn clean compile          # compilar
mvn test                   # tests (JUnit 5 + AssertJ)
mvn package                # empaqueta target/simped-1.0.0-SNAPSHOT.jar
```

Entry point: `ar.edu.itba.simped.App`. Argumentos posicionales:

```
App <scenarioDir> <outputFile> [om]
```

- `scenarioDir` — directorio del escenario (default `scenarios/example`).
- `outputFile`  — CSV de salida (default `out/output.csv`).
- `om`          — modelo: `cpm` o `sfm`. **Para este TP usar siempre `cpm`** (también vía
  env var `SIMPED_OM=cpm`). El default actual del código es `sfm`; conviene cambiarlo a
  `cpm` o pasarlo explícito.

Correr (el escenario `example` es Formato A puro CSV y no necesita Jackson):

```bash
java -jar target/simped-1.0.0-SNAPSHOT.jar scenarios/example out/output.csv cpm
```

Para escenarios en **Formato B** (`parameters.json`) hace falta Jackson en el classpath
(el jar no es fat-jar): correr con `mvn exec` o armando el classpath con las deps.

### Visualización

- `tools/visualize_simulation.py` — anima `out/output.csv` sobre el escenario (2D, círculos
  coloreados por estado). Requiere `pip install matplotlib pillow`.
- `tools/animate_run.py` — animación alternativa.
- Debug de ruteo: `-Dsimped.hopLog=out/hops.csv` registra cada `nextVisibleHop` para
  superponer los hops sobre el GIF.

---

## Arquitectura

Diseño hexagonal: los **puertos** (interfaces) viven en `core/ports/` y cada módulo tiene su
implementación. `App.java` cablea todo a mano (no hay framework de DI). El loop maestro es
`SimulationDriverImpl`.

### Estado del agente — `core/AgentState`
Única fuente de verdad del estado kinemático y de comportamiento. **Mutable.** Lo escribe el
generador al spawnear y el OperationalModel cada `dt`; lo leen Sensors y el índice de vecinos.
Hoy guarda `x, y, vx, vy, radius, BehaviorState, AgentProfile`. **No tiene `z`.**

### Pipeline por agente (`AgentImpl.step(dt)`)
1. **Sensors** (`agent/sensors/SensorsImpl`) — calcula distancia al foot-target, dispara
   approach/arrival, recibe señales de Servers.
2. **StateMachine** (`agent/statemachine/StateMachineImpl`) — traduce la task del Plan en un
   `BehaviorState` (WALKING, APPROACHING, ARRIVED, OCCUPYING, LEAVING, QUEUEING, DEAD…) y
   expone el foot-target.
3. **PreOM** (`agent/preom/CpmPreOM`) — resuelve el foot-target intermedio consultando el
   **Graph** (`nextVisibleHop`) si no hay línea de vista directa. Throttling de ~0.25 s.
4. **NeighborsIndex** (`environment/neighbors/CimNeighborsIndex`) — devuelve vecinos
   (agentes + paredes) dentro de un radio.
5. **OperationalModel** (`agent/om/CpmOperationalModel`) — integra fuerzas y muta
   `AgentState`.

### Loop maestro — `simulation/SimulationDriverImpl`
`init()` → spawn inicial → por cada `dt`: spawnTick, update CIM, `agent.step()`, step de
Servers, harvest de agentes DEAD, output cada `dtOut`.

### Módulos (carpeta → puerto → rol)

| Carpeta | Puerto (`core/ports/`) | Rol |
|---|---|---|
| `core/`, `core/ports/` | — | Tipos compartidos (`Vec2`, `Wall`, `Segment`, `AgentState`, `AgentProfile`…) + contratos |
| `simulation/` | `SimulationDriver`, `OutputSink` | Loop temporal y escritura de output |
| `input/` | `ScenarioLoader` | Carga de escenarios (CSV Formato A / JSON Formato B) |
| `scenario/` | — | Cableado de agentes/servers (`AgentAssembler`, `ServersWiring`) |
| `agent/plan/` | `Plan` | Lista ordenada de tasks del agente |
| `agent/sensors/` | `Sensors` | Detección de arrival/approach |
| `agent/statemachine/` | `StateMachine` | Behavior state + foot-target |
| `agent/preom/` | `PreOM` | Ruteo (consulta al Graph) |
| `agent/om/` | `OperationalModel` | Física (CPM / SFM) |
| `environment/geometry/` | `Geometry` | Walls, Locations, Exits, GeneratorZones, ServerZones |
| `environment/graph/` | `Graph` | Malla de navegación + A* + Furthest Visible Point |
| `environment/neighbors/` | `NeighborsIndex` | CIM (Cell Index Method) |
| `environment/generator/` | `PedestrianGenerator` | Spawneo de agentes |
| `environment/servers/` | `Server` | Servidores/colas (cajas, aulas, etc.) |

---

## Formato de escenarios

Directorio con CSV (separador `, `). Ejemplo completo en `scenarios/example/`. Los CSV de
geometría traen columnas `z`. **Desde el paso 2, `input/csv/*` propaga la `z` como "planta"
de cada elemento** (ver D3 en [`DECISIONES.md`](./DECISIONES.md)): para elementos planos
`z1==z2` y se usa ese valor; si difieren se emite un warning y se toma `z1`. (El grafo —
`GraphBuilder.parseWallsCsv`— todavía descarta `z`; se ataca en el paso 4.)

| Archivo | Columnas | Nota 3D |
|---|---|---|
| `WALLS.csv` | `x1,y1,z1,x2,y2,z2` | `z` = planta de la pared |
| `EXITS.csv` | `block_name,x1,y1,z1,x2,y2,z2` | `z` = planta de la salida |
| `GENERATORS.csv` | `block_name,x1,y1,z1,x2,y2,z2` | `z` = planta de la zona de spawn |
| `TARGETS.csv` | `block_name,figure_type,radius,x1,y1,z1,x2,y2,z2` | `z` = planta del target |
| `SERVERS.csv` | `block_name,x1,y1,z1,x2,y2,z2` (sufijos `_SERVER`, `_QUEUE000`…) | `z` = planta del server |
| `STAIRS.csv` *(nuevo, opcional)* | `block_name,x1,y1,z1,x2,y2,z2,width[,speed_factor]` | escalera: eje pie `(x1,y1,z1)` → tope `(x2,y2,z2)`, `z1≠z2` (ver D4) |

Params: `SIM_PARAMS.csv` (dt, dt_out, t_total), `GENERATOR_PARAMS.csv`, `SERVER_PARAMS.csv`,
`PLANS.csv` (templates de plan: `template_name, step_order, target_type, target_block_name`
con `target_type ∈ {TARGET, SERVER, EXIT}`).

### Output — `out/output.csv`
Una fila por agente por output step: `tout; x; y; vx; vy; state; id` (sep `; `, sin header,
`Locale.US`). **No tiene `z`** → habrá que agregarlo para la animación 3D.

---

## Cambios necesarios para la ampliación a 3D (por módulo)

> El hilo conductor del problema: hoy **todo es 2D** porque el tipo base de posición/velocidad
> es `core/Vec2` (solo `x, y`) y se usa en cascada en todos los módulos. La ampliación gira en
> torno a representar la coordenada `z` (planta + escalera) y a que **vecindad, física, grafo y
> output** la respeten. Las escaleras son el elemento nuevo que conecta plantas.

### 0. `core/` — tipos base (es la raíz del cambio)
- **`Vec2` → posición 3D.** Decidir entre: (a) crear un `Vec3 (x,y,z)` y migrar, o (b) agregar
  `z` a `AgentState` aparte y mantener `Vec2` para el cómputo horizontal. **Recomendado:** un
  `Vec3` para posiciones/velocidades y conservar operaciones 2D donde la dinámica es planar
  (la física peatonal ocurre en el plano de cada planta; la `z` cambia principalmente en la
  escalera). Esto evita reescribir toda la geometría planar.
- **`AgentState`** — agregar `z` (y `vz` si el modelo lo necesita) con sus getters/setters.
  Es el cambio que se propaga a todos los consumidores.
- **`Wall`, `Segment`, `Exit`, `Location`** — hoy son 2D. Las paredes pertenecen a una planta;
  conviene asociarles la planta/`z` a la que pertenecen (o un rango `z` para rampas/escaleras).
- **Nuevo concepto `Escalera`/`Stairs`** — tipo nuevo en `core/`: un tramo que conecta dos
  plantas (`z` inicio → `z` fin), con su geometría, su par de extremos y la marca de "velocidad
  reducida". Definir cómo se declara en los CSV (p. ej. un `STAIRS.csv` nuevo, o un layer/atributo
  en `WALLS`/`GENERATORS`). Mantener intactos los CSV de geometría existentes y agregar uno nuevo
  es lo más limpio (igual criterio que el README sugiere para `EXIT_PARAMS`/`TARGET_PARAMS`).

### 1. `input/` — carga de escenarios
- Dejar de descartar `z`: `WallsCsvReader`, `ExitsCsvReader`, `TargetsCsvReader`,
  `GeneratorsCsvReader`, `ServersCsvReader` deben **propagar `z1/z2`**.
- Parsear el nuevo elemento **escalera** (archivo/atributo a definir) y exponerlo en `Geometry`.
- `GeometryAssembler` arma `GeometryImpl`: sumar las escaleras y la info de planta.

### 2. `environment/geometry/` — `Geometry`
- Exponer las paredes/exits/locations **con su planta** y la lista de **escaleras**.
- Útil agregar consultas tipo `floorOf(z)` o agrupar elementos por planta, porque varios
  módulos (vecinos, grafo) operan **por planta**.

### 3. `environment/graph/` — `Graph` (A* + navegación)
Es uno de los módulos más afectados. Según el enunciado:
- **Generar el grafo de cada planta de forma independiente** (reusar el generador automático
  por grilla actual, `GraphBuilder`/`GridNodeReducer`, una vez por planta) y luego **unir los
  grafos a través de las escaleras** (aristas que conectan el nodo al pie de una escalera en
  una planta con el nodo al tope en la otra).
- **A* en 3D:** la heurística debe ser **distancia euclídea con las tres coordenadas (x,y,z)**.
  `AStarPathfinder` ya usa `nodes.get(neighbor).distanceTo(goal)` como heurística → al pasar a
  `Vec3` con `distanceTo` 3D queda correcto. Verificar que los **costos de arista** también
  consideren `z` (y que la escalera tenga el costo/longitud real del tramo inclinado).
- `NavigationGraph` / `nextVisibleHop` (Furthest Visible Point) y `VisibilityUtils`: la
  visibilidad debe respetar plantas — un agente en planta 0 no "ve" un nodo de planta 1 salvo a
  través de la escalera. Lo más simple: visibilidad y FVP por planta; el cruce entre plantas solo
  ocurre por aristas de escalera del grafo.
- Los nodos del grafo deben llevar `z` (hoy `List<Vec2> nodes`).

### 4. `environment/neighbors/` — `NeighborsIndex` (CIM)
Enunciado: **la detección de vecinos es independiente por planta, salvo en las escaleras.**
- El `CimNeighborsIndex` indexa hoy en una grilla 2D `(x,y)`. Opciones: (a) **una grilla por
  planta** (lo más directo y eficiente), o (b) grilla 3D. Con (a), un agente solo ve vecinos de
  su planta; los de la escalera se tratan como caso especial (la escalera puede modelarse como
  una "planta puente" o consultarse en ambas grillas adyacentes).
- **Verificar que las paredes (su punto más cercano) sigan detectándose como vecinos, y los
  vértices de las paredes cuando el vértice sea el punto más cercano** (el enunciado lo pide
  explícito). Hoy `Wall.closestPointTo` ya devuelve el extremo cuando el `t` se clampa a [0,1]
  → revisar que ese comportamiento se mantenga y se testee en 3D/por planta.

### 5. `agent/om/` — `CpmOperationalModel` (física)
- La dinámica del CPM es planar; debe seguir operando en el plano de la planta del agente.
  En la **escalera la velocidad es menor** (enunciado): introducir un factor de velocidad
  reducida cuando el agente está sobre una escalera (afecta `vd`/`desiredVelocity`).
- Al avanzar en la escalera, además de `(x,y)` hay que **actualizar `z`** interpolando según el
  progreso a lo largo del tramo (la escalera tiene `z` de inicio y fin).
- `moveWithWallCheck` y el chequeo anti-tunneling usan paredes 2D: deben usar las paredes **de la
  planta actual** del agente (no las de otra planta).
- Mantener CPM como único modelo (ver Reglas).

### 6. `agent/preom/`, `agent/statemachine/`, `agent/sensors/`
- **PreOM** ya delega el ruteo al Graph; al volverse 3D el grafo, el PreOM funciona casi igual,
  pero el `footTarget` y las posiciones deben ser 3D. La distancia/visibilidad la resuelve el
  Graph.
- **Sensors/StateMachine**: las distancias a target (`distanceToTarget`) deben considerar `z`
  cuando el target está en otra planta (aunque normalmente el agente llega vía escalera y el
  arrival se evalúa ya en la planta del target).

### 7. `simulation/` — output
- `OutputSinkImpl` debe **emitir `z`** (y `vz` si aplica): nueva columna para que la animación
  3D pueda ubicar al agente en la planta correcta. Actualizar el formato y los lectores de los
  scripts Python.

### 8. Animación / `tools/`
Enunciado: cada planta se sigue animando **2D (círculos)**, y se agrega una **vista 3D a 45°**
con las plantas una arriba de otra (los círculos pueden verse como cilindros).
- Adaptar `tools/visualize_simulation.py` para leer la columna `z` y, o bien filtrar/animar por
  planta, o renderizar la vista isométrica 45° apilando plantas.
- Posiblemente un script nuevo para la vista 3D.

### 9. Escenario a simular (entrega)
Construir el escenario **Escuela** (≥2 plantas, aulas, pasillos, escaleras, recreo con kiosco) y
los dos sub-escenarios del enunciado:
- **Evacuación** — input: capacidad total (N agentes); observable: distribución de tiempos de
  evacuación; escalar: tiempo promedio/máximo.
- **Ingreso** — input: caudal (los Nmax agentes distribuidos en 1/5/10 min); observable:
  población vs tiempo en una zona (p. ej. antes de la escalera principal); escalar: ocupación
  máxima/promedio.
Hay builders de escenarios viejos en `tools/scenarios-builders/` (de los grupos anteriores) que
sirven de referencia de cómo se generan los CSV, pero **no forman parte de este TP**.

---

## Orden de ataque sugerido

1. `core/Vec2`→`Vec3` + `AgentState.z` (cambio base, rompe y arregla en cascada).
2. `input/` deja de descartar `z` + define y parsea **escaleras**.
3. `Geometry` expone plantas + escaleras.
4. `Graph` 3D: grafo por planta unido por escaleras, A* con heurística euclídea 3D.
5. CIM por planta (+ test de paredes/vértices como vecinos).
6. CPM: `z` en escaleras + velocidad reducida + paredes de la planta actual.
7. Output con `z` + animación 3D.
8. Escenario Escuela + scripts de barrido y gráficos.

> Antes de tocar un módulo, conviene leer su implementación concreta y sus tests
> (`src/test/...`). Los tests de escenarios `A`–`I` (`ScenarioBPaseoComprasSmokeTest`, etc.) son
> de los grupos anteriores y pueden eliminarse junto con sus builders (ver
> `LIMPIEZA_RASTROS_GRUPOS.md`).
