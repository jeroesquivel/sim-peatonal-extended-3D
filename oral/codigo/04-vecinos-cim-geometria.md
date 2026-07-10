# Vecinos (CIM por planta) y Geometría

Defensa del módulo de **detección de vecinos** (CIM por planta, [D8](../../.claude/DECISIONES.md)) y
de la **geometría por planta** ([D5](../../.claude/DECISIONES.md)). El enunciado pide que *"la
detección de vecinos sea independiente por planta, salvo en las escaleras"* y que paredes (por su
punto más cercano) y vértices se sigan detectando. Cada afirmación con su cita y bloque verbatim.

Archivos:

- `neighbors/FloorAwareNeighborsIndex.java` (307 líneas): facade con una grilla CIM 2D por planta +
  índice "puente" por escalera.
- `neighbors/CimNeighborsIndex.java` (231 líneas): grilla CIM 2D base (una planta), intacta.
- `neighbors/Wall.java`: detección por punto más cercano y vértices (clamp).
- `core/ports/Geometry.java` y `environment/geometry/GeometryImpl.java`: consultas por planta.

---

## 1. Qué es el CIM (Cell Index Method) y por qué una grilla por planta

**CIM**: en vez de O(N²), divide el espacio en celdas `≥ rmax` y cada agente compara sólo contra su
celda y las 8 adyacentes (barrido 3×3), devolviendo agentes y paredes por **distancia planar**
(`CimNeighborsIndex.java:129-161`). Las paredes (estáticas) se indexan una vez en el constructor; los
agentes se re-indexan perezosamente (javadoc `CimNeighborsIndex.java:18-29`).

**Por qué una grilla por planta.** El CIM es 2D (indexa por `(x,y)`) y las aulas de PB y P1 comparten
`(x,y)`. Con una sola grilla, un alumno de PB "vería" y esquivaría a uno de P1 parado justo encima
(hay una losa en medio): absurdo. D8 mantiene **una `CimNeighborsIndex` por planta** con `wallsOn(z)`.
El facade `FloorAwareNeighborsIndex` orquesta esto (javadoc líneas 16-37) creando una grilla por cada
`geometry.floors()` (`FloorAwareNeighborsIndex.java:90-108`):

```java
// src/main/java/ar/edu/itba/simped/environment/neighbors/FloorAwareNeighborsIndex.java:90-108
        for (int fi = 0; fi < nf; fi++) {
            double z = floors.get(fi);
            floorLevels[fi] = z;
            List<ar.edu.itba.simped.core.Wall> coreWalls = geometry.wallsOn(z);
            List<Wall> floorWalls = new ArrayList<>(coreWalls.size());
            int[] map = new int[coreWalls.size()];
            for (int li = 0; li < coreWalls.size(); li++) {
                ar.edu.itba.simped.core.Wall cw = coreWalls.get(li);
                Wall nw = new Wall(cw.p1(), cw.p2());
                map[li] = globalWalls.size();
                globalWalls.add(nw);
                floorWalls.add(nw);
            }
            l2g[fi] = map;
            // Sin paredes no hay bounding box para la grilla: esa planta no
            // indexa nada por grilla (sus agentes se ven sólo vía puentes).
            grids[fi] = floorWalls.isEmpty() ? null
                    : new CimNeighborsIndex(floorWalls, rmax, cellSizeOverride);
        }
```

**Paridad con 1 planta.** Con un solo nivel: una única grilla, mapa de ids identidad, sin escaleras —
byte-idéntico al `CimNeighborsIndex` 2D original.

---

## 2. Independencia por planta salvo en las escaleras: el acople en los descansos

El corazón de D8 está en `neighborsOf`: **quién consulta qué** (`FloorAwareNeighborsIndex.java:173-201`):

```java
// src/main/java/ar/edu/itba/simped/environment/neighbors/FloorAwareNeighborsIndex.java:173-201
    @Override
    public List<Neighbor> neighborsOf(AgentState self, double rmax) {
        Loc loc = classify(self);
        List<Neighbor> out = new ArrayList<>();

        if (!loc.stair()) {
            int fi = loc.index();
            if (floorGrids[fi] != null) {
                out.addAll(remapWalls(floorGrids[fi].neighborsOf(self, rmax), fi));
            }
            // Acople en descansos: escaleras que aterrizan en esta planta.
            for (int si : stairsByFloor.get(fi)) {
                bridges[si].agentsWithin(self, rmax, out);
            }
        } else {
            int si = loc.index();
            bridges[si].agentsWithin(self, rmax, out);
            addFloorNeighbors(self, rmax, stairFootFloor[si], out);
            if (stairTopFloor[si] != stairFootFloor[si]) {
                addFloorNeighbors(self, rmax, stairTopFloor[si], out);
            }
        }

        out.sort(Comparator
                .comparingDouble(Neighbor::distance)
                .thenComparing(n -> n.type().name())
                .thenComparingInt(Neighbor::id));
        return out;
    }
```

