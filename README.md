# sim-peatonal-extended-3D

Trabajo Práctico Final de **Simulación de Sistemas** — ampliación del simulador de dinámica
peatonal a **3D**: mapas con más de una planta conectadas por **escaleras**. Escenario de estudio:
**Escuela** (2 plantas, aulas, pasillos, escaleras switchback, recreo con kiosco). Modelo físico:
**Contractile Particle Model (CPM)**.

El informe de la entrega está en [`informe/informe.pdf`](informe/informe.pdf). Las decisiones de
arquitectura están registradas en [`.claude/DECISIONES.md`](.claude/DECISIONES.md) (D1–D21).

---

## Requisitos
- **Java 21** (LTS) y **Maven 3.9+**.
- Para la visualización y los gráficos: `pip install matplotlib pillow numpy` (Python 3).

## Build y tests
```bash
mvn clean compile     # compilar
mvn test              # tests (JUnit 5 + AssertJ) — 141 verdes
mvn package           # empaqueta target/simped-1.0.0-SNAPSHOT.jar
```

## Correr una simulación
Entry point `ar.edu.itba.simped.App`, argumentos posicionales:
```
App <scenarioDir> <outputFile> [om]
```
`om` es siempre **cpm** (el SFM fue eliminado; el argumento se conserva por compatibilidad).

El escenario `scenarios/example/` es Formato A (CSV puro) y corre con el jar directo:
```bash
java -jar target/simped-1.0.0-SNAPSHOT.jar scenarios/example out/output.csv cpm
```

Los escenarios en **Formato B** (`parameters.json`, p. ej. la Escuela) necesitan Jackson en el
classpath (el jar no es fat-jar). Se corren armando el classpath con las dependencias:
```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=out/.cp.txt
java -Dsimped.seed=1 -cp "target/classes:$(cat out/.cp.txt)" \
     ar.edu.itba.simped.App scenarios/escuela out/escuela.csv cpm
```
La semilla reproducible se pasa por `-Dsimped.seed=<n>`.

## Visualización
```bash
# Vista 3D a 45° con las plantas apiladas (paredes a su z, agentes en (x,y,z), escaleras con peldaños):
python tools/visualize_simulation_3d.py --scenario scenarios/escuela --output out/escuela.csv \
  --out out/escuela_3d.mp4 --stride 3

# Vista 2D de una sola planta (paredes + agentes de esa z):
python tools/visualize_simulation.py --scenario scenarios/escuela --output out/escuela.csv \
  --floor 3 --out out/escuela_p1.gif
```
El escenario **Escuela** se genera con `python tools/scenarios-builders/build_escuela.py`.

---

## Reproducción de los resultados del informe

```bash
# 0) Compilar + classpath
mvn clean test
mvn -q dependency:build-classpath -Dmdep.outputFile=out/.cp.txt

# 1) Baseline "día escolar" + animación 3D
python tools/scenarios-builders/build_escuela.py
java -Dsimped.seed=1 -cp "target/classes:$(cat out/.cp.txt)" \
     ar.edu.itba.simped.App scenarios/escuela out/escuela.csv cpm
python tools/visualize_simulation_3d.py --scenario scenarios/escuela \
     --output out/escuela.csv --out out/escuela_3d.mp4 --stride 3

# 2) Sub-escenario Evacuación (input N; observable: distribución de t_evac)
python tools/sweep_run.py --mode evacuacion --values 40,80,120 --seeds 1 --om cpm
python tools/plot_evacuacion.py --sweep-dir out/sweeps/evacuacion --seed 1 --out-prefix out/evac

# 3) Sub-escenario Ingreso (input caudal 1/5/10 min; observable: población vs t en una zona)
python tools/sweep_run.py --mode ingreso --values 1,5,10 --seeds 1 --om cpm
python tools/plot_ingreso.py --sweep-dir out/sweeps/ingreso --seed 1 --out-prefix out/ingreso

# 4) Estudio complementario: Ingreso variando la cantidad de agentes (Nmax)
python tools/run_ingreso_nmax.py --nmax 60,120,180 --window 5 --seed 1

# 5) Mapas de calor (densidad + tiempos de evacuación por origen)
python tools/heatmaps.py --scenario scenarios/escuela \
     --output out/sweeps/evacuacion/v120/seed1/output.csv \
     --kind densidad --out out/heatmap_densidad_evac.png --title "Densidad — Evacuación (N=120)"
python tools/heatmaps.py --scenario scenarios/escuela \
     --output out/sweeps/evacuacion/v120/seed1/output.csv \
     --kind tevac --out out/heatmap_tevac.png --title "Tiempo de evacuación por origen (N=120)"
```

