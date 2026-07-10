# Resumen breve — todo en una hoja

*La versión corta para leer antes del oral. El detalle con snippets está en [código](codigo/02-fisica-cpm.md) y [blindaje](blindaje/q-fisica-cpm.md).*

## El pitch (30 s)

Ampliamos a **3D** un simulador peatonal 2D para soportar **plantas unidas por escaleras**. Enfoque
**híbrido**: `Vec3 (x,y,z)` para la posición, pero `Vec2` para toda la dinámica planar — el CPM
sigue operando en el plano de cada planta y la `z` se **interpola** sobre la escalera (sin `vz`). Así
el núcleo del modelo quedó intacto; lo difícil fue la **integración** multiplanta.

## Qué cambió por módulo

| Módulo | Cambio | Decisión |
|---|---|---|
| Tipos | `Vec3` + `AgentState.z`; `z` interpolada, sin `vz` | D1, D2 |
| Geometría | cada elemento con su planta `z`; `STAIRS.csv` (eje pie→tope) | D3, D4 |
| Grafo | malla por planta unida por aristas de escalera; A* con heurística **euclídea 3D**; visibilidad/FVP **por planta** | D6 |
| Vecinos (CIM) | una grilla por planta + **puente** por escalera | D8 |
| Física (CPM) | velocidad reducida + `z` interpolada en escalera; anti-tunneling con paredes de la planta actual | D9 |
| Salida | columna `z`: `tout; x; y; z; vx; vy; state; id` | D10 |

## Los 3 fixes que impresionan

1. **La `z` que se perdía en silencio** (D11): nadie subía a P1, *sin ningún error* — cuatro caminos
   de datos con default `z=0`. Lección: al agregar una coordenada, auditar todos los caminos que
   copian estado; los tests por módulo no reemplazan un escenario de integración real.
2. **El salto de `z`** (D21): agentes teletransportados a media escalera. Dos causas de **ruteo** (no
   render): nodos del grafo dentro de la huella + cambio de hop lejos del pie.
3. **El livelock de jamba** (D17): un error del **modelo de fuerzas**, no del ruteo. La pared contaba
   como contacto en todo el espacio personal (`rmax`); se corrigió a contacto sólo en el núcleo duro
   (`rmin`). Oscilación en esquina `20.1% → 5.5%`, atascados `2/30 → 0/30`.

## Números canónicos

- Perfil CPM: `vd=1.55` m/s (normal) / `2.0` (evacuación, modo crisis) · `τ=0.5` · `rmin=0.15` ·
  `rmax=0.32` (**constante**, no se muestrea) · `β=0.9`.
- `Δt ≈ 0.048` s / `0.0375` (evac); `Δt_out=0.2`. Escalera: `2.6` m, `speed_factor=0.38` → `0.59` m/s.
- Mapa 60×60 m; kiosco observable `R=[2,14]×[42,52]`. Suite **143 tests, 0 fallos**. **5 realizaciones**.

## Resultados en 4 líneas

- **Evacuación** (`N` = 40…500): el edificio se vacía **completo** en todo el rango. **Dos regímenes**:
  suave hasta `N≈200`, saturado desde `N≈300` (el máximo se despega: `129 → 305` s). Distribución
  **bimodal** (PB rápido, P1 lento por bajar la escalera).
- **Ingreso** (`Ta` = 1/5/10 min, `Nmax=120`): pico en el kiosco `53 → 4.8` (**~11×** menos al
  repartir en 10 min con la misma gente).
- **Complementario** (`Nmax` a `Ta=5`): supralineal hasta saturar el kiosco (`~180`), luego ~lineal
  (pendiente `≈0.44`).
- **Remanente**: `≈1` agente/corrida no evacúa y **no crece con `N`** (último en tránsito + livelock
  esporádico). Límite conocido del CPM, acotado a un agente aislado.

## Q&A esenciales

**¿Por qué `Vec3` y no reescribir todo a 3D?** La física es planar; migrar todo arriesgaba mover la
`z` por fuerzas planares. El híbrido mantiene el CPM intacto (D1).

**¿Por qué no hay `vz`?** La dinámica vertical no aporta al modelo peatonal. La `z` sale de interpolar
el avance sobre la escalera (`Stairs.zAt`).

**¿Por qué la heurística del A* es euclídea 3D y es admisible?** Cada arista cuesta la distancia
euclídea real entre extremos (la de escalera, el largo 3D del tramo); por desigualdad triangular la
heurística nunca sobreestima → admisible y consistente.

**¿Cómo se ven dos agentes sobre la misma escalera?** Por el índice **puente** de esa escalera (fuerza
bruta, pocos agentes en el tramo); en los descansos se acopla con las grillas de las dos plantas.

**¿Por qué el livelock no era el ruteo?** El hop era estable y correcto; la causa estaba en las
fuerzas (contacto de pared en `rmax`). Fix: contacto solo en `rmin`, repulsión suave en `rmin..rmax`
(coherente con el contacto anisotrópico del A-CPM).

**¿Por qué el observable del Ingreso es el kiosco y no el pie de la escalera?** El pie no congestiona
(escalera ancha, mitad sube, repartidos). La congestión real está en el kiosco; el enunciado dice
"por ejemplo", así que medimos donde hay señal.

**¿Por qué 5 realizaciones?** La semilla gobierna todos los procesos aleatorios → réplicas
independientes; reportamos media ± σ. Es donde más importa: en el cruce de regímenes (`N=300`) una de
cinco corridas formó un atasco transitorio de 427 s.

**⚠️ Punto débil — `rmax`:** el informe dice que se muestrea en `[0.30,0.32]`, pero en el código es
**fijo `0.32`**. Si preguntan "¿dónde se muestrea?", la respuesta honesta es que no se muestrea; lo
variable es el radio dinámico del CPM (`0.15 → 0.32`). *(Conviene corregir el informe.)*

---

### Navegación

- Detalle: [Implementación](resumenes/02-implementacion.md) · [Resultados](resumenes/04-resultados.md) · [Conclusiones](resumenes/05-conclusiones.md)
- Código citado: [tipos/IO](codigo/01-tipos-base-io.md) · [física](codigo/02-fisica-cpm.md) · [grafo](codigo/03-grafo.md) · [vecinos/CIM](codigo/04-vecinos-cim-geometria.md)
- Blindaje completo: [física](blindaje/q-fisica-cpm.md) · [grafo](blindaje/q-grafo.md) · [resultados](blindaje/q-resultados-estadistica.md) · [escenario](blindaje/q-escenario-modelado.md)
- [Índice](README.md) · [informe](../informe/informe.tex) · [`DECISIONES.md`](../.claude/DECISIONES.md)