- **Agente sobre una planta** (`!loc.stair()`): su grilla + los puentes de las escaleras que aterrizan
  en esa planta (`stairsByFloor.get(fi)`). Ve a los que arrancan/terminan el tramo.
- **Agente sobre una escalera** (`loc.stair()`): su puente + las dos grillas de planta de los extremos
  (`stairFootFloor`, `stairTopFloor`). Ve a los parados en los descansos.

Así se materializa el "salvo en las escaleras": vecindad estanca por planta, acoplada en los
descansos. Cada agente vive en **exactamente un** contenedor y son **disjuntos** → sin deduplicar. El
acople lo habilita `stairsByFloor`, precomputado en la construcción (`FloorAwareNeighborsIndex.java:118-127`).

---

## 3. La clasificación runtime: en qué contenedor vive el agente

`classify` decide el contenedor según la `z` (`FloorAwareNeighborsIndex.java:241-260`):

```java
// src/main/java/ar/edu/itba/simped/environment/neighbors/FloorAwareNeighborsIndex.java:241-260
    /** Decide en qué contenedor vive el agente según su posición 3D. */
    private Loc classify(AgentState a) {
        int nf = nearestFloorIndex(floorLevels, a.z());
        if (Math.abs(floorLevels[nf] - a.z()) <= FLOOR_EPS) {
            return new Loc(false, nf);
        }
        // z entre plantas: el agente está sobre una escalera. La primera escalera
        // cuyo rango z lo abarca y cuya huella contiene (x,y) (las huellas no se
        // solapan en escenarios válidos).
        for (int si = 0; si < stairs.length; si++) {
            Stairs s = stairs[si];
            double zlo = Math.min(s.foot().z(), s.top().z());
            double zhi = Math.max(s.foot().z(), s.top().z());
            if (a.z() < zlo - FLOOR_EPS || a.z() > zhi + FLOOR_EPS) continue;
            if (s.containsXy(a.x(), a.y())) {
                return new Loc(true, si);
            }
        }
        return new Loc(false, nf); // fallback: planta más cercana
    }
```

Si la `z` coincide (±`FLOOR_EPS`) con un nivel discreto → grilla de planta; si está **entre** plantas
→ puente de la escalera cuya huella contiene `(x,y)`. Sólo el avance por escalera la pone entre
plantas. La pertenencia la resuelve `Stairs.containsXy` (proyección sobre el eje en `[0,1]`,
perpendicular `≤ width/2`, `Stairs.java:99-110`), que **comparte la misma proyección** que
`Stairs.zAt` —`containsXy` decide *pertenencia*, `zAt` *interpola*—, sin geometría duplicada.

---

## 4. El índice "puente" por escalera y por qué fuerza bruta

El puente es un bucket plano de agentes: **sin** grilla, barrido lineal por distancia planar
(`FloorAwareNeighborsIndex.java:277-305`):

```java
// src/main/java/ar/edu/itba/simped/environment/neighbors/FloorAwareNeighborsIndex.java:277-305
    /**
     * Bucket de agentes que están físicamente sobre una escalera. Pocos agentes
     * por escalera → barrido lineal por distancia planar (igual semántica que el
     * CIM). No indexa paredes propias: el agente en escalera obtiene las paredes
     * de los descansos vía las dos grillas de planta.
     */
    private static final class StairBridge {
        private final Map<Integer, AgentState> agents = new HashMap<>();

        void update(AgentState a) {
            agents.put(a.id(), a);
        }

        void remove(int agentId) {
            agents.remove(agentId);
        }

        /** Agrega a {@code out} los agentes del puente dentro de {@code rmax} (planar), excluyendo {@code self}. */
        void agentsWithin(AgentState self, double rmax, List<Neighbor> out) {
            for (AgentState other : agents.values()) {
                if (other.id() == self.id()) continue;
                double dx = self.x() - other.x();
                double dy = self.y() - other.y();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist <= rmax) {
                    out.add(new Neighbor(other.id(), NeighborType.AGENT, dist, other));
                }
            }
        }
```

