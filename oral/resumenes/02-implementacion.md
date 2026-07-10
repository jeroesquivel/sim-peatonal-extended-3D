# Resumen §Implementación — la ampliación a 3D, módulo por módulo

**Hub** técnico: qué cambió en cada módulo y por qué, con links al detalle. Era 2D (`Vec2` `(x,y)` en cascada). A 3D = representar la `z` (planta + altura en escalera) en vecindad, física, grafo y salida. La escalera une plantas.

> **Híbrido**: `Vec3 (x,y,z)` para posición/velocidad, `Vec2` para la dinámica horizontal. La `z` cambia sólo en la escalera, por interpolación, sin velocidad vertical. Así no se reescribió la geometría planar ni el núcleo del CPM (**D1**).

## Tabla de cambios por módulo (informe, Tabla 1)

| Módulo | Qué cambió en 3D | Detalle con snippets |
|---|---|---|
| **Tipos base** | `Vec3` posición/velocidad; `AgentState.z`. `z` interpolada por avance (sin `vz`). | [código: tipos base e I/O](../codigo/01-tipos-base-io.md) · [blindaje](../blindaje/q-tipos-io.md) |
| **Geometría** | Cada pared/salida/aula/generador/server con su planta `z`. Nuevo `Stairs` en `STAIRS.csv`. | [código: vecinos/geometría](../codigo/04-vecinos-cim-geometria.md#7-geometría-por-planta-d5) · [tipos base](../codigo/01-tipos-base-io.md) |
| **Grafo de navegación** | Malla por planta unida por aristas de escalera (pie↔tope). A* euclídeo 3D; visibilidad/FVP por planta. | [código: grafo](../codigo/03-grafo.md) · [blindaje](../blindaje/q-grafo.md) |
| **Vecinos (CIM)** | Grilla por planta + índice "puente" por escalera. Independiente salvo sobre escaleras; se preservó detección de paredes y vértices. | [código: vecinos/CIM](../codigo/04-vecinos-cim-geometria.md) · [blindaje](../blindaje/q-vecinos-cim.md) |
| **Física (CPM)** | Planar en la planta del agente. En escalera: velocidad reducida + interpolación de `z`; anti-tunneling con paredes de la planta actual. | [código: física CPM](../codigo/02-fisica-cpm.md) · [blindaje](../blindaje/q-fisica-cpm.md) |
| **Salida** | Columna `z`: `tout; x; y; z; vx; vy; state; id`. | [código: I/O](../codigo/01-tipos-base-io.md#5-output-con-columna-z-d10) |
| **Animación** | 2D por planta (círculos) + vista 3D a 45° con plantas apiladas. | [informe §Implementación](../../informe/informe.tex) |

## Los "por qué" clave (decisiones D1–D25)

Con código citado en el archivo enlazado. Fuente completa: [`.claude/DECISIONES.md`](../../.claude/DECISIONES.md).

- **D1 — Híbrido `Vec3`+`Vec2`.** Migrar todo obligaba a reescribir la geometría planar y arriesgaba mover la `z` por fuerzas planares. → [detalle](../codigo/01-tipos-base-io.md#1-el-híbrido-vec3-vec2-planar-d1)
- **D2 — Sin `vz`: la `z` se interpola.** No aporta al modelo peatonal y complica el CPM. → [detalle](../codigo/01-tipos-base-io.md#2-sin-vz-la-z-se-interpola-no-se-integra-d2)
- **D3 / D4 — `z` por elemento + `STAIRS.csv`.** `z` por extremo daría muros inclinados pero rompe la planaridad (CIM, anti-tunneling). Escalera aparte, eje pie→tope. → [detalle](../codigo/01-tipos-base-io.md#3-geometría-una-planta-z-por-elemento-no-z-por-extremo-d3)
- **D6 — Grafo 3D.** Malla por planta unida por escaleras (costo = largo 3D); A* euclídeo 3D; visibilidad/FVP por planta. → [detalle](../codigo/03-grafo.md#grafo-por-planta)
- **D7 — Se eliminó el SFM.** CPM único modelo; duplicaba el costo de cada cambio de interfaz.
- **D8 — CIM por planta + puente por escalera.** Independiente salvo sobre el tramo. → [detalle](../codigo/04-vecinos-cim-geometria.md)
- **D9 — CPM 3D.** Velocidad reducida + interpolación de `z` en escalera; anti-tunneling con paredes de la planta actual. → [detalle](../codigo/02-fisica-cpm.md)
- **D10 — Output con `z`.** Columna nueva tras `y`. → [detalle](../codigo/01-tipos-base-io.md#5-output-con-columna-z-d10)
- **D11 — Ruteo multiplanta end-to-end.** Cuatro fixes que sólo aparecieron con dos plantas reales (ver abajo). → [grafo](../codigo/03-grafo.md#fvp-al-pie)
- **D17 — Contacto de pared al núcleo duro `rmin`.** Fix del *livelock* de jamba: pared cuenta como contacto sólo si `d ≤ rmin`; en `rmin..rmax`, repulsión suave. → [detalle](../codigo/02-fisica-cpm.md#5-fix-del-contacto-de-pared-al-núcleo-duro-rmin-d17)
- **D19 — Escalera switchback.** Dos tramos paralelos + descanso; confinamiento y carriles de contraflujo. → [detalle](../codigo/03-grafo.md) · [física](../codigo/02-fisica-cpm.md#7-carriles-de-contraflujo-en-el-switchback-d19)
- **D21 — Fix del "salto de z".** Dos causas: nodos del grafo dentro de la huella + cambio de hop lejos del pie. → [detalle](../codigo/03-grafo.md#salto-de-z)
- **D22 — Modo crisis.** Perfil físico por generador (`max_velocity` honrado); `vd = 2.0` en Evacuación. → [detalle](../codigo/02-fisica-cpm.md#8-modo-crisis-y-acotado-del-δt-d22)
- **D23 — Semilla a todos los streams.** Reproducibilidad opt-in con `Seeds`. → [detalle](../codigo/01-tipos-base-io.md#6-reproducibilidad-opt-in-con-seeds-d23)
- **D24 — Fix del deadlock de boca de escalera.** Descansos mallados + gate lateral + boca ancha. → [detalle](../codigo/03-grafo.md#deadlock-boca)

## La integración multiplanta: el bug que ningún test unitario atrapó

Cada módulo verde aislado, pero en el primer escenario real de dos plantas **ningún agente subía** — sin error en runtime. La `z` se perdía en silencio (*default* `z=0`): simulación plausible pero errónea. Los cuatro fixes (D11):

1. La task de un grupo tomaba la `z` del **primer** candidato → aulas de ambas plantas en PB. Ahora la planta va por candidato.
2. Aulas de ambas plantas comparten `(x,y)` → resolver por posición colapsaba a PB. Ahora por **índice**.
3. El *Furthest Visible Point* ruteaba hasta el pie y se quedaba (el tope "no es visible"). Al llegar al pie, el hop avanza al tope.
4. Servers y generadores descartaban la `z` → aulas y spawns de P1 caían a `z=0`. Se propagó la planta punta a punta.

**La lección** (para el oral): al agregar una coordenada, auditar *todos* los caminos de datos que copian estado; los *defaults* silenciosos dan simulaciones que corren y "se ven bien" pero son erróneas. Tests por módulo ≠ integración real. → [detalle en I/O](../codigo/01-tipos-base-io.md#7-la-lección-los-defaults-z0-silenciosos-d11d16d18)

## Verificación

Suite: **143 tests, 0 fallos, 0 omitidos** (física de escaleras, grafo multiplanta, CIM por planta). Cada resultado se midió sobre el output.

---

### Navegación

- ⬅ [Resumen §Introducción y observables](01-introduccion-observables.md)
- ➡ [Resumen §Simulaciones](03-simulaciones.md) · [Resumen §Resultados](04-resultados.md) · [Conclusiones](05-conclusiones.md)
- Detalle de código: [tipos/IO](../codigo/01-tipos-base-io.md) · [física CPM](../codigo/02-fisica-cpm.md) · [grafo](../codigo/03-grafo.md) · [vecinos/CIM](../codigo/04-vecinos-cim-geometria.md)
- Blindaje: [tipos/IO](../blindaje/q-tipos-io.md) · [física](../blindaje/q-fisica-cpm.md) · [grafo](../blindaje/q-grafo.md) · [vecinos/CIM](../blindaje/q-vecinos-cim.md)
- [Índice](../README.md) · [`DECISIONES.md`](../../.claude/DECISIONES.md) · [informe](../../informe/informe.tex)
