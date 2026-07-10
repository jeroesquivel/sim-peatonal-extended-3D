# Blindaje — Física: CPM / A-CPM

Preguntas de defensa oral sobre el modelo físico. Cada respuesta con cita al código, informe o
decisión. Los `⚠️ Punto débil` marcan las limitaciones conocidas y cómo defenderlas honestamente.

Detalle de código con snippets verbatim en [../codigo/02-fisica-cpm.md](../codigo/02-fisica-cpm.md).

---

### P: ¿Qué es el CPM y por qué lo eligieron?

El **Contractile Particle Model** (Baglietto & Parisi 2011) modela cada peatón como un disco de
**radio variable** (de `rmin` a `rmax`): ante un contacto contrae a `rmin` y escapa; libre,
expande hacia `rmax` con constante `τ` y su velocidad deseada escala con la expansión — la
repulsión emerge sin fuerzas newtonianas explícitas. Lo elegimos porque es el modelo de la
cátedra y el que traía el simulador; nuestro TP es la ampliación a 3D, no cambiar de modelo
(`CpmOperationalModel.java:403` y `:934`; SFM eliminado, CPM único,
[D7](../../.claude/DECISIONES.md)).

### P: ¿Qué agrega la variante anisotrópica (A-CPM)?

Dos cosas de Martin & Parisi (2024) (`CpmOperationalModel.java:21`). **(1) Evitación angular:**
la dirección deseada `e_a` suma al versor al target la repulsión suave de pared y un esquive de
los dos peatones frontales que se aproximan, sólo en el semicírculo frontal
(`CpmOperationalModel.java:537`). **(2) Contacto anisotrópico:** dos que caminan en paralelo con
el radio expandido **no** cuentan como contacto salvo que uno corte la franja frontal `rmin`;
sólo al núcleo duro o detenidos se cae al contacto isotrópico (`CpmOperationalModel.java:788`).
Sin esto el CPM isotrópico genera colisiones artificiales y estrangula el flujo bidireccional.

### P: ¿Cómo frena la velocidad en la escalera y de dónde sale el 0.59 m/s?

Sobre la escalera la velocidad deseada se multiplica por el `speed_factor` del tramo: `integrate`
la detecta con `locateStair` y aplica `vdMax *= speedScale` (`CpmOperationalModel.java:216`,
`:946` y el branch `LEAVING` `:327`). Con `speed_factor = 0.38` sobre `vd = 1.55 m/s` da
`0.38 × 1.55 ≈ 0.59 m/s`, que es la velocidad **efectiva medida** en el output, dentro del rango
realista 0.5–0.6 m/s ([D19](../../.claude/DECISIONES.md),
[informe §Simulaciones](../../informe/informe.tex)).

### P: ¿Por qué la `z` no es un grado de libertad dinámico? ¿Por qué no hay `vz`?

Porque la dinámica es **planar** y la `z` sólo cambia sobre la escalera, donde surge de
**interpolar** el avance, no de integrar una fuerza vertical:
`z = lerp(foot.z, top.z, progressAt(x,y))` (`Stairs.java:90`, aplicado en
`CpmOperationalModel.java:240`; [D2](../../.claude/DECISIONES.md)). Un `vz` real complicaría el
CPM sin aportar nada y arriesgaría mover la `z` por fuerzas planares; por eso `setPosition(x,y)`
conserva la `z`.

### P: ¿Por qué la dinámica es planar pero las posiciones son `Vec3`? ¿No es contradictorio?

Es un híbrido deliberado
([D1](../../.claude/DECISIONES.md)/[D2](../../.claude/DECISIONES.md)): `Vec3` para posición y
velocidad (output, heurística 3D del A*, ubicación por planta), `Vec2` para toda la dinámica
horizontal. El CPM proyecta el foot-target a `xy` y opera ahí
(`CpmOperationalModel.java:290`); sólo el wrapper `integrate` toca la `z`. Migrar todo a `Vec3`
se descartó porque obligaba a reescribir la geometría planar.

### P: ¿Qué era el livelock de la jamba y por qué la causa NO era el ruteo?

Algunos agentes quedaban **oscilando pegados a la jamba** de una puerta (velocidad `~vd` pero
desplazamiento neto nulo). Descartamos el ruteo con el hop-log: el hop era estable y apuntaba al
hueco. La causa estaba en las **fuerzas**: el CPM trataba la pared como contacto ya en el radio
**expandido** (hasta `rmax`) y disparaba la velocidad de escape perpendicular, que peleaba con la
atracción al destino ([D14](../../.claude/DECISIONES.md)→[D17](../../.claude/DECISIONES.md)). Un
primer intento en `moveWithWallCheck` se revirtió porque la causa no estaba ahí.

### P: ¿Cuál fue el fix (rmin vs rmax) y por qué es coherente con el A-CPM?

La pared cuenta como **contacto** (el que dispara el escape) **sólo** cuando el cuerpo físico la
tocaría, `d ≤ rmin`, en vez de `d ≤ radius` (`CpmOperationalModel.java:748`). En la franja
`rmin..rmax` actúa la **repulsión suave** `Aw·exp(-d/Bw)`, que aparta sin anular el pull al
objetivo. Es **coherente con el A-CPM** porque el modelo ya hace eso entre agentes (contacto duro
sólo al `rmin`, `isAnisotropicContact` `:788`). **Números:** la oscilación cayó de **20.1% a
5.5%** y los atascados de **2/30 a 0/30** ([D17](../../.claude/DECISIONES.md)); el anti-tunneling
queda intacto.

### P: ⚠️ El livelock, ¿quedó realmente resuelto?

