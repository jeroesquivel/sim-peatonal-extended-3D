# Física: CPM / A-CPM (`CpmOperationalModel`)

Defensa del **modelo físico**: Contractile Particle Model anisotrópico con evitación (A-CPM) y lo
que la ampliación a 3D le agregó (velocidad reducida e interpolación de `z`, anti-tunneling por
planta, contacto de pared al núcleo duro, contacto anisotrópico, carriles de contraflujo, modo
crisis, acotado del `Δt`). Archivo: `CpmOperationalModel.java` (951 líneas); parámetros en
`CpmParameters.java`.

---

## 1. Qué es el CPM / A-CPM

**Contractile Particle Model** (Baglietto & Parisi 2011) en variante **anisotrópica con evitación**
(A-CPM, Martin & Parisi 2024), declarado en el javadoc de la clase (`CpmOperationalModel.java:21`).
Tres ideas: **(a)** radio variable de `rmin` (núcleo duro) a `rmax` (espacio personal) — colapsa a
`rmin` en contacto, expande hacia `rmax` con relajación `τ`; **(b)** velocidad deseada acoplada al
radio: escala con la fracción expandida `frac ∈ [0,1]` elevada a `β` (un agente comprimido arranca
lento y acelera), y recibe el `speedScale` de la escalera (`CpmOperationalModel.java:934`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmOperationalModel.java:934-950
    private Vec2 desiredVelocity(AgentState state, BehaviorState behavior, Vec2 e_a,
                                 AgentProfile profile, double speedScale) {
        double range = profile.rmax() - profile.rmin();
        double frac = range > 0.0 ? (state.radius() - profile.rmin()) / range : 1.0;
        if (frac < 0.0) frac = 0.0;
        else if (frac > 1.0) frac = 1.0;

        double vdMax = profile.vd();
        if (behavior == BehaviorState.APPROACHING) {
            vdMax *= 0.3;
        }
        // Paso 6: velocidad reducida sobre la escalera (speedScale = speedFactor).
        vdMax *= speedScale;

        double magnitude = vdMax * Math.pow(frac, profile.beta());
        return e_a.scale(magnitude);
    }
```

**(c)** evitación angular: `e_a` es la suma normalizada del versor al target `e_t`, la repulsión
suave de pared `n_w_c` y las maniobras de los dos peatones frontales `sum_n_c_j`
(`CpmOperationalModel.java:537-539`), sólo sobre el **semicírculo frontal** y con ángulo de
aproximación en `[π/2, π]` (`CpmOperationalModel.java:457-535`) — corrección de la velocidad
deseada, no fuerza isotrópica, por eso no hay colisiones artificiales entre agentes en paralelo. En
contacto físico se usa la **velocidad de escape** `ve` (`CpmOperationalModel.java:413-418`).

---

## 2. Dinámica planar en la planta del agente

La ampliación a 3D **no** vuelve vertical la dinámica: el CPM opera sobre la proyección `xy` del
foot-target; la `z` la maneja el wrapper `integrate`, no las fuerzas (decisión
[D1](../../.claude/DECISIONES.md)/[D2](../../.claude/DECISIONES.md); sin `vz`,
`CpmOperationalModel.java:290`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmOperationalModel.java:290-293
        // La dinámica del CPM es planar (D1): se opera sobre la proyección xy del
        // foot-target. La z del agente (planta / escalera) la maneja el wrapper
        // integrate (interpolación en la escalera), no las fuerzas de este modelo.
        Vec2 footTarget = footTarget3 == null ? null : footTarget3.xy();
```

---

## 3. Velocidad reducida sobre escalera + interpolación de `z` (D2 / D9)

`integrate` es un **wrapper**: detecta si el agente está sobre escalera, reduce la velocidad por
`speedFactor`, corre la dinámica planar e interpola la `z` según el avance sobre el eje
(`CpmOperationalModel.java:217`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmOperationalModel.java:217-242
    public void integrate(
            AgentState state,
            Vec3 footTarget3,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            double dt
    ) {
        // Paso 6: ¿el agente está sobre una escalera? Si sí, su velocidad deseada
        // se reduce por speedFactor y, tras mover en el plano, su z se interpola a
        // lo largo del tramo (D2). El anti-tunneling usa las paredes de la planta
        // actual (o la unión de las dos plantas de la escalera).
        Stairs onStair = locateStair(state, footTarget3);
        double speedScale = onStair != null ? onStair.speedFactor() : 1.0;
        List<Wall> floorWalls = floorWallsFor(state, onStair);

        integratePlanar(state, footTarget3, behavior, neighbors, dt, speedScale, floorWalls, onStair);

        if (onStair != null) {
            // z = lerp(foot.z, top.z, avance planar): la altura sigue al progreso
            // (x,y) del agente sobre el eje de la escalera (D2). Como el agente entra
            // al tramo por el PIE (avance ≈0, ver D21: exclusión de la huella del
            // grafo + STAIR_FOOT_REACH chico), la z engancha desde el nivel del piso y
            // crece suave; no hay salto por frame.
            state.setZ(onStair.zAt(state.x(), state.y()));
        }
    }
```

El `speedScale` se propaga a `desiredVelocity` (línea 946) y al branch `LEAVING`
(`CpmOperationalModel.java:327`): con `speed_factor = 0.38` sobre `vd = 1.55 m/s`,
`0.38 × 1.55 ≈ 0.59 m/s` es la velocidad efectiva en el tramo. `locateStair`
(`CpmOperationalModel.java:253-267`) decide qué escalera recorre (huella `containsXy` + `midStair`
o `headingAcross`); la altura la da `Stairs.zAt`: `z = lerp(foot.z, top.z, progressAt(x,y))`
([D2](../../.claude/DECISIONES.md), `Stairs.java:90`).

---

## 4. Anti-tunneling con paredes de la planta actual (D9)

El anti-tunneling usa **sólo las paredes de la planta actual** (o la unión de las dos plantas si
está sobre escalera): un muro de la planta alta no debe frenar a un agente de la baja con la misma
proyección `xy`. Esa selección la hace `floorWallsFor` (`CpmOperationalModel.java:269`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmOperationalModel.java:269-278
    /** Paredes a usar para el anti-tunneling de {@code state} este paso. */
    private List<Wall> floorWallsFor(AgentState state, Stairs onStair) {
        if (floorWalls == null || floorWalls.isEmpty()) {
            return walls; // ctor legacy / 1 planta: lista global
        }
        if (onStair != null) {
            return stairWalls.get(onStair);
        }
        return floorWalls.get(nearestFloorIndex(floorLevels, state.z()));
    }
```

`floorWalls` (por planta) y `stairWalls` (por escalera, **unión** de las dos plantas) se precomputan
en `fromGeometry`. El chequeo en sí (`moveWithWallCheck`, `CpmOperationalModel.java:585-592`) es
estricto: mueve sólo si `from→to` no atraviesa pared (`isStepClear`); si no, desliza paralelo y como
último recurso se despega. `isStepClear` **no** exige clearance mínima (la separación cuerpo-pared la
hace la repulsión suave), lo que permite que el fix D17 acerque el agente al muro sin tunneling
(`CpmOperationalModel.java:663-670`).

---

## 5. Fix del contacto de pared al núcleo duro `rmin` (D17)

Fix físico más importante; resuelve el **livelock de la jamba**. Diagnóstico: el CPM trataba la
pared como **contacto** apenas el agente entraba en su radio **expandido** (hasta `rmax`) y
disparaba el escape perpendicular; cerca de una jamba ese escape peleaba con la atracción al destino
y el agente **oscilaba en el lugar**. No era el ruteo (hop estable): estaba en las fuerzas
([D17](../../.claude/DECISIONES.md)). Corrección: la pared cuenta como contacto **sólo** con
`d ≤ rmin` (núcleo duro); en `rmin..rmax` la repulsión suave `Aw·exp(-d/Bw)` aparta sin anular el
pull, así el agente **dobla esquinas y cruza puertas** (`CpmOperationalModel.java:737`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmOperationalModel.java:737-758
            } else if (neighbor.type() == NeighborType.WALL) {
                double d = neighbor.distance();
                // La pared cuenta como CONTACTO (dispara la velocidad de escape
                // perpendicular, que ignora el objetivo) sólo cuando el CUERPO
                // FÍSICO del agente (rmin, núcleo duro) la tocaría — no dentro de
                // todo su espacio personal expandido (hasta rmax). En la franja
                // rmin..rmax la repulsión SUAVE de pared (Aw·exp(-d/Bw), rama de
                // avoidance) ya lo aparta sin anular el pull al objetivo, así el
                // agente DOBLA esquinas / cruza puertas en vez de rebotar y
                // quedarse pegado en la jamba (bug D14). El anti-tunneling
                // (moveWithWallCheck) sigue impidiendo atravesar la pared.
                if (d <= rmin && d > 0.0) {
                    int wallId = neighbor.id();
                    if (wallId >= 0 && wallId < this.walls.size()) {
                        Vec2 w = this.walls.get(wallId).closestPointTo(selfPos);
                        Vec2 toAgent = selfPos.sub(w);
                        dirs.add(toAgent.scale(1.0 / d));
                    } else {
                        throw new IllegalStateException("Wall neighbor ID " + wallId + " is out of bounds for the loaded walls list.");
                    }
                }
            }
```

El `rmin` es el del perfil, no el radio expandido (`CpmOperationalModel.java:717`). **Números del
informe**: la oscilación-en-esquina cayó de **20.1% a 5.5%** y los atascados de **2/30 a 0/30**;
coherente con el contacto anisotrópico entre agentes (§6), anti-tunneling intacto.

⚠️ **Punto débil (oral):** el livelock quedó reducido, no eliminado. En corridas aisladas de la
evacuación reaparece en dos geometrías estrechas (boca de escalera, jamba de portón); es un agente
aislado por punto, no congestión, y no crece con `N` ([informe §Resultados](../../informe/informe.tex)).

---

## 6. Contacto anisotrópico entre agentes (A-CPM)

Corazón del A-CPM: dos agentes en paralelo (espacio personal expandido) **no** cuentan como contacto
salvo que uno esté al frente y su sección intersecte la franja frontal de ancho `rmin`. Al radio
mínimo o detenido, se cae al criterio **isotrópico** clásico (nunca se atraviesan)
(`CpmOperationalModel.java:788`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmOperationalModel.java:788-818
    private boolean isAnisotropicContact(AgentState self, Vec2 selfPos, Vec2 selfVel,
                                         double rmin, AgentState other, Vec2 otherPos,
                                         double centerDist) {
        double sumRadii = self.radius() + other.radius();
        boolean overlap = centerDist < sumRadii;
        if (!overlap) {
            return false;
        }

        boolean atMinRadius = self.radius() <= rmin + 1e-9;
        double speed = selfVel.norm();
        // En radio mínimo o sin dirección de movimiento -> núcleo duro isotrópico.
        if (atMinRadius || speed < 1e-9) {
            return true;
        }

        Vec2 dirMove = selfVel.scale(1.0 / speed);
        Vec2 toOther = otherPos.sub(selfPos);

        // (1) El otro debe estar al frente: proyección sobre la dirección de
        // movimiento positiva (ángulo en [-π/2, π/2]).
        double along = toOther.dot(dirMove);
        if (along < 0.0) {
            return false;
        }

        // (2) Sección transversal frontal: distancia perpendicular del centro de
        // j al eje de movimiento de i < rmin + r_j.
        double perp = Math.abs(toOther.x() * dirMove.y() - toOther.y() * dirMove.x());
        return perp < rmin + other.radius();
    }
```

Es la simetría que el fix D17 replica para las paredes: sólo el núcleo duro (`rmin`) dispara la
respuesta isotrópica. Además, si dos núcleos se penetran, un desplazamiento **posicional** (no
fuerza) los separa `2·rmin`, aun a velocidad cero (`hardCoreSeparation`,
`CpmOperationalModel.java:827`; usado en `QUEUEING`, `CpmOperationalModel.java:344-384`).

---

## 7. Carriles de contraflujo en el switchback (D19)

En tramos anchos el CPM aplica un **bias lateral gentil** que separa subida y bajada, **gateado** por
`stairLanes` (OFF por defecto → baseline y tests byte-idénticos) y por `STAIR_LANE_MIN_WIDTH = 2.5`;
peso saturado `LANE_BIAS_WEIGHT = 0.45` (con 0.20 no separaba; con 0.45 separa ~0.34 m sin
estrangular la evacuación densa). Mezcla en `e_a` un versor al centro del carril (`+perp` sube,
`-perp` baja) — corrección perpendicular, no reemplazo del target (`CpmOperationalModel.java:551`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmOperationalModel.java:551-566
            if (onStair != null && stairLanes && onStair.width() >= STAIR_LANE_MIN_WIDTH
                    && footTarget3 != null) {
                boolean ascending = footTarget3.z() > state.z();
                Vec2 laneCenter = onStair.laneTargetAt(pos.x(), pos.y(), ascending).xy();
                Vec2 toLane = laneCenter.sub(pos);
                double lateralDev = toLane.norm();
                if (lateralDev > 1e-9) {
                    Vec2 laneDir = toLane.scale(1.0 / lateralDev);
                    double weight = Math.min(LANE_BIAS_WEIGHT,
                            LANE_BIAS_WEIGHT * (lateralDev / onStair.laneOffset()));
                    Vec2 blended = e_a.scale(1.0 - weight).add(laneDir.scale(weight));
                    if (blended.norm() > 0.0) {
                        e_a = blended.normalized();
                    }
                }
            }
```

El objetivo lo da `Stairs.laneTargetAt`, que desplaza el punto del eje `±laneOffset = ±width/4`
según `ascending` (`Stairs.java:140-146`). Con `width = 2.6` el offset es `0.65 m`; la separación
resultante es `≈ 0.34 m` ([D19](../../.claude/DECISIONES.md)).

---

## 8. Modo crisis y acotado del `Δt` (D22)

Perfil físico **por generador**: cada zona declara su `max_velocity`. La Evacuación usa el **modo
crisis** `vd = ve = 2.0 m/s` (vs. `1.55 m/s` normal), del orden de los modelos de escape (Helbing
2000); el `max_velocity` del Formato B era código muerto hasta [D22](../../.claude/DECISIONES.md).
Perfil de partida = conjunto 1 de Baglietto & Parisi (`CpmParameters.java:13`).

```java
// src/main/java/ar/edu/itba/simped/agent/om/CpmParameters.java:13-15
    public static AgentProfile baglietoParisiSet1() {
        return new AgentProfile(1.55, 0.5, 0.15, 0.32, 0.9, 1.55);
    }
```

Orden `AgentProfile(vd, τ, rmin, rmax, β, ve)` → `vd=1.55`, `τ=0.5`, `rmin=0.15`, `rmax=0.32`,
`β=0.9`, `ve=1.55`; en crisis App sustituye `vd=ve=2.0`. **Acotado del `Δt`:** paso estable
`rmin/(2·max(vd,ve))` (`recommendedDt`, `CpmParameters.java:54`): normal `0.15/(2·1.55) ≈ 0.048 s`,
crisis `0.15/(2·2.0) = 0.0375 s`. El `Δt` efectivo se acota con el perfil **más rápido** presente;
salida cada `Δt_out = 0.2 s`. **Radio de consulta de vecinos:** `rmax·8 ≈ 2.56 m` (no `rmax·2`),
para ver a los frontales con antelación (`CpmOperationalModel.java:203-214`).

---

## 9. Tabla de parámetros (del informe)

| Parámetro | Símbolo | Valor |
|---|---|---|
| Velocidad deseada (normal) | `vd` | 1.55 m/s |
| Velocidad deseada (evacuación / crisis) | `vd` | 2.0 m/s |
| Tiempo de relajación | `τ` | 0.5 s |
| Radio mínimo | `rmin` | 0.15 m |
| Radio máximo | `rmax` | 0.32 m |
| Coeficiente anisotrópico | `β` | 0.9 |
| Velocidad de escape | `ve` | = `vd` |
| Paso de integración | `Δt` | 0.048 s / 0.0375 s (evac.) |
| Paso de salida | `Δt_out` | 0.2 s |
| Factor de velocidad en escalera | `speed_factor` | 0.38 |
| Ancho de escalera | — | 2.6 m |

Fuente: [informe §Simulaciones](../../informe/informe.tex). Todos los agentes usan el mismo perfil:
`rmax = 0.32 m` es **constante** (`CpmParameters.baglietoParisiSet1`, `CpmParameters.java:14`), no se
muestrea. El radio nace en `AGENT_RADIUS = 0.25 m` (`ConfigurablePedestrianGenerator.java:71`) y
**crece** hacia `rmax` por la regla de relajación.

> ⚠️ **Punto débil / discrepancia con el informe (memorizá esto):** el caption de la Tabla dice que
> `rmax` "se muestrea uniforme en `[0.30, 0.32]`". Eso **no** ocurre: `rmax` es fijo `0.32`. Si
> preguntan "¿dónde se muestrea `rmax`?", la respuesta honesta es que **no se muestrea**; lo que varía
> es el radio *dinámico* del CPM, de `rmin=0.15` a `rmax=0.32`, con spawn `0.25`. (Conviene corregir
> el `informe.tex`.)

---

## Navegación

- Hermano (código): [Grafo de navegación (A* 3D)](03-grafo.md) ·
  [Vecinos (CIM) y geometría](04-vecinos-cim-geometria.md)
- Q&A de esta área: [Blindaje — Física CPM/A-CPM](../blindaje/q-fisica-cpm.md)
- Resumen: [Implementación](../resumenes/02-implementacion.md)
- Fuentes: [informe (LaTeX)](../../informe/informe.tex) ·
  [DECISIONES.md](../../.claude/DECISIONES.md) (D1, D2, D9, D17, D19, D22)
