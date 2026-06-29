# sim-peatonal-extended-3D

Trabajo Práctico de **Simulación de Sistemas** — Ampliación del simulador de dinámica peatonal desarrollado en clase para soportar mapas en 3D.

---

## Cómo trabajar con el repo

### Requisitos
- Java 21 (LTS).
- Maven 3.9+.

### Build
```
mvn clean compile
```
### Tests
```
mvn test
```

### Empaquetar y correr
```
mvn package
java -jar target/simped-1.0.0-SNAPSHOT.jar
```

---

## Módulos y contratos
| Carpeta | Módulo del contract v4 | Interface a implementar (`core/ports/`) |
|---|---|---|
| `core/`, `core/ports/`, `App.java`, `pom.xml`, `scenarios/` | Tipos compartidos + contratos + esqueleto | — |
| `simulation/` | 2. SimulationLoop, 3. Output | `SimulationDriver`, `OutputSink` |
| `input/` | 1. UserInput (loader) | `ScenarioLoader` |
| `agent/plan/` | 4.2 Plan | `Plan` |
| `agent/sensors/` | 4.3 Sensors | `Sensors` |
| `agent/statemachine/` | 4.4 StateMachine | `StateMachine` |
| `agent/preom/` | 4.5 PreOM | `PreOM` |
| `agent/om/` | 4.6 OperationalModel | `OperationalModel` |
| `environment/geometry/` | 5.1 Geometry (Walls/Exits/Locations/GeneratorPos/ServerPos) | `Geometry` |
| `environment/graph/` | 5.2 Graph | `Graph` |
| `environment/neighbors/` | 5.3 Neighbors (CIM) | `NeighborsIndex` |
| `environment/generator/` | 5.4 PedestrianGenerator | `PedestrianGenerator` |
| `environment/servers/` | 5.5 Servers | `Server` |

`AgentState` (4.1) es la única fuente de verdad del estado del agente y vive en `core/`. La escriben PG (al spawnear) y OM (cada dt); la leen Sensors y CIM.

---

## Formato de escenarios

Un escenario es un directorio con CSV. Hay un ejemplo completo en `scenarios/example/`.

**Separador:** coma + espacio opcional (`, `). Primera línea = header. Coordenadas en metros. En el ejemplo z es siempre 0.0, pero la idea es extender el simulador para trabajar en 3D.

### Archivos de geometría (definidos por la cátedra)

| Archivo | Columnas |
|---|---|
| `WALLS.csv` | `x1, y1, z1, x2, y2, z2` |
| `EXITS.csv` | `block_name, x1, y1, z1, x2, y2, z2` |
| `GENERATORS.csv` | `block_name, x1, y1, z1, x2, y2, z2` |
| `TARGETS.csv` | `block_name, figure_type, radius, x1, y1, z1, x2, y2, z2` |
| `SERVERS.csv` | `block_name, x1, y1, z1, x2, y2, z2` — convención de naming: sufijos `_SERVER`, `_QUEUE000`, `_QUEUE001`, ... |

### Archivos de parámetros 

#### `SIM_PARAMS.csv`
```
key, value
dt, 0.05
dt_out, 0.1
t_total, 60.0
```

#### `GENERATOR_PARAMS.csv`
Una fila por `block_name` de `GENERATORS.csv`.
```
block_name, mode, rate_or_count, agent_type, plan_template
```
- `mode`: `flowrate` (rate en ped/s) o `instant_occupation` (count).
- `agent_type`: etiqueta libre.
- `plan_template`: referencia a `template_name` de `PLANS.csv`.

#### `SERVER_PARAMS.csv`
Una fila por `block_name` `_SERVER` de `SERVERS.csv` (no por las posiciones `_QUEUE`).
```
block_name, type, service_time_mean, service_time_std
```
- `type`: `queue` | `classroom` | `broadcast`.

#### `PLANS.csv`
Templates de plan referenciados por `GENERATOR_PARAMS.plan_template`. Una fila por paso.
```
template_name, step_order, target_type, target_block_name
```
- `target_type`: `TARGET` | `SERVER` | `EXIT`.
- `target_block_name`: referencia al `block_name` correspondiente en `TARGETS.csv`, `SERVERS.csv` o `EXITS.csv`.

### Faltantes conocidos (a decidir en grupo)

- `EXITS.csv` no tiene `max_flow_rate` (mencionado en el contract §5.1.3).
- `TARGETS.csv` no tiene `dwell_time t_l` (mencionado en el contract §5.1.2).

Recomendación: crear `EXIT_PARAMS.csv` y `TARGET_PARAMS.csv` aparte, manteniendo intactos los CSV originales de geometría.

---

## Formato de Output

Archivo plano único `output.txt`. Una fila por agente por output step.

```
tout; x; y; vx; vy; state
```

- Separador: `;`.
- `tout` con `%.4f`.
- `state` = nombre del enum `BehaviorState`.
- Sin header (a confirmar con el grupo).

