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