**Por qué fuerza bruta.** Pocos agentes por tramo (angosto, flujo serializado): O(k²) es más barato y
simple que montar una grilla CIM (bounding box, celdas, re-indexado). Misma semántica (distancia
planar, umbral `rmax`), sólo cambia la estructura. El puente **no** indexa paredes propias: las de los
descansos las aporta el acople con las dos grillas de planta (§2), coherente con
[D9](../../.claude/DECISIONES.md) (el CPM en escalera usa la **unión** de paredes de las dos plantas,
ver [Física CPM §4](02-fisica-cpm.md)).

---

## 5. Ids de pared globales compartidos con el CPM

**Problema.** El `Neighbor` de tipo `WALL` no lleva geometría (`null`): lleva un `id` = índice de la
pared, y el `CpmOperationalModel` hace `walls.get(id)` contra **su** lista. El id de pared es un
espacio global compartido entre índice y OM; ids locales por planta romperían la repulsión (o
`IllegalStateException`, ver [Física CPM §5](02-fisica-cpm.md)).

**Solución.** El facade mantiene una **lista global de paredes** (concatenación determinística de
`wallsOn(z)` sobre `floors()`) y, por planta, un mapa `local→global`; en `neighborsOf` reescribe el
`id` de cada vecino `WALL` (`FloorAwareNeighborsIndex.java:225-237`):

```java
// src/main/java/ar/edu/itba/simped/environment/neighbors/FloorAwareNeighborsIndex.java:225-237
    /** Reescribe el id de cada vecino WALL de local (planta {@code fi}) a global. */
    private List<Neighbor> remapWalls(List<Neighbor> neighbors, int fi) {
        int[] map = localToGlobal[fi];
        List<Neighbor> out = new ArrayList<>(neighbors.size());
        for (Neighbor n : neighbors) {
            if (n.type() == NeighborType.WALL) {
                out.add(new Neighbor(map[n.id()], NeighborType.WALL, n.distance(), null));
            } else {
                out.add(n);
            }
        }
        return out;
    }
```

El facade expone `globalWalls(Geometry)` (mismo orden `wallsOn(z)` sobre `floors()`,
`FloorAwareNeighborsIndex.java:138-146`) que App pasa al `CpmOperationalModel`; el factory
`CpmOperationalModel.fromGeometry` replica ese orden (ver [Física CPM §4](02-fisica-cpm.md)), así
`walls.get(neighborId)` resuelve la pared correcta. **Nota:** los vecinos `WALL` ya vienen filtrados
por planta; el id global sólo recupera la geometría, no reintroduce paredes de otra planta.

---

## 6. Detección de paredes por punto más cercano y de vértices como vecinos

El enunciado pide detectar paredes por su **punto más cercano** y sus **vértices** cuando el vértice
es ese punto. En el CIM la distancia es `w.distanceTo(selfPos)` (`CimNeighborsIndex.java:153-157`).
`Wall.distanceTo` proyecta el agente sobre la recta del muro y **clampea** `t` a `[0,1]`
(`Wall.java:22-35`):

```java
// src/main/java/ar/edu/itba/simped/environment/neighbors/Wall.java:22-35
    public Vec2 closestPointTo(Vec2 p) {
        double dx = p2.x() - p1.x();
        double dy = p2.y() - p1.y();
        double len2 = dx * dx + dy * dy;
        if (len2 == 0.0) return p1;
        double t = ((p.x() - p1.x()) * dx + (p.y() - p1.y()) * dy) / len2;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;
        return new Vec2(p1.x() + t * dx, p1.y() + t * dy);
    }

    public double distanceTo(Vec2 p) {
        return closestPointTo(p).distanceTo(p);
    }
```

El **clamp** resuelve el vértice: si la proyección cae más allá del extremo (`t<0→0`, `t>1→1`), el
punto más cercano es el endpoint `p1`/`p2`. Un agente que rodea una esquina detecta el vértice como
vecino, como pide el enunciado. Es comportamiento del `Wall` original; la ampliación 3D lo preserva
(las paredes por planta usan el mismo `Wall`).

---

## 7. Geometría por planta (D5)

