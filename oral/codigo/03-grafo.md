# Grafo de navegación (A\* + Furthest Visible Point, 3D por planta)

Defensa del **módulo de ruteo**: grafo por planta, unión por aristas de escalera, heurística
euclídea 3D del A\*, visibilidad/FVP por planta, y los tres fixes multiplanta: FVP que ruteaba al
pie (D11), "salto de z" (D21), deadlock de boca (D24). Cada afirmación con su cita y bloque verbatim.

Paquete `src/main/java/ar/edu/itba/simped/environment/graph/`:

- `StubGraph.java` — puerto `Graph`, delega en la malla.
- `GraphBuilder.java` — `fromGeometry`: malla por planta + cosido por escaleras.
- `NavigationGraph.java` (551 líneas) — `nextVisibleHop`, FVP por planta, cruce de escalera.
- `AStarPathfinder.java` — A\* con heurística euclídea 3D.
- `VisibilityUtils.java` — visibilidad planar.
- `GridNodeReducer.java` (1132 líneas) — grilla por planta + exclusión de huella de escalera.

---

## 1. El puerto: `StubGraph` delega en la malla

`StubGraph` implementa `Graph`, construye la malla una vez desde `Geometry` (D6) y en runtime
delega el hop en `NavigationGraph.nextVisibleHop`. Firma `Vec3 → Vec3` (floor-aware): agente y
target llevan su `z`; antes de D6 el pipeline era `Vec2` — el `footTarget` pasó a `Vec3` para que
el ruteo conozca la planta del destino (`StubGraph.java:110-118`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/StubGraph.java:110-118
    @Override
    public Vec3 nextVisibleHop(Vec3 agentPosition, Vec3 target) {
        // Grafo 3D por planta (paso 4 Fase B): la malla resuelve el hop con
        // visibilidad por planta y A* con heurística euclídea 3D.
        if (mesh == null) {
            return target;
        }
        return mesh.nextVisibleHop(agentPosition, target);
    }
```

---

## 2. Grafo por planta unido por aristas de escalera (D6) {#grafo-por-planta}

`GraphBuilder.fromGeometry` es el corazón del ruteo multiplanta (D6): (1) corre `GridNodeReducer.reduce`
**una vez por planta** con `wallsOn(z)`; (2) eleva cada nodo con `n.withZ(z)`; (3) por cada escalera
agrega nodo al pie y al tope, conectados al nodo visible más cercano de su planta; (4) agrega la
**arista pie↔tope** con costo = distancia 3D del tramo inclinado (`GraphBuilder.java:61-94`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/GraphBuilder.java:61-94
        for (double z : geometry.floors()) {
            List<Wall> floorWalls = new ArrayList<>();
            for (ar.edu.itba.simped.core.Wall cw : geometry.wallsOn(z)) {
                floorWalls.add(new Wall(cw.p1(), cw.p2(), cw.z()));
            }
            if (floorWalls.isEmpty()) {
                // Sin paredes no hay bounding box para la grilla: esa planta no
                // aporta malla (sus escaleras igual se agregan abajo).
                continue;
            }
            List<ServerRect> servers = serverRectsOn(geometry, z);
            // ...
            GridNodeReducer.Result r =
                    GridNodeReducer.reduce(floorWalls, servers, geometry.stairs(), gridSpacing);

            int base = nodes.size();
            for (Vec2 n : r.nodes()) {
                nodes.add(n.withZ(z));
                nodesByFloor.computeIfAbsent(floorKey(z), k -> new ArrayList<>()).add(nodes.size() - 1);
            }
            // ...
            types.addAll(r.types());
            allWalls.addAll(floorWalls);
        }
```

Cosido: `s.foot().distanceTo(s.top())` es la **distancia euclídea 3D** (largo real del tramo
inclinado, no la proyección planar) (`GraphBuilder.java:96-112`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/GraphBuilder.java:96-112
        List<NavigationGraph.StairSpan> spans = new ArrayList<>();
        for (ar.edu.itba.simped.core.Stairs s : geometry.stairs()) {
            int footIdx = addNode(nodes, adjacency, types, s.foot());
            int topIdx = addNode(nodes, adjacency, types, s.top());
            nodesByFloor.computeIfAbsent(floorKey(s.foot().z()), k -> new ArrayList<>()).add(footIdx);
            nodesByFloor.computeIfAbsent(floorKey(s.top().z()), k -> new ArrayList<>()).add(topIdx);

            connectToFloor(nodes, adjacency, allWalls, nodesByFloor, footIdx);
            connectToFloor(nodes, adjacency, allWalls, nodesByFloor, topIdx);
            // Arista de escalera: costo = largo 3D del tramo inclinado.
            addEdge(adjacency, footIdx, topIdx, s.foot().distanceTo(s.top()));
            // D24: semiancho del tramo para el gate lateral del hop en la boca.
            spans.add(new NavigationGraph.StairSpan(s.foot(), s.top(), s.width() / 2.0));
        }
```

`connectToFloor` conecta el pie/tope con el nodo **visible** más cercano de su planta (planar); si
ninguno es visible, con el más cercano (`GraphBuilder.java:146-177`). Los costos intra-planta son la
distancia planar (= 3D con misma `z`); las aristas de escalera, el tramo inclinado 3D (D6).

---

## 3. A\* con heurística euclídea 3D {#astar-3d}

Heurística `h = nodes.get(neighbor).distanceTo(goal)`, con `distanceTo` sobre `Vec3` =
**distancia euclídea 3D** — lo que pide el enunciado. El costo real `g = currentG + edge.getValue()`
usa el peso de arista (para escalera, el largo inclinado 3D de §2), misma métrica que `h`
(`AStarPathfinder.java:54-65`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/AStarPathfinder.java:54-65
            for (var edge : neighbors.entrySet()) {
                int neighbor = edge.getKey();
                if (closed.contains(neighbor)) continue;

                double tentativeG = currentG + edge.getValue();
                if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    gScore.put(neighbor, tentativeG);
                    cameFrom.put(neighbor, current);
                    double f = tentativeG + nodes.get(neighbor).distanceTo(goal);
                    open.add(encode(f, neighbor));
                }
            }
```

`Vec3.distanceTo` es la norma euclídea 3D (`Vec3.java:41-43`); el javadoc de `AStarPathfinder`
(líneas 7-14) declara la intención.

**Por qué es admisible.** `h = ‖nodo − goal‖₂` (recta 3D) nunca sobreestima: todo camino es una
poligonal ≥ la recta que une sus extremos, y los costos de arista son longitudes euclídeas exactas.
A\* con esta heurística es óptimo (ver [q-grafo.md](../blindaje/q-grafo.md#admisible)).

---

## 4. Visibilidad y Furthest Visible Point POR PLANTA {#visibilidad-por-planta}

Regla clave: **un agente no "ve" nodos de otra planta**; el cruce sólo ocurre por la arista de
escalera (javadoc de `NavigationGraph`, líneas 12-23). `isVisible(Vec3,Vec3)` devuelve `false` entre
plantas distintas; en la misma hace visibilidad planar contra `wallsOfFloor(z)`
(`NavigationGraph.java:246-251`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/NavigationGraph.java:246-251
    private boolean isVisible(Vec3 from, Vec3 to) {
        if (!sameFloor(from.z(), to.z())) {
            return false;
        }
        return VisibilityUtils.isVisible(from.xy(), to.xy(), wallsOfFloor(from.z()));
    }
```

Las paredes se agrupan por planta en el constructor (`wallsByFloor`, clave `z` redondeada,
`NavigationGraph.java:104-107`). La visibilidad planar (segmento que no corte pared) está en
`VisibilityUtils.isVisible` (`VisibilityUtils.java:58-68`). El FVP recorre el camino A\* de atrás
hacia adelante y devuelve el punto más lejano visible (`furthestVisibleHopOnPath`,
`NavigationGraph.java:259-311`); como `isVisible` corta entre plantas, el FVP **nunca salta de
planta**. Igual `closestVisibleNode` (`NavigationGraph.java:474-485`) sólo matchea nodos de la misma
planta.

**Por qué por planta.** Hay una losa entre plantas: sin línea de vista real; y PB/P1 comparten
`(x,y)`, así que una visibilidad global 2D dejaría "ver" el aula de arriba desde abajo. La única
conexión legítima es la arista de escalera.

---

## 5. Fix del FVP que ruteaba al pie y se quedaba (D11) {#fvp-al-pie}

**Bug "nadie subía".** En el primer escenario de dos plantas ningún agente llegaba a P1 (`maxz=0`):
el FVP ruteaba al **pie** y se quedaba (el tope, en otra planta, "no es visible", §4; hop = posición,
estancado); y un agente ya **sobre** la escalera no veía ningún nodo (D11, fix 3).

**(a) El FVP avanza al tope al llegar al pie.** Con arista de escalera en el camino (`cur`/`nxt` en
plantas distintas), mantiene el pie como hop hasta que el agente alcanza el extremo cercano
**medido a lo largo del eje** (misma proyección que `Stairs.zAt`, engancha la `z` desde el piso sin
el salto 0→0.56 de D21) (`NavigationGraph.java:274-302`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/NavigationGraph.java:274-302
            if (!sameFloor(cur.z(), nxt.z())) {
                // Cambiar el hop al otro extremo SÓLO cuando el agente llegó al
                // extremo cercano (cur) MEDIDO A LO LARGO DEL EJE del tramo — no la
                // distancia euclídea. Es la misma proyección que usa Stairs.zAt, así
                // que al cambiar el hop zAt(agente) ≈ zAt(cur) = z de su planta y la
                // z engancha desde el nivel del piso, sin el salto 0→0.56 (D21). El
                // desvío perpendicular se ignora a propósito: el agente puede llegar
                // al extremo con offset lateral y aun así enganchar suave.
                // ...
                double lateralTol = lateralTolFor(cur, nxt);
                if (alongAxisDistFromNear(agent.xy(), cur.xy(), nxt.xy()) <= STAIR_FOOT_REACH
                        && perpDistFromAxis(agent.xy(), cur.xy(), nxt.xy()) <= lateralTol) {
                    return new FvpOnPath(nxt, i + 1, i + 1);
                }
                // ...
                return new FvpOnPath(mouthPoint(agent, cur, nxt, lateralTol), i, i);
            }
```

**(b) Un agente ya sobre la escalera recibe como hop el extremo hacia la planta del target.** Si su
`z` está entre plantas (`!isOnFloor`), `computeQuery` llama `stairTraversalHop`, que le da el extremo
del tramo hacia la planta del target para que siga subiendo/bajando (`NavigationGraph.java:323-348`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/NavigationGraph.java:323-348
    private Vec3 stairTraversalHop(Vec3 agent, Vec3 target) {
        for (int[] e : stairEdges) {
            Vec3 a = nodes.get(e[0]);
            Vec3 b = nodes.get(e[1]);
            if (distanceToSegmentXy(agent.xy(), a.xy(), b.xy()) > STAIR_AXIS_TOL) {
                continue;
            }
            // Guard multi-tramo (D19, switchback): con dos tramos del mismo
            // switchback cercanos entre sí (p.ej. piso0↔landing y landing↔piso1),
            // la sola distancia planar al eje no alcanza para distinguirlos si sus
            // huellas quedan próximas. Exigir además que la z del agente caiga
            // dentro del rango [min,max] de ESTA arista (± FLOOR_EPS) para no
            // matchear el tramo equivocado. Con un solo tramo no cambia nada: el
            // agente siempre está dentro del rango de su único tramo.
            double zlo = Math.min(a.z(), b.z());
            double zhi = Math.max(a.z(), b.z());
            if (agent.z() < zlo - FLOOR_EPS || agent.z() > zhi + FLOOR_EPS) {
                continue;
            }
            Vec3 lower = a.z() <= b.z() ? a : b;
            Vec3 upper = a.z() <= b.z() ? b : a;
            // Subir si el target está por encima del agente; bajar si está por debajo.
            return target.z() >= agent.z() ? upper : lower;
        }
        return null;
    }
```

Las `stairEdges` (pares de nodos adyacentes en plantas distintas) se precomputan en el constructor
(`NavigationGraph.java:112-119`). El FVP **no interpola sobre la arista de escalera** —
`binarySearchFVP` devuelve directamente `visibleEnd` (el pie) si el tramo cruza plantas; el cruce
físico lo hace el CPM (paso 6), no el ruteo (`NavigationGraph.java:434-440`). Ver
[q-grafo.md](../blindaje/q-grafo.md#nadie-subia).

---

## 6. Fix del "salto de z" (D21): DOS causas {#salto-de-z}

**Síntoma.** Agentes que saltaban de `z=0` a `z≈0.55` en un solo frame; `max |Δz|/frame ≈ 0.56`
(D21, §"Corrección del salto de z"): "concurrían dos causas".

**Causa 1 — la grilla ponía nodos DENTRO de la huella.** El A\* ruteaba al pie por dentro de la
huella y el agente subía el tubo a `z=0`. **Fix: excluir de la grilla los nodos dentro de la
huella** — una celda es libre sólo si además `!insideAnyStairFootprint`
(`GridNodeReducer.java:218-222`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/GridNodeReducer.java:218-222
                // El espacio personal también aplica a los servidores: ningún nodo a menos de
                // PERSONAL_SPACE de un servidor (además del margen de pared para caminar).
                free[g] = dWall >= WALL_CLEARANCE && dServer >= PERSONAL_SPACE
                        && !insideAnyStairFootprint(c);
            }
```

`insideAnyStairFootprint` usa `Stairs.containsXy` en **todas** las plantas (la huella no es piso
caminable en ninguna) (`GridNodeReducer.java:396-401`); las huellas se las pasa
`GraphBuilder.fromGeometry` vía `geometry.stairs()` (`GraphBuilder.java:77-78`).

**Causa 2 — el cambio de hop del pie al tope ocurría LEJOS del pie.** Con `STAIR_FOOT_REACH` viejo
(1.5, euclídeo) el hop cambiaba a mitad de huella (avance ≈0.37, `zAt ≈ 0.56`) y la `z` enganchaba
de golpe. **Fix: reach a 0.15 y medido a lo largo del eje** (`NavigationGraph.java:55-64`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/NavigationGraph.java:55-64
     * <p><b>Bug del salto de z (D21).</b> Con el valor viejo (1.5, medido como
     * distancia euclídea al pie) el cambio de hop ocurría a mitad de la huella
     * (avance ≈ 0.37 sobre un tramo de 4 m con Δz=1.5), donde {@code zAt ≈ 0.56}: al
     * enganchar la {@code z} el agente <b>saltaba de 0 a ~0.56 en un frame</b>. Con
     * 0.15 a-lo-largo-del-eje el cambio de hop ocurre pegado al pie (avance ≈0), donde
     * {@code zAt ≈ 0}, así la z arranca en ~0. Funciona junto con la <b>exclusión de
     * las huellas de la grilla</b> ({@code GridNodeReducer}), que limpia los nodos del
     * tubo y evita el atasco en el descanso (turnback) que este reach chico causaba
     * antes con la grilla vieja. */
    private static final double STAIR_FOOT_REACH = 0.15;
```

`alongAxisDistFromNear` proyecta sobre el eje, recortada a ≥0 si el agente se pasó
(`NavigationGraph.java:359-366`) — es la **misma proyección** que `Stairs.zAt` (`Stairs.java:90-92`),
por eso `zAt(agente) ≈ zAt(extremo)` al cambiar el hop.

**Las dos causas son inseparables** (D21): reach chico **sin** exclusión regresa el ruteo (turnback=4,
P1=10); reach generoso **sin** el cambio reintroduce el salto (0.56). Con ambos: turnback=0, P1=15,
`max |Δz|/frame` a **0.081**. Ver [q-grafo.md](../blindaje/q-grafo.md#salto-z).

---

## 7. Fix del deadlock de boca de escalera (D24) {#deadlock-boca}

**Síntoma.** Con N≥300 la evacuación se clavaba: un arco estable en la boca superior de UNA escalera
detenía el flujo (N=500: 168 atrapados, flujo 0 durante 600 s). Tres defectos encadenados, cuatro
cambios coordinados.

**(a) `keepMainFreeComponent` descartaba los descansos reales.** En `z=1.5` la región libre mayor era
la franja fantasma entre descansos (artefacto del bounding box), así que la malla del descanso real
se tiraba y los extremos se encadenaban por el vacío ("wormhole", aristas de 49–54 m). Fix: conservar
las componentes **ancladas por un extremo de escalera de esa planta** (`GridNodeReducer.java:269-282`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/GridNodeReducer.java:269-282
        Set<Integer> keep = anchoredComponents(comp);
        if (keep.isEmpty()) {
            int bestId = 0;
            for (int id = 1; id < sizes.size(); id++) {
                if (sizes.get(id) > sizes.get(bestId)) bestId = id;
            }
            keep = Set.of(bestId);
        }
        for (int g = 0; g < nx * ny; g++) {
            if (free[g] && !keep.contains(comp[g])) {
                free[g] = false;
                comp[g] = -1;
            }
        }
```

`anchoredComponents` busca la celda libre a ≤1 m del pie/tope de un tramo de **esta** planta
(`GridNodeReducer.java:349-354`); sin anclas conserva la mayor (idéntico al histórico).

**(b) El árbol de expansión dejaba UNA sola ruta de bajada.** `assemblePlanar` armaba un árbol sin
alternativas comparables → toda la planta alta se embudaba en una boca. Fix: **fase de densificación**
que agrega las aristas visibles restantes que no crucen y respeten el espacio personal
(`GridNodeReducer.java:954-967`), quedando un grafo planar (≤3n−6) con rutas alternativas.

**(c) La boca se trataba como nodo puntual y el flip ignoraba el desvío lateral.** Los agentes
corridos lateralmente recibían "bajá" y quedaban clavados contra las barandas, taponando; el resto
convergía isotrópicamente al nodo puntual → arco. **Fix doble.** *Gate lateral*: el flip exige estar
DE FRENTE al tramo (`perpDistFromAxis(...) <= lateralTol`, ver bloque de §5). *Boca ancha*: mientras
se acerca, el hop es la **proyección** del agente sobre el segmento de la boca (`mouthPoint`), cada uno
apuntando a su propio punto de entrada (`NavigationGraph.java:374-386`):

```java
// src/main/java/ar/edu/itba/simped/environment/graph/NavigationGraph.java:374-386
    private static Vec3 mouthPoint(Vec3 agent, Vec3 cur, Vec3 nxt, double halfWidth) {
        double dx = nxt.x() - cur.x(), dy = nxt.y() - cur.y();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return cur;
        // Perpendicular unitaria al eje del tramo.
        double px = -dy / len, py = dx / len;
        double s = (agent.x() - cur.x()) * px + (agent.y() - cur.y()) * py;
        double margin = 0.35; // hombro: no apuntar al filo de la baranda
        double lim = Math.max(0.0, halfWidth - margin);
        if (s > lim) s = lim;
        if (s < -lim) s = -lim;
        return new Vec3(cur.x() + s * px, cur.y() + s * py, cur.z());
    }
```

El semiancho llega desde `GraphBuilder` como `StairSpan` (`s.width()/2.0`, §2) y lo resuelve
`lateralTolFor`; sin tramos (mocks/CSV) cae en `STAIR_AXIS_TOL` (`NavigationGraph.java:401-406`).

**(d) Espaciado máximo de nodos.** Áreas abiertas grandes (recreo 30×60 m) quedaban con 2–3 nodos,
distorsionando distancias. `enforceNodeSpacing` agrega nodos hasta que ninguna celda libre quede a
más de `MAX_NODE_SPACING = 12 m`, sólo con línea de vista a un nodo existente
(`GridNodeReducer.java:327-341`).

**Resultado (D24)** (diagnóstico del fix, semilla 1 — no los observables canónicos de 5 semillas del
informe): N=500 evacúa 499/500 (antes 168 atrapados para siempre); N=120 mejora t_evac medio
63.1→50.3 s (−20%) y máx 137.8→90.6 s (−34%). Suite 143 tests, 0 fallos. Ver
[q-grafo.md](../blindaje/q-grafo.md#deadlock).

---

## 8. ¿Se cachea o se reconstruye? {#cache}

El grafo se construye **una sola vez** en `init()` (`StubGraph.fromGeometry` → `GraphBuilder`) y vive
en el campo `mesh`; cada `nextVisibleHop` sólo lee esa malla. El build de la Escuela tarda **~1 min**
(grilla 0.20 m sobre 60×60 en dos plantas + descansos); es normal. No se reconstruye por paso ni por
agente.

---

## Navegación

- Física del CPM (velocidad reducida e interpolación de `z` en escalera, anti-tunneling por
  planta): [../codigo/02-fisica-cpm.md](02-fisica-cpm.md)
- Vecinos (CIM por planta + puente de escalera) y geometría:
  [../codigo/04-vecinos-cim-geometria.md](04-vecinos-cim-geometria.md)
- Tipos base (`Vec3`, `AgentState.z`) e I/O: [../codigo/01-tipos-base-io.md](01-tipos-base-io.md)
- Preguntas de defensa de este módulo: [../blindaje/q-grafo.md](../blindaje/q-grafo.md)
- Resumen de implementación: [../resumenes/02-implementacion.md](../resumenes/02-implementacion.md)
- Informe (§Implementación, "Grafo de navegación", "La integración multiplanta", "Corrección del
  salto de z"): [informe §Implementación](../../informe/informe.tex)
- Decisiones: [D6](../../.claude/DECISIONES.md) (grafo 3D), [D11](../../.claude/DECISIONES.md) (FVP
  al pie), [D21](../../.claude/DECISIONES.md) (salto de z), [D24](../../.claude/DECISIONES.md)
  (deadlock de boca)
