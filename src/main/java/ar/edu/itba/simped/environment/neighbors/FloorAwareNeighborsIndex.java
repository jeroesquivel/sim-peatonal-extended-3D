package ar.edu.itba.simped.environment.neighbors;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.NeighborType;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.core.ports.NeighborsIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CIM por planta (paso 5, D8). Facade sobre {@link CimNeighborsIndex}: mantiene
 * <b>una grilla 2D por planta</b> y un <b>índice "puente" por escalera</b>, de
 * modo que la detección de vecinos es independiente por planta salvo en las
 * escaleras.
 *
 * <p>Cada agente vive en exactamente un contenedor —una grilla de planta o el
 * puente de una escalera— según su {@code z} (ver {@link #classify}). El acople
 * en los descansos lo resuelve {@link #neighborsOf}: un agente en planta también
 * consulta los puentes de las escaleras que aterrizan en su planta, y un agente
 * en escalera consulta además las dos grillas de planta de sus extremos.</p>
 *
 * <p><b>Ids de pared.</b> El {@link Neighbor} de pared lleva como {@code id} el
 * índice de la pared, que el {@code CpmOperationalModel} resuelve contra su lista
 * de paredes. Para que ese espacio de ids sea consistente con grillas por planta,
 * el facade mantiene una <b>lista global de paredes</b> ({@link #globalWalls})
 * y reescribe el id de cada vecino {@code WALL} de local (índice dentro de la
 * grilla de su planta) a global. App debe pasarle esa misma lista al OM vía
 * {@link #globalWalls(Geometry)}.</p>
 *
 * <p>En escenarios de una sola planta el facade degenera a una única grilla con
 * mapa de ids identidad: comportamiento idéntico al {@link CimNeighborsIndex} 2D.</p>
 */
public final class FloorAwareNeighborsIndex implements NeighborsIndex {

    private static final double FLOOR_EPS = Geometry.FLOOR_EPS;

    private final double[] floorLevels;
    private final CimNeighborsIndex[] floorGrids;   // paralelo a floorLevels; null si la planta no tiene paredes
    private final int[][] localToGlobal;            // [floor][localWallId] -> globalWallId
    private final List<Wall> globalWalls;

    private final Stairs[] stairs;
    private final StairBridge[] bridges;            // paralelo a stairs
    private final int[] stairFootFloor;             // índice de planta del pie
    private final int[] stairTopFloor;              // índice de planta del tope
    private final List<List<Integer>> stairsByFloor; // por planta: índices de escaleras que aterrizan en ella

    /** Dónde está indexado un agente: una planta o una escalera. */
    private final Map<Integer, Loc> membership = new HashMap<>();

    private record Loc(boolean stair, int index) {}

    private FloorAwareNeighborsIndex(double[] floorLevels, CimNeighborsIndex[] floorGrids,
                                     int[][] localToGlobal, List<Wall> globalWalls,
                                     Stairs[] stairs, StairBridge[] bridges,
                                     int[] stairFootFloor, int[] stairTopFloor,
                                     List<List<Integer>> stairsByFloor) {
        this.floorLevels = floorLevels;
        this.floorGrids = floorGrids;
        this.localToGlobal = localToGlobal;
        this.globalWalls = globalWalls;
        this.stairs = stairs;
        this.bridges = bridges;
        this.stairFootFloor = stairFootFloor;
        this.stairTopFloor = stairTopFloor;
        this.stairsByFloor = stairsByFloor;
    }

    // ── Construcción ─────────────────────────────────────────────────────────

    public static FloorAwareNeighborsIndex fromGeometry(Geometry geometry, double rmax) {
        return fromGeometry(geometry, rmax, null);
    }

    public static FloorAwareNeighborsIndex fromGeometry(Geometry geometry, double rmax,
                                                        Double cellSizeOverride) {
        List<Double> floors = geometry.floors();
        int nf = floors.size();
        double[] floorLevels = new double[nf];
        CimNeighborsIndex[] grids = new CimNeighborsIndex[nf];
        int[][] l2g = new int[nf][];
        List<Wall> globalWalls = new ArrayList<>();

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

        List<Stairs> stairList = geometry.stairs();
        int ns = stairList.size();
        Stairs[] stairs = stairList.toArray(new Stairs[0]);
        StairBridge[] bridges = new StairBridge[ns];
        int[] footFloor = new int[ns];
        int[] topFloor = new int[ns];
        List<List<Integer>> stairsByFloor = new ArrayList<>(nf);
        for (int fi = 0; fi < nf; fi++) stairsByFloor.add(new ArrayList<>());
        for (int si = 0; si < ns; si++) {
            Stairs s = stairs[si];
            bridges[si] = new StairBridge();
            footFloor[si] = nearestFloorIndex(floorLevels, s.foot().z());
            topFloor[si] = nearestFloorIndex(floorLevels, s.top().z());
            stairsByFloor.get(footFloor[si]).add(si);
            if (topFloor[si] != footFloor[si]) {
                stairsByFloor.get(topFloor[si]).add(si);
            }
        }

        return new FloorAwareNeighborsIndex(floorLevels, grids, l2g, globalWalls,
                stairs, bridges, footFloor, topFloor, stairsByFloor);
    }

    /**
     * Lista global de paredes (orden = concatenación de {@code wallsOn(z)} sobre
     * {@code floors()}), tal como la arma {@link #fromGeometry}. App se la pasa al
     * {@code CpmOperationalModel} para que el espacio de ids de pared coincida.
     */
    public static List<Wall> globalWalls(Geometry geometry) {
        List<Wall> out = new ArrayList<>();
        for (double z : geometry.floors()) {
            for (ar.edu.itba.simped.core.Wall cw : geometry.wallsOn(z)) {
                out.add(new Wall(cw.p1(), cw.p2()));
            }
        }
        return out;
    }

    /** Vista de la lista global de paredes que usa este índice. */
    public List<Wall> walls() {
        return List.copyOf(globalWalls);
    }

    // ── NeighborsIndex ───────────────────────────────────────────────────────

    @Override
    public void update(AgentState agent) {
        Loc now = classify(agent);
        Loc prev = membership.put(agent.id(), now);
        if (prev != null && !prev.equals(now)) {
            removeFrom(prev, agent.id());
        }
        addTo(now, agent);
    }

    @Override
    public void remove(int agentId) {
        Loc loc = membership.remove(agentId);
        if (loc != null) {
            removeFrom(loc, agentId);
        }
    }

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

    private void addFloorNeighbors(AgentState self, double rmax, int fi, List<Neighbor> out) {
        if (floorGrids[fi] != null) {
            out.addAll(remapWalls(floorGrids[fi].neighborsOf(self, rmax), fi));
        }
    }

    private void addTo(Loc loc, AgentState agent) {
        if (loc.stair()) {
            bridges[loc.index()].update(agent);
        } else if (floorGrids[loc.index()] != null) {
            floorGrids[loc.index()].update(agent);
        }
    }

    private void removeFrom(Loc loc, int agentId) {
        if (loc.stair()) {
            bridges[loc.index()].remove(agentId);
        } else if (floorGrids[loc.index()] != null) {
            floorGrids[loc.index()].remove(agentId);
        }
    }

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

    // ── Clasificación por planta / escalera ──────────────────────────────────

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

    private static int nearestFloorIndex(double[] floorLevels, double z) {
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < floorLevels.length; i++) {
            double d = Math.abs(floorLevels[i] - z);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    // ── Índice "puente" por escalera ─────────────────────────────────────────

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
    }
}