El módulo de vecinos consume la geometría **agrupada por planta**, identificada por su valor `z`
(double) con tolerancia `FLOOR_EPS`. Las consultas son métodos `default` del puerto `Geometry`, así
las heredan todas las implementaciones (`Geometry.java:54-90`):

```java
// src/main/java/ar/edu/itba/simped/core/ports/Geometry.java:54-90
    /** Tolerancia para comparar/deduplicar niveles de planta z. */
    double FLOOR_EPS = 1e-6;

    /**
     * Niveles de planta distintos presentes en la geometría (de walls, exits,
     * locations, generators, servers y los extremos de las escaleras),
     * ordenados ascendentemente y deduplicados por {@link #FLOOR_EPS}.
     */
    default List<Double> floors() {
        List<Double> all = new ArrayList<>();
        for (Wall w : walls()) all.add(w.z());
        for (Exit e : exits()) all.add(e.z());
        for (Location l : locations()) all.add(l.z());
        for (GeneratorZone g : generatorZones()) all.add(g.z());
        for (ServerZone s : serverZones()) all.add(s.z());
        for (Stairs s : stairs()) {
            all.add(s.foot().z());
            all.add(s.top().z());
        }
        all.sort(Double::compare);
        List<Double> out = new ArrayList<>();
        for (double z : all) {
            if (out.isEmpty() || Math.abs(out.get(out.size() - 1) - z) > FLOOR_EPS) {
                out.add(z);
            }
        }
        return out;
    }

    /** Paredes que pertenecen a la planta {@code z} (±{@link #FLOOR_EPS}). */
    default List<Wall> wallsOn(double z) {
        List<Wall> out = new ArrayList<>();
        for (Wall w : walls()) {
            if (Math.abs(w.z() - z) < FLOOR_EPS) out.add(w);
        }
        return out;
    }
```

`floors()` deriva las plantas de **todos** los elementos (incluidos los extremos de escalera, para que
aparezca el descanso), ordena y deduplica por `FLOOR_EPS`; `wallsOn(z)` filtra por planta (hay análogas
para exits/locations/generators/servers). La razón: grafo (paso 4) y CIM (paso 5) operan **por planta**.
`stairsAt(z)` devuelve las escaleras que tocan `z` en pie **o** tope, para coser los subgrafos
(`Geometry.java:133-142`). La implementación `GeometryImpl` (`GeometryImpl.java:18-24`) es un record
inmutable con las listas de cada elemento + escaleras, heredando las consultas por planta del puerto.
Lo arma `GeometryAssembler.assemble` juntando CSV de geometría + escaleras + params
(`GeometryAssembler.java:48-64`), quedando la geometría con su planta por elemento. (La propagación de
la `z` de cada elemento CSV —el bug de defaults `z=0` que sólo apareció con dos plantas reales— es
dominio de [tipos base e IO](01-tipos-base-io.md).)

---

## 8. Alternativas descartadas

- **Snap del agente de escalera a "la planta más cercana"** (sin puente): produce una discontinuidad
  en el punto medio del tramo — dos agentes casi juntos, uno bajo y otro sobre el medio, caerían en
  grillas distintas y **no se verían**. El enunciado pide que en la escalera sí se detecten
  ([D8](../../.claude/DECISIONES.md), [informe §Implementación](../../informe/informe.tex), "Vecinos
  por planta").
- **Una grilla 3D única** (celdas en `z`): los pisos son planos y discretos (0 y 3 m), casi todas las
  celdas verticales quedan vacías → overhead sin beneficio ([D8](../../.claude/DECISIONES.md)).
- **Lógica de plantas dentro de `CimNeighborsIndex`** en vez de un facade: acopla y reescribe una clase
  2D testeada; se prefirió el facade para no arriesgar regresiones en el caso 2D (que queda intacto).

---

## Navegación

- Hermano (código): [Física CPM/A-CPM](02-fisica-cpm.md) · [Grafo de navegación (A* 3D)](03-grafo.md) ·
  [Tipos base e IO](01-tipos-base-io.md)
- Q&A de esta área: [Blindaje — Vecinos (CIM)](../blindaje/q-vecinos-cim.md)
- Resumen: [Implementación](../resumenes/02-implementacion.md)
- Fuentes: [informe (LaTeX)](../../informe/informe.tex) ·
  [DECISIONES.md](../../.claude/DECISIONES.md) (D5, D8, D9)
