# Rastros del trabajo comunitario anterior (división por grupos)

Inventario de todo lo que en el proyecto refiere a la **organización previa repartida entre
grupos** (el TP comunitario de la cursada). Esa división **ya no aplica**: tenemos control
total del repo. Este documento existe para que puedas **limpiar** esas menciones sin perder de
vista nada. No es código a borrar a ciegas: algunos archivos son funcionales y solo arrastran
comentarios/nombres; otros son artefactos enteros de los grupos anteriores que sí pueden
eliminarse.

Vocabulario usado en esas marcas: `G0`–`G9`, `Grupo N`, `Block H`, `T3`/`T4`/`T5`/`T6`,
`contract v4`, `interface_contracts_v4`, interfaces `I1`–`I20`, "cátedra".

> Magnitud: ~240 menciones en ~94 archivos (la mayoría son referencias a las interfaces
> `I1`–`I20` y a "contract v4" dentro de comentarios de Javadoc). Abajo va agrupado por tipo.

---

## 1. Metadatos del proyecto

| Archivo | Rastro | Acción sugerida |
|---|---|---|
| `pom.xml` | Líneas 14-18: `<description>` dice "TP grupal… Arquitectura definida en Docs/general/interface_contracts_v4.txt. Esqueleto del monorepo a cargo del Grupo 6." | Reescribir la descripción para el TP 3D. |
| `pom.xml` | Comentario línea 29: "G3: Jackson para parsear parameters.json…" | Quitar el prefijo `G3:`. |
| `README.md` | Tabla "Módulos y contratos" mapea cada carpeta a un "módulo del contract v4" e "interface a implementar"; menciones a "G3/G9", "a decidir en grupo", "a confirmar con el grupo". | Reescribir para el alcance 3D (la tabla de módulos del nuevo `CLAUDE.md` ya no referencia grupos). |

---

## 2. Comentarios "Implementación original del Grupo N" (archivos funcionales)

Son clases que **se usan** y solo arrastran la autoría en el Javadoc. Limpiar el comentario,
**no borrar la clase**.

| Archivo | Línea aprox. | Texto |
|---|---|---|
| `agent/plan/PlanImpl.java` | 11 | "Implementación original del Grupo 2, adaptada al port." |
| `agent/statemachine/StateMachineImpl.java` | 25 | "Implementación original del Grupo 2, refactoreada…" |
| `agent/sensors/SensorsImpl.java` | 13 | "…Implementación original del Grupo 2, adaptada al port." |
| `agent/preom/CpmPreOM.java` | varias | "implementation for Group 7", "pedido por G8" |
| `agent/om/CpmParameters.java` | 7, 22 | "Implementación original del Grupo 7", "Experimento Grupo 7" |
| `agent/om/CpmOperationalModel.java` | 37, 54, 104 | "experimento Grupo 7" (regla de prioridad + override de rmin) |
| `core/Task.java` | 8 | "Implementación original del Grupo 2." |
| `environment/neighbors/Wall.java` | 7 | "Implementación original del Grupo 7." |
| `environment/neighbors/BruteForceNeighborsIndex.java` | 14 | "Implementación original del Grupo 7, adaptada al port." |

> Nota: `agent/om/CpmOperationalModel.java` mezcla la física CPM (que SÍ se usa) con dos
> "experimentos del Grupo 7" activables por env var (`SIMPED_CPM_PRIORITY`, `SIMPED_CPM_SET`).
> Esos experimentos pueden quitarse si no los necesitás; el CPM base queda igual.

---

## 3. Referencias a "contract v4" e interfaces `I1`–`I20`

Casi todos los puertos en `core/ports/` y muchas implementaciones documentan su rol como
"sub-módulo X.Y del contract v4" y citan interfaces `I1`–`I20` (ej. `I16q`, `I13b`, `I17`).
Es la nomenclatura del reparto anterior.

Archivos con estas marcas (Javadoc, no afectan la lógica):
- `core/ports/`: `Agent`, `Environment`, `Geometry`, `Graph`, `NeighborsIndex`,
  `OperationalModel`, `OutputSink`, `PedestrianGenerator`, `Plan`, `PreOM`, `ScenarioLoader`,
  `Sensors`, `Server`, `ServerSignal`, `SimulationDriver`, `StandardServerSignal`,
  `StateMachine`, `TaskTarget`.
- `core/`: `AgentState`, `BehaviorState`, `Exit`, `Location`, `Segment`, `Wall`,
  `GeneratorZone`, `GeneratorRawParams`, `LoadedScenario`, `PlanTemplate`, `ServerZone`,
  `ServerType`, `ServerParams`, `SimulationParameters`, `Task`, `TaskStep`, `TaskType`,
  `LegacyExtras`, `validation/ValidationCode`.
- Implementaciones: `App.java`, `simulation/SimulationDriverImpl`, `simulation/ServerStep`,
  `scenario/AgentAssembler`, `scenario/ServersWiring`, `environment/EnvironmentImpl`,
  `environment/graph/*`, `environment/generator/*`, `environment/servers/*`, `input/*`, etc.

Acción: opcional. Si querés despersonalizar la documentación, reemplazar las referencias a
interfaces `Ixx` y "contract v4" por descripciones funcionales. Es cosmético — no cambia el
comportamiento. Conviene hacerlo en pasada, no bloquea el trabajo 3D.

