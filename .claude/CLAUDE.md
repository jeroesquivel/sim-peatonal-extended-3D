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
3. **Usar siempre CPM** (Contractile Particle Model) como modelo físico. El SFM fue
   **eliminado** del código (D7): ya no existe `SfmaOperationalModel` y `App` construye
   siempre `CpmOperationalModel`. El argumento CLI `om` se conserva por compatibilidad, pero
   cualquier valor resuelve a CPM.
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
- `om`          — vestigial tras D7 (SFM eliminado). Prioridad de lectura: arg posicional >
  env var `SIMPED_OM` > default `cpm`, pero `App` **siempre** construye CPM sin importar el
  valor. Se mantiene por compatibilidad de CLI.

Correr (el escenario `example` es Formato A puro CSV y no necesita Jackson):

```bash
java -jar target/simped-1.0.0-SNAPSHOT.jar scenarios/example out/output.csv cpm
```

Para escenarios en **Formato B** (`parameters.json`) hace falta Jackson en el classpath
(el jar no es fat-jar): correr con `mvn exec` o armando el classpath con las deps.

### Visualización

Requiere `pip install matplotlib pillow`. Los scripts leen el output con `z` (formato D10:
`tout; x; y; z; vx; vy; state; id`).

- `tools/visualize_simulation.py` — anima `out/output.csv` sobre el escenario (2D, círculos
  coloreados por estado). Opción `--floor <z>` para animar **una sola planta** (paredes + agentes
  de esa `z`).
- `tools/visualize_simulation_3d.py` — **vista 3D a 45°** con las plantas apiladas (paredes a su
  `z`, agentes como puntos en `(x,y,z)`, escaleras como segmento inclinado). Lee `STAIRS.csv` si
  existe. Opción `--stride N` para submuestrear 1 de cada N frames (outputs largos).
- `tools/animate_run.py` — animación alternativa.
- Debug de ruteo: `-Dsimped.hopLog=out/hops.csv` registra cada `nextVisibleHop` para
  superponer los hops sobre el GIF.

Flujo completo del escenario **Escuela** (2 plantas, Formato B → correr con `mvn exec`):

```bash
python tools/scenarios-builders/build_escuela.py                 # genera scenarios/escuela/
mvn exec:java -Dexec.mainClass=ar.edu.itba.simped.App \
  -Dexec.args="scenarios/escuela out/escuela.csv cpm"            # corre la simulación
python tools/visualize_simulation_3d.py --scenario scenarios/escuela \
  --output out/escuela.csv --out out/escuela_3d.gif --stride 5   # vista 3D apilada
python tools/visualize_simulation.py --scenario scenarios/escuela \
  --output out/escuela.csv --floor 3 --out out/escuela_p1.gif    # solo P1 (z=3) en 2D
```

---

## Arquitectura

Diseño hexagonal: los **puertos** (interfaces) viven en `core/ports/` y cada módulo tiene su
implementación. `App.java` cablea todo a mano (no hay framework de DI). El loop maestro es
`SimulationDriverImpl`.

### Estado del agente — `core/AgentState`
Única fuente de verdad del estado kinemático y de comportamiento. **Mutable.** Lo escribe el
generador al spawnear y el OperationalModel cada `dt`; lo leen Sensors y el índice de vecinos.
Guarda `x, y, z, vx, vy, radius, BehaviorState, AgentProfile` (la `z` se agregó en el paso 1; sin
`vz`, ver D2). `position()` devuelve `Vec3`; `setPosition(x,y)` conserva la `z` (la cambia solo el
avance por escalera).

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
| `agent/om/` | `OperationalModel` | Física (CPM — único modelo; SFM eliminado, D7) |
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
`z1==z2` y se usa ese valor; si difieren se emite un warning y se toma `z1`. El grafo (paso 4)
ya construye desde `Geometry` y respeta la `z`.

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
Una fila por agente por output step: `tout; x; y; z; vx; vy; state; id` (sep `; `, sin header,
`Locale.US`). La `z` (planta / altura en escalera) va junto a `x, y` (ver D10). Los scripts
Python leen este formato.

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

---

## Estado del trabajo y pendientes (retomar acá)

**Última sesión: 2026-07-09 (videos a YouTube + links en la ppt).** Se completó el pendiente de
video de la presentación:
- **`anim_evac_n500.mp4` renderizado** — la nota previa decía "MP4 regenerados (incluye
  anim_evac_n500)" pero el `.mp4` **no existía** (solo estaba el `.png`). Se generó desde
  `out/sweeps/evacuacion/v500/seed1/output.csv` con
  `tools/visualize_simulation_3d.py --stride 4 --dpi 110` (389 frames, 1.0 MB), mismos parámetros
  que los otros evac.
