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