---

## 4. Clases `Stub*` (esqueletos de arranque del reparto)

Implementaciones tridente/placeholder que cada grupo dejó para arrancar antes de tener la real.
Casi todas están **reemplazadas por la implementación concreta** y solo se usan (si acaso) en
tests. Revisar uso real antes de borrar:

`agent/StubAgent`, `agent/om/StubOperationalModel`, `agent/plan/StubPlan`,
`agent/sensors/StubSensors`, `agent/statemachine/StubStateMachine`, `agent/preom/StubPreOM`,
`environment/StubEnvironment`, `environment/geometry/StubGeometry`,
`environment/graph/StubGraph`, `environment/neighbors/StubNeighborsIndex`,
`environment/generator/StubPedestrianGenerator`, `input/StubScenarioLoader`,
`simulation/StubSimulationDriver`, `simulation/StubOutputSink`.

> Cuidado: **`environment/graph/StubGraph` SÍ se usa en producción** — `App.java` lo instancia
> como el `Graph` real (`StubGraph.fromScenarioFiles(...)`). El nombre "Stub" es engañoso: es
> el punto de entrada del módulo Graph. No borrarlo; en todo caso renombrarlo.

---

## 5. Escenarios y herramientas de los grupos anteriores (artefactos completos)

Estos son escenarios de demo del TP comunitario (Paseo de Compras, Terminal de Tren,
Aeropuerto, Discoteca, Microestadio, Universidad, Supermercado, Vía Pública, Tren Subte). **No
forman parte del TP 3D** (que pide el escenario "Escuela"). Pueden eliminarse o conservarse solo
como referencia.

### Builders de escenarios (`tools/scenarios-builders/`)
`build_a_tren_subte.py`, `build_b_paseo_compras.py`, `build_c_terminal_tren.py`,
`build_d_aeropuerto.py`, `build_e_discoteca.py`, `build_f_microestadio.py`,
`build_g_universidad.py`, `build_h_supermercado.py`, `build_i_via_publica.py`.

### Smoke tests de esos escenarios (`src/test/.../input/`)
`ScenarioBPaseoComprasSmokeTest`, `ScenarioCTerminalTrenSmokeTest`,
`ScenarioDAeropuertoSmokeTest`, `ScenarioEDiscotecaSmokeTest`, `ScenarioFMicroestadioSmokeTest`,
`ScenarioGUniversidadSmokeTest`, `ScenarioHSupermercadoSmokeTest`, `ScenarioIViaPublicaSmokeTest`.

### Visualizador del Grupo 4
`tools/g4-visualizer/visualize_sfma.py` — atado al SFM del Grupo 4 (no se usa CPM acá).

### Referencias DXF legacy
`tools/dxf-parser/legacy_reference/` (`Leedme.txt`, `dxf_read.py`) — material heredado.

---

## 6. Documentación interna de los grupos (`.md` dentro de `src/`)

Notas de diseño/integración que los grupos dejaron junto a su código. Suelen describir el
reparto y los pendientes de integración entre grupos:

- `environment/generator/generator.md` (menciona "Grupo 2 nos pasa…")
- `environment/generator/integration-geometry.md`
- `environment/graph/GEOMETRY_INTEGRATION.md` (integración Graph↔Geometry "grupo T4")
- `environment/graph/README.md`
- `environment/graph/tests/README.md`

Acción: revisar si algo sigue siendo útil técnicamente; el resto (lo que solo coordina entre
grupos) se puede borrar.

---

## 7. Código muerto del modelo NO usado (SFM)

El enunciado pide **usar siempre CPM**. El modelo SFM es del reparto anterior (Grupo 4) y queda
como código no usado en este TP:
- `agent/om/SfmaOperationalModel.java` ("Implementación del Grupo 4…")
- `src/test/.../agent/om/SfmaOperationalModelTest.java`
- `tools/g4-visualizer/visualize_sfma.py`
- En `App.java`: la rama `sfm`, el `SFM_DEFAULT_PROFILE` y el default `om = "sfm"`.

Acción sugerida: si se decide eliminar SFM, sacar la rama de `App.java` y dejar CPM como único
modelo (ver regla "usar siempre CPM" en `CLAUDE.md`).

---

## 8. Nombre `LegacyExtras` y campos "legacy"

`core/LegacyExtras.java` y `LoadedScenario.legacy()` exponen campos del "Formato B" heredado
(`evacuateAt`, `blueprintName`). El nombre "legacy" viene del reparto. Es funcional; renombrar
es opcional. Ojo: `evacuateAt` puede ser útil para el escenario de **Evacuación** del TP.

---

## Resumen de prioridades de limpieza

1. **Cosmético / inmediato:** `pom.xml` y `README.md` (descripción y tabla de grupos).
2. **Artefactos eliminables:** escenarios A-I (builders + smoke tests), `g4-visualizer`,
   `dxf-parser/legacy_reference`, `.md` de coordinación entre grupos.
3. **Comentarios "Grupo N" / "contract v4 / Ixx":** limpiar en pasada, no urgente.
4. **Decisión de diseño:** eliminar SFM si se confirma CPM-only; renombrar `StubGraph` y los
   demás `Stub*` que en realidad son las implementaciones reales.