- **4 links de YouTube cargados** en los macros `\videolink*` de
  `presentacion/SdS_TPFinal_2026Q1G05_Presentacion.tex` (son **4**, no 5 — `anim_evac_n120.mp4`
  quedó como sobrante sin referenciar): EvacBaja=`youtu.be/GSnIwkEzUjQ` (n40),
  EvacAlta=`youtu.be/9H_JlijpAMA` (n500), IngresoUno=`youtu.be/_g14NaB1mp8` (t1),
  IngresoDiez=`youtu.be/9h85AAXzKDo` (t10). Dos IDs tienen `_`, que rompe `\href`/`\url`; se
  definieron dentro de un grupo con ``\catcode`\_=12`` + `\gdef` (ver cabecera del `.tex`).
- **Ambos PDFs recompilados** (entregable + `tpfinal_presentable`, 2 pasadas c/u, exit 0, sin
  errores). Verificado: los 4 links aparecen en el texto del PDF entregable y cero
  "[link pendiente]". Auxiliares de LaTeX borrados.
- **Falta (usuario):** wiki, ensayo (~13 min) y **commit** (regla 1). Los links ya no son pendiente.

**Sesión 2026-07-09 (D25: re-análisis al rango post-D24).** Con el fix D24 el barrido
N≤120 quedó corto: se extendió Evacuación a **N∈{40,80,120,200,300,400,500}×5 seeds** (max_time
escala con N en el builder, D25) y el complementario a **Nmax∈{60..300}×5**. Resultados nuevos:
evacuación completa en todo el rango (evacuados ≥N−1 hasta 500); **dos regímenes** (suave hasta
N≈200; escaleras saturadas desde N≈300 con el máximo despegándose: 129±9→244±103 s, y dispersión
máxima en el cruce — en N=300 una seed formó un atasco transitorio de 427 s que se disolvió);
Nmax: supralineal hasta saturar el kiosco (~180) y ~lineal después (78.8±2.3 / 104.0±3.7 para
240/300). Ingreso principal: idéntico (kiosco-dominado). Informe **15 páginas** (tablas 7/5 filas,
análisis de regímenes, conclusiones) y ppt **26 páginas** actualizados (barrido {40..500}, anim
"alta"=N500 nueva, hist de slide con subset {40,120,500} vía `plot_evacuacion --values`, hspace
fix en el histograma). Suite 143 verdes. **Videos/links: ya resueltos** (ver nota de sesión al
tope). **Falta (usuario):** wiki, ensayo, commit.

**Sesión 2026-07-07 (informe + ppt: cambios del simulador y porqués).** La fuente de verdad
de las decisiones es [`DECISIONES.md`](./DECISIONES.md) (**D1–D23**); la auditoría contra la wiki
está en [`REVISION_WIKI.md`](./REVISION_WIKI.md) y el plan vigente en
[`PLAN_ENTREGA.md`](./PLAN_ENTREGA.md) (Fase 2, Tasks 8–14).

**Hecho (2026-07-07):** informe y presentación ahora explican todos los cambios del simulador y sus
porqués (pedido del usuario), con verificación adversarial:
- **Informe §Implementación ampliado** (11→**14 páginas**, 0 errores/refs sin resolver): porqués por
  módulo con alternativas descartadas (D1–D10), párrafo nuevo **"La integración multiplanta"**
  (D11/D16/D18: la z perdida en silencio, lección de defaults z=0), párrafo nuevo del **livelock de
  jamba + contacto a rmin** (D14→D17, con 20.1%→5.5% y 2/30→0/30) y **"Reproducibilidad"** (D23).
- **Presentación** (25→**26 páginas** ambas versiones, sin overfull): tabla de módulos con columna
  "Por qué", frame nuevo **"Lo que sólo aparece con dos plantas reales"** (gaps de integración,
  cruce de arista de escalera, salto de z, livelock→rmin, lección), bullets de híbrido y de semilla.
  Estructura por estudio intacta (re-verificada contra `lecciones_correcciones.md`).
- **Corrección verificada con datos** (análisis de los 15 output.csv de evacuación): los ~1.4
  no-evacuados/corrida (1.4/1.6/1.4 para N=40/80/120) NO están en jambas de aula (0/22 — eso lo
  arregló D17): 14/22 oscilan en la **boca de la escalera SUR de P1** (contra la baranda, z=3.0) y
  7/22 en la **jamba del portón norte** de PB; 1/22 (N=120 seed1) es un caso borde del criterio (el
  último agente en tránsito: todos evacuaron a t≈143 s y el output no escribe cuadros vacíos).
  Redacción corregida en Resultados+Conclusiones del informe y 2 bullets del ppt (nota en D17).
- Suite re-verificada: **143 tests, 0 fallos, 0 skipped** (el "0 omitidos" del informe es exacto).

**Hecho (2026-07-03/04):**
- **Auditoría completa contra la wiki de SDS** (`SDS_Obsidian/`, en particular
  `lecciones_correcciones.md`): hallazgos y fixes en `REVISION_WIKI.md`.
- **Estadística con 5 realizaciones** (semillas 1–5) en TODOS los resultados: barridos re-corridos,
  `plot_evacuacion.py`/`plot_ingreso.py`/`run_ingreso_nmax.py` con `--seeds all` (agregación
  cross-seed, errorbars capsize=4, banda ±σ en población-vs-t, viridis+colorbar para inputs
  numéricos, dpi=200, sin seed en títulos).
- **D22 — modo crisis**: `max_velocity` del Formato B ahora se honra (era código muerto) → perfil
  por generador; Evacuación con **vd=ve=2.0 m/s**; dt acotado por el perfil más rápido (0.0375 s en
  evac). Verificado: v mediana 2.00 m/s en el plano.
- **D23 — semilla a todos los streams**: `Seeds.mixOr` en servers (service-time, softmax) y
  selección (salida/aula/dwell). Sin `simped.seed` el comportamiento es idéntico (fallback a las
  constantes históricas). **Suite: 143 tests verdes** (2 nuevos).
- **Informe reestructurado** a la estructura de la cátedra (Introducción con observables definidos
  por ecuación / Implementación sin valores numéricos / Simulaciones con parámetros y 5
  realizaciones / Resultados con \ref antes de analizar / Conclusiones compactas / Referencias:
  Baglietto-Parisi 2011, Martin-Parisi 2024, Helbing 2000). Números finales media±σ cargados.
  **PDF: 11 páginas, 0 refs sin resolver** (`informe/informe.pdf`).
- Resultados finales (5 seeds): evac prom 48.2±0.6 / 59.6±1.2 / 66.7±3.6 s y máx 93.5 / 121.6 /
  151.2 s para N=40/80/120 (distribución **bimodal** PB/P1); ingreso pico 53.4±1.8 / 21.0±3.6 /
  5.0±0.7 para Ta=1/5/10 min; Nmax pico 4.8 / 21.0 / 51.6 para 60/120/180. Heatmaps regenerados.
- **Wiki actualizada**: `SDS_Obsidian/wiki/tps/TP_FINAL.md` reescrita al enunciado real (3D), con
  índice y log al día.

**Pendiente (ver PLAN_ENTREGA Fase 2):** presentación oral Beamer (Task 11) y empaquetado del código
a entregar (Task 12, solo motor). Opcional: más puntos + log-log (Task 13).

**Task 11 — avance (2026-07-06):** mockup completo en `presentacion/` (mecanismo dual de TP5:
`SdS_TPFinal_2026Q1G05_Presentacion.tex` = entregable con póster+link, `tpfinal_presentable.tex` =
oral con MP4 embebidos vía media9; ambos PDFs compilan, 25 páginas). 4 animaciones renderizadas de
corridas reales (`presentacion/videos/anim_{evac_n40,evac_n120,ingreso_t1,ingreso_t10}.{mp4,png}`).
Estructura verificada contra `lecciones_correcciones.md` (animación→serie→escalar por estudio, sin
índice, citas inline, ≤2 animaciones/estudio) y números verificados contra `informe.tex` (0 errores).
**Falta (usuario):** subir los 4 MP4 a YouTube y pegar los links en los macros `\videolink...`,
ensayar ~13 min (18 frames de contenido; candidatos a recorte: las 2 "huella espacial" y el
complementario Nmax). Ver `presentacion/README.md`.

---

**Sesión 2026-07-01 (histórico):**
- **Baseline "día escolar" con generación FINITA** (D13): el `inactive_time=0` del D12 hacía que el
  generador spawneara para siempre (edificio nunca se vaciaba, sólo 18/100 evacuaban). Se cambió a un
  **burst matinal finito** (`ARRIVAL_WINDOW=60`, `inactive_time=1e6`, `max_time=240`, `DOOR=2.4`) ⇒ ~30
  agentes. Resultado validado: **30/30 asisten clase, ~22 suben a P1 y bajan por escalera, ~23/30
  evacúan**; la población sube a 30 (t≈60–90) y baja a ~13 (t=200). Animaciones regeneradas: 3D apilada
  + 2D por planta (`--stride` nuevo en `tools/visualize_simulation.py`).
- **Comportamiento raro diagnosticado** (D14): ~1–2/30 agentes quedan oscilando en la **jamba de una
  puerta de aula** al salir hacia una salida lejana. Diagnóstico (hop-log + `vx,vy`): **no es ruteo** —
  es el CPM tratando la jamba como *contacto de pared* y aplicando escape perpendicular que pelea con el
  pull del target. Un intento de fix en `moveWithWallCheck` **no lo resuelve** (la causa está aguas
  arriba, en el modelo de fuerzas) y **se revirtió**; el CPM quedó idéntico al original (149 tests
  verdes). Mitigación parcial aplicada: puertas más anchas. **Fix real pendiente de decidir** (ver D14).
  Nota: sigue presente con el modelo CLASSROOM (afecta 2/30 al evacuar).
- **Afinado de servers/planes** (D15, D16): las aulas pasaron de TARGETs capacidad-1 a **servers
  `CLASSROOM`** (recinto colectivo). 8 en PB (`AULA_PB`) + 8 en P1 (`AULA_P1`), **planes diferenciados**
  `CLASE_PB`/`CLASE_P1` asignados ~50/50 por el generador (pool `"CLASE_PB|CLASE_P1"`) → **elimina el
  sesgo a P1**. **Timbre único** (dismissal sincronizado) a t=140. Al implementarlo se descubrió que el
  **módulo de Servers no estaba migrado a 3D**: `GeometryAssembler` tiraba la `z` del `ServerZone` (aulas
  de P1 caían a z=0) y `ServersWiring` ubicaba el target fino en la `z` del agente, no del server. **Se
  arreglaron ambos** (D16). Resultado: 13/30 suben a P1 y bajan, 26/30 asisten, timbre limpio, 28/30
  evacúan. **149 tests verdes.**

**Hecho (pasos 1–7 + 8.1):**
- Pasos **1–7** completos: `Vec3` + `AgentState.z` (D1, D2), input propaga `z` + escaleras (D3, D4),
  `Geometry` por planta (D5), grafo 3D por planta unido por escaleras (D6, D7), **CIM por planta** +
  puente de escalera (D8), **CPM 3D** (z en escaleras + velocidad reducida + paredes de la planta
  actual, D9), **output con `z`** (D10) + animación 2D-por-planta y **vista 3D** (`tools/`).
- Paso **8.1** — escenario **Escuela** construido y andando (D12): `scenarios/escuela/` (Formato B)
  generado por `tools/scenarios-builders/build_escuela.py`. Validado: ~100 agentes, ~60% sube a P1
  por las escaleras, asiste clase y baja a una salida. Suite: **149 tests verde** (8 skipped viejos).
- Durante 8.1 se arreglaron **4 bugs de integración multiplanta** que sólo se ven con 2 plantas
  reales (ver **D11**): `Task` z por candidato, selección por índice (aulas PB/P1 comparten `(x,y)`),
  grafo cruzando la arista de escalera, y `locateStair` del OM arrancando la interpolación en el pie.

**Pendiente — confirmar con el usuario (decisiones de modelado de la Escuela):**
1. **Aulas como `TARGET` con `attending_time`** (el agente llega y permanece la clase) vs. server
   `classroom` con sesiones. Hoy: TARGET con dwell.
2. **Kiosco fuera del plan baseline** (queda en la geometría del recreo, para usar en el sub-escenario
   de Ingreso/recreo) vs. incluirlo en el plan. Hoy: fuera (un único server con todos los agentes
   sería un cuello de botella).

**Pendiente — paso 8.2 (próximo): sub-escenarios + barridos y gráficos.** Sobre la misma geometría
de la Escuela, parametrizar el builder (`build_escuela.py`) para los dos casos del enunciado:
- **Evacuación** — input: capacidad total `N` (agentes ya adentro, repartidos en aulas de **ambas
  plantas**; usar generadores `instant_occupation` en las aulas o spawns por planta); observable:
  **distribución de tiempos de evacuación**; escalar: tiempo promedio/máximo. Las dos salidas
  (RECREO y EDIFICIO_PB) con `exit_selection` (los de P1 bajan por escalera).
- **Ingreso** — input: caudal (los `Nmax` agentes distribuidos en **1 / 5 / 10 min**); observable:
  **población vs. tiempo** en una zona (p. ej. antes de una escalera principal); escalar: ocupación
  máxima/promedio. Acá el kiosco del recreo entra naturalmente en el plan.
- Scripts de **barrido** y **gráficos** (variar `N` / caudal, varias corridas): ✅ **ya existen**
  en `tools/` — `sweep_run.py` + `sweep_lib.py` (motor de barrido con semillas),
  `plot_evacuacion.py` / `plot_ingreso.py` (observables/escalares) y `run_ingreso_nmax.py`
  (barrido de `Nmax`). Ver el estado 2026-07-09 al pie para los rangos ya corridos.

**Notas para retomar:**
- Formato B (Escuela) **necesita Jackson** → correr con `mvn exec:java` (no el `-jar`, que no es
  fat-jar). Ver comandos en la sección *Visualización*.
- El build del grafo de la Escuela tarda ~1 min (grilla 0.20 m sobre 60×60 en 2 plantas); es normal.
- `git status` al cierre: working tree con los cambios de la sesión **sin commitear** (regla 1: el
  usuario commitea).