Herramientas Python en `tools/`: `sweep_run.py` / `sweep_lib.py` (barrido reproducible + carga de
métricas), `plot_evacuacion.py` / `plot_ingreso.py` (observables), `run_ingreso_nmax.py` (barrido por
Nmax), `heatmaps.py` (mapas de calor), `visualize_simulation*.py` (animaciones).

---

## Arquitectura

Diseño hexagonal: los **puertos** (interfaces) viven en `core/ports/` y cada módulo tiene su
implementación. `App.java` cablea todo a mano. El loop maestro es `SimulationDriverImpl`.

| Carpeta | Puerto (`core/ports/`) | Rol |
|---|---|---|
| `core/`, `core/ports/` | — | Tipos compartidos (`Vec2`, `Vec3`, `Wall`, `Stairs`, `AgentState`, `AgentProfile`…) + contratos |
| `simulation/` | `SimulationDriver`, `OutputSink` | Loop temporal y escritura de output |
| `input/` | `ScenarioLoader` | Carga de escenarios (CSV Formato A / JSON Formato B) |
| `scenario/` | — | Cableado de agentes/servers (`AgentAssembler`, `ServersWiring`) |
| `agent/plan/` | `Plan` | Lista ordenada de tasks del agente |
| `agent/sensors/` | `Sensors` | Detección de arrival/approach |
| `agent/statemachine/` | `StateMachine` | Behavior state + foot-target |
| `agent/preom/` | `PreOM` | Ruteo (consulta al Graph) |
| `agent/om/` | `OperationalModel` | Física (CPM) |
| `environment/geometry/` | `Geometry` | Walls, Locations, Exits, GeneratorZones, ServerZones, Stairs — por planta |
| `environment/graph/` | `Graph` | Malla de navegación 3D (por planta + unión por escaleras) + A\* euclídeo 3D |
| `environment/neighbors/` | `NeighborsIndex` | CIM por planta + puente de escalera |
| `environment/generator/` | `PedestrianGenerator` | Spawneo de agentes |
| `environment/servers/` | `Server` | Servidores/colas (kiosco, aulas) |

`AgentState` (en `core/`) es la única fuente de verdad del estado del agente: lo escriben el
generador (al spawnear) y el OM (cada `dt`); lo leen Sensors y el CIM. Guarda `x, y, z, vx, vy,
radius, BehaviorState, AgentProfile` — la `z` es la coordenada 3D (planta / altura en escalera).

---

## Formato de escenarios

Un escenario es un directorio con CSV. Hay un ejemplo completo en `scenarios/example/`.

**Separador:** coma + espacio opcional (`, `). Primera línea = header. Coordenadas en metros. La `z`
es la **planta** de cada elemento (`scenarios/example/` es de una sola planta `z=0`;
`scenarios/escuela/` usa dos plantas `z=0` y `z=3` conectadas por escaleras).

### Archivos de geometría

| Archivo | Columnas |
|---|---|
| `WALLS.csv` | `x1, y1, z1, x2, y2, z2` |
| `EXITS.csv` | `block_name, x1, y1, z1, x2, y2, z2` |
| `GENERATORS.csv` | `block_name, x1, y1, z1, x2, y2, z2` |
| `TARGETS.csv` | `block_name, figure_type, radius, x1, y1, z1, x2, y2, z2` |
| `SERVERS.csv` | `block_name, x1, y1, z1, x2, y2, z2` — sufijos `_SERVER`, `_QUEUE000`, … |
| `STAIRS.csv` *(opcional, 3D)* | `block_name, x1, y1, z1, x2, y2, z2, width[, speed_factor]` — escalera: eje pie `(x1,y1,z1)` → tope `(x2,y2,z2)` con `z1≠z2` (ver D4). Sin este archivo el escenario es de una sola planta. |

Los escenarios en **Formato B** llevan además un `parameters.json` con los planes, generadores y
servers (como el de la Escuela; ver `tools/scenarios-builders/build_escuela.py`). El escenario
`scenarios/example/` es Formato A y usa además `SIM_PARAMS.csv`, `GENERATOR_PARAMS.csv`,
`SERVER_PARAMS.csv` y `PLANS.csv`.

---

## Formato de output

Archivo plano único (CSV) `out/output.csv`. Una fila por agente por output step:
```
tout; x; y; z; vx; vy; state; id
```
- Separador `; `, sin header, `Locale.US` (punto decimal). `tout` con `%.4f`; coordenadas/velocidades con `%.6f`.
- `z` = planta / altura en escalera (se agregó para 3D, ver D10 en `.claude/DECISIONES.md`).
- `state` = nombre del enum `BehaviorState`. `id` = id del agente (para trazar trayectorias).