**⚠️ Punto débil (defendible).** No eliminado, pero **muy reducido y acotado**: el caso original
(jamba de aula) quedó resuelto — **0/22** no-evacuados en jambas de aula en los 15 output de
Evacuación ([D17](../../.claude/DECISIONES.md), nota 2026-07-07). Persiste esporádico el mismo
mecanismo en dos geometrías estrechas (boca de escalera contra la baranda y jamba de un portón).
Se defiende: (1) siempre **un agente aislado**, no congestión; (2) **no crece con `N`** (`≈1` por
corrida incluso con `N=500`); (3) casi siempre es el último agente aún en tránsito cuando el
output deja de escribir. Lo reportamos en
[informe §Resultados](../../informe/informe.tex) y Conclusiones.

### P: ¿Qué es el modo crisis y por qué `vd = 2.0`?

El perfil físico se asigna **por generador** (cada zona declara su `max_velocity`); la
**Evacuación** usa el modo crisis `vd = ve = 2.0 m/s` frente a los `1.55 m/s` normales, del orden
de las velocidades de escape en pánico (Helbing 2000). Es [D22](../../.claude/DECISIONES.md):
antes el `max_velocity` del Formato B era código muerto, así que la evacuación corría con física
de baseline; ahora se honra. Verificado: velocidad mediana en el plano = 2.00 m/s en la
evacuación.

### P: ¿Por qué se acota el `Δt` y cómo?

Porque el paso debe ser **estable para el agente más rápido**: el CPM recomienda
`Δt = rmin/(2·max(vd,ve))` (`CpmParameters.java:54`), que mueve como mucho medio `rmin` por paso
para que el contacto y el anti-tunneling no salten un cuerpo. El `Δt` efectivo es
`min(Δt_esc, rmin/(2·max(vd,ve)))` con el perfil más rápido: `0.048 s` normal, `0.0375 s` en
crisis ([D22](../../.claude/DECISIONES.md)). La salida se registra cada `Δt_out = 0.2 s`.

### P: ¿Qué son los carriles de contraflujo y cuándo se activan?

Sobre tramos anchos el CPM aplica un **bias lateral gentil** que separa a los que suben
(`+perp`) de los que bajan (`-perp`), mezclando en `e_a` un versor al centro del carril
(`±width/4`), saturado en `LANE_BIAS_WEIGHT = 0.45` (`CpmOperationalModel.java:551`;
[D19](../../.claude/DECISIONES.md)). Está gateado por tres condiciones: flag `stairLanes` (OFF
por defecto → baseline byte-idéntico), agente sobre la escalera, y tramo más ancho que
`STAIR_LANE_MIN_WIDTH = 2.5 m` (`CpmOperationalModel.java:42-53`). Con `width = 2.6` la
separación medida es `≈ 0.34 m`; `0.45` separa sin estrangular la evacuación densa.

### P: ¿Qué pasa si dos agentes se solapan? ¿Se atraviesan?

Nunca. El contacto al **núcleo duro** es isotrópico y omnidireccional
(`isAnisotropicContact` cae al criterio isotrópico al `rmin` o detenido,
`CpmOperationalModel.java:800`), así que el escape los separa. Y si dos núcleos llegaran a
penetrarse, `hardCoreSeparation` (`CpmOperationalModel.java:827`) suma un desplazamiento que
corrige la penetración completa **incluso a velocidad cero** — un reacomodo geométrico, no una
fuerza, para que los peatones no se atraviesen (`rmin_i + rmin_j = 2·rmin`).

### P: ¿El anti-tunneling usa las paredes de qué planta?

Sólo las de la **planta actual**, o la **unión** de las dos plantas si está sobre una escalera
(`floorWallsFor`, `CpmOperationalModel.java:269`; [D9](../../.claude/DECISIONES.md)): que un muro
de la planta alta frenara a un agente de la baja con la misma proyección `xy` sería un error. Las
paredes por planta se precomputan en `fromGeometry` (`:138`); `moveWithWallCheck` no mueve al
destino si el segmento cruza un muro, intenta deslizar paralelo y si no se detiene (`:585`).

### P: ¿Por qué el radio de consulta de vecinos es tan grande (`rmax·8`)?

Porque la evitación angular necesita "ver" a los peatones frontales con **antelación**: un radio
chico (`rmax·2 ≈ 0.64 m`) los detecta tarde y se chocan antes de esquivar; `rmax·8 ≈ 2.56 m` da
margen (`CpmOperationalModel.java:203-214`). App dimensiona la grilla del CIM con el máximo entre
su margen y este radio, así que pedir más acá no rompe el índice.

### P: ¿Cómo evita el modelo que la cola del kiosco se disperse?

El estado `QUEUEING` tiene manejo **dedicado**: si el agente está sobre su slot (dentro de
`QUEUE_SNAP_DISTANCE`) se ancla (velocidad cero, posición exacta, radio mínimo) y el server lo
detecta llegado; si no, avanza despacio y directo (`QUEUE_APPROACH_SPEED`) sin la velocidad de
escape que dispersaría la fila, conservando el núcleo duro vía `hardCoreSeparation`
(`CpmOperationalModel.java:344-384`).

---

## Navegación

- Detalle de código: [../codigo/02-fisica-cpm.md](../codigo/02-fisica-cpm.md)
- Áreas vecinas: [../codigo/03-grafo.md](../codigo/03-grafo.md) ·
  [../codigo/04-vecinos-cim-geometria.md](../codigo/04-vecinos-cim-geometria.md)
- Resumen: [../resumenes/02-implementacion.md](../resumenes/02-implementacion.md)
- Fuentes: [informe (LaTeX)](../../informe/informe.tex) ·
  [DECISIONES.md](../../.claude/DECISIONES.md) (D1, D2, D9, D17, D19, D22)
