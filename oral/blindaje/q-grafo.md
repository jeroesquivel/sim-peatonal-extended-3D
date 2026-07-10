# Blindaje — Grafo de navegación (A\* + FVP, 3D por planta)

Preguntas de defensa oral sobre el módulo de ruteo. Cada respuesta va con su cita al código, al
informe o a la decisión. Los snippets verbatim están en
[../codigo/03-grafo.md](../codigo/03-grafo.md).

---

## Preguntas "fáciles" (qué es / cómo funciona)

### P: ¿Cómo se une el grafo entre plantas?
Se genera **una malla por planta** corriendo `GridNodeReducer.reduce` una vez por cada `z` de
`geometry.floors()` con sólo las paredes de esa planta (`wallsOn(z)`), elevando cada nodo con
`n.withZ(z)`. Luego, por cada escalera se agregan un nodo al pie y otro al tope (conectados por
`connectToFloor`) y una **arista pie↔tope** que cruza plantas (`GraphBuilder.fromGeometry`,
`GraphBuilder.java:61-112`). Es lo que pide el enunciado.

### P: ¿Cuánto cuesta una arista de escalera?
El **largo real del tramo inclinado**: la distancia euclídea 3D `s.foot().distanceTo(s.top())`
(`GraphBuilder.java:106`), que incluye el desnivel `Δz`. Las aristas intra-planta cuestan la
distancia planar, así que el A\* paga por subir lo que realmente mide el tramo y no prefiere una
escalera por su proyección corta.

### P: ¿Por qué la heurística del A\* es euclídea 3D? {#admisible}
Porque el grafo vive en 3D y la heurística debe estar en la misma métrica que los costos de
arista: `nodes.get(neighbor).distanceTo(goal)` (`AStarPathfinder.java:62`) con `Vec3.distanceTo`
euclídea 3D (`Vec3.java:41-43`), como pide el enunciado.

**¿Y por qué es admisible?** Es la distancia recta 3D al goal y nunca sobreestima porque
cualquier camino del grafo es una poligonal ≥ el segmento recto (desigualdad triangular) y los
costos de arista son exactamente longitudes euclídeas (`GraphBuilder.java:106`). Como
`h ≤ costo real`, A\* devuelve el óptimo; además `h` es consistente (monótona), así que no
reabre nodos cerrados.

### P: ¿Qué es el Furthest Visible Point y por qué es "por planta"?
El FVP es el punto más lejano del camino A\* que el agente todavía ve en línea recta: el
`footTarget` intermedio que persigue el CPM, para no ir nodo por nodo
(`furthestVisibleHopOnPath`, `NavigationGraph.java:259-311`). Es "por planta" porque
`isVisible(Vec3,Vec3)` devuelve `false` si los puntos están en plantas distintas
(`NavigationGraph.java:246-251`): entre plantas hay una losa. Así el cruce lo hace
exclusivamente la arista de escalera.

### P: ¿Por qué la visibilidad es por planta y no global 2D?
Dos motivos: (1) hay una **losa** entre plantas, dos puntos a distinta `z` no tienen visibilidad
física; (2) PB y P1 comparten `(x,y)` en la Escuela, y con visibilidad global 2D un agente de PB
"vería" el aula de arriba y el A\* rutearía a través de la losa. La visibilidad por planta lo
impide: `isVisible` sólo evalúa las paredes de la planta del punto (`wallsByFloor`,
`NavigationGraph.java:104-107`).

### P: ¿El grafo se reconstruye en cada paso o se cachea? Escuché que tarda ~1 min. {#cache}
Se construye **una sola vez en `init()`** (`GraphBuilder.fromGeometry`) y queda cacheado en el
campo `mesh` de `StubGraph`; cada `nextVisibleHop` sólo **lee** esa malla
(`StubGraph.java:110-118`). El ~1 min es el costo único de mallar 0.20 m sobre 60×60 m en dos
plantas (más el descanso a `z=1.5`), antes del loop; en runtime el ruteo es sólo A\* + FVP.

---

## Preguntas "trampa" (por qué no / qué pasa si / limitaciones)

### P: ¿Qué era el bug de que "nadie subía" a la planta alta y cómo lo arreglaron? {#nadie-subia}
Al correr el primer escenario de dos plantas ningún agente llegaba a P1 (`maxz=0`), sin error en
runtime (D11): el FVP ruteaba hasta el **pie** y se quedaba ahí porque el tope "no es visible"
(otra planta), y un agente ya sobre la escalera no veía ningún nodo (su `z` está entre plantas).
Lo resuelven dos mecanismos: (1) el FVP avanza al tope al alcanzar el pie a lo largo del eje
(`STAIR_FOOT_REACH`, `NavigationGraph.java:290-294`); (2) un agente sobre la escalera recibe como
hop el extremo hacia la planta del target (`stairTraversalHop`, `NavigationGraph.java:160-165` y
`:323-348`). No fue un defecto de diseño del grafo 3D sino un gap de integración que sólo aparece
con dos plantas reales.

### P: ¿Qué causaba el "salto de z" (el teletransporte a mitad de tramo) y cuáles fueron las DOS causas? {#salto-z}
Agentes que saltaban de `z=0` a `z≈0.55` en un frame (`max |Δz|/frame ≈ 0.56`, D21) — era el
ruteo, con dos causas concurrentes:
1. **La grilla ponía nodos DENTRO de la huella de la escalera**, así el A\* ruteaba al pie por
   dentro del tubo a ras del piso. Fix: excluir esos nodos (`insideAnyStairFootprint` →
   `Stairs.containsXy`, `GridNodeReducer.java:220-221` y `:396-401`).
2. **El cambio de hop del pie al tope ocurría LEJOS del pie** (reach 1.5 euclídeo → flip a mitad
   de huella, `zAt ≈ 0.56`). Fix: reach a **0.15 medido a lo largo del eje**, así el flip queda
   pegado al pie (`zAt ≈ 0`) (`NavigationGraph.java:64` + `alongAxisDistFromNear` `:359-366`).

**Por qué ambas:** son inseparables. Reach chico sin exclusión regresa el ruteo (turnback=4,
P1=10); reach generoso sin exclusión reintroduce el salto (0.56). Con las dos: turnback=0, P1=15,
`max |Δz|/frame` = 0.081 (D21).

### P: ¿Qué era el deadlock de boca de escalera y cómo lo resolvieron? {#deadlock}
Con N≥300 la evacuación se clavaba: un arco estable de agentes en la boca superior de UNA
escalera detenía el flujo (N=500: 168 atrapados, flujo 0 durante 600 s; D24). Tres defectos,
cuatro cambios:
1. **`keepMainFreeComponent` tiraba los descansos reales** (en `z=1.5` la región libre mayor era
   una franja fantasma del bounding box → aristas "wormhole" de 49–54 m). Fix: conservar las
   componentes **ancladas** por un extremo de escalera (`anchoredComponents`,
   `GridNodeReducer.java:269-282` y `:349-371`).
2. **Una sola ruta de bajada** embudaba la planta alta. Fix: **densificación** que agrega aristas
   visibles restantes sin cruce (`GridNodeReducer.java:954-967`).
3. **La boca era un nodo puntual** y el flip ignoraba el desvío lateral. Fix: **gate lateral**
   (flip exige desvío ≤ semiancho, `NavigationGraph.java:291-292`) + **boca ancha** (`mouthPoint`,
   cada agente apunta a su proyección sobre el segmento, `NavigationGraph.java:374-386`).
4. **Espaciado máximo de nodos** (`enforceNodeSpacing`, `MAX_NODE_SPACING = 12 m`): áreas
   abiertas quedaban con 2–3 nodos y distorsionaban distancias
   (`GridNodeReducer.java:327-341`).

Resultado: N=500 evacúa 499/500 y el edificio se vacía a t=310 s; N=120 mejora −20% el t_evac
medio y −34% el máximo. Suite 143 tests verdes.

**⚠️ Punto débil — deuda conocida (D24).** El ruteo sigue prefiriendo **mayormente UNA escalera**
(la sur): ya no hay deadlock pero la otra queda subusada; balancear la carga queda como mejora
opcional. A **N=800** (~1.6× el máximo del barrido, que llega a N=500) la boca se arrastra
(631/800): es el límite documentado, por eso el barrido se corta en N=500 donde las corridas
completan la evacuación (D25).

### P: ¿Por qué el FVP no interpola sobre la arista de escalera? ¿No sería más suave?
Porque el cruce de plantas es **dinámica**, no ruteo: `binarySearchFVP` corta si el tramo cruza
plantas y devuelve `visibleEnd` (el pie) (`NavigationGraph.java:434-440`); el grafo lleva a la
boca y el CPM sube el tramo interpolando la `z` (paso 6). Separar responsabilidades es lo que
hace que la `z` enganche suave (D21).

### P: El switchback tiene dos tramos casi paralelos (piso0↔descanso y descanso↔piso1). ¿Cómo evita el grafo confundirlos?
Con un **guard de rango-z** en `stairTraversalHop`: además del eje planar (`STAIR_AXIS_TOL`),
exige que la `z` del agente caiga en el rango `[min,max]` de esa arista
(`NavigationGraph.java:337-341`), porque la sola distancia planar no alcanza cuando las huellas
quedan próximas (D19). Con un solo tramo el guard no cambia nada.

### P: ¿Por qué construir el grafo desde `Geometry` y no re-parsear los CSV, como antes?
Porque `Geometry` es la única fuente que tiene la `z` y las escaleras (`floors()`, `wallsOn(z)`,
`stairs()`). Antes el grafo re-parseaba `WALLS.csv`/`SERVERS.csv` y descartaba la `z` (D6); al
pasar por `Geometry` respeta la planta de cada pared y conoce las escaleras para coserlas.

### P: ¿Qué pasa si una planta no tiene paredes, o una escalera no tiene nodos cerca?
- **Planta sin paredes**: `GraphBuilder` la saltea (no hay bounding box), pero sus escaleras
  igual se agregan (`GraphBuilder.java:66-70`).
- **Extremo sin nodo visible cerca**: `connectToFloor` cae en el nodo más cercano de su planta
  aunque no sea visible (`bestAny`, `GraphBuilder.java:174-177`), para no dejarlo desconectado.

### P: ¿La aleatorización de hops rompe la reproducibilidad?
No. El FVP puede randomizar el hop dentro de un cono ≤10° respecto del óptimo
(`randomizeHopOnEdge`), pero sólo entre dos nodos del mismo plano y con un RNG sembrado por la
semilla global (`Seeds.rng("navgraph")`, `NavigationGraph.java:32`); sin `simped.seed` el stream
es determinista (D23). Sobre aristas de escalera no hay randomización.

### P: ¿Por qué visibilidad "tocar la pared = no visible"? ¿No es demasiado conservador?
Es una política deliberada de `VisibilityUtils.segmentsIntersect`: cualquier contacto (vértice o
colineal) cuenta como intersección (`VisibilityUtils.java:34-56`), porque un hop que roza una
esquina es riesgoso para el CPM. El costo es despreciable: la grilla es densa (0.20 m) y siempre
hay un nodo alternativo.

---

## Navegación

- Snippets verbatim de este módulo: [../codigo/03-grafo.md](../codigo/03-grafo.md)
- Física del CPM (interpolación de `z` en escalera, anti-tunneling por planta):
  [../codigo/02-fisica-cpm.md](../codigo/02-fisica-cpm.md)
- Vecinos (CIM por planta + puente de escalera) y geometría:
  [../codigo/04-vecinos-cim-geometria.md](../codigo/04-vecinos-cim-geometria.md)
- Resumen de implementación: [../resumenes/02-implementacion.md](../resumenes/02-implementacion.md)
- Informe (§Implementación): [informe §Implementación](../../informe/informe.tex)
- Decisiones: [D6](../../.claude/DECISIONES.md) (grafo 3D), [D11](../../.claude/DECISIONES.md) (FVP
  al pie / "nadie subía"), [D21](../../.claude/DECISIONES.md) (salto de z),
  [D24](../../.claude/DECISIONES.md) (deadlock de boca)
