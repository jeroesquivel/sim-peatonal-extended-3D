package ar.edu.itba.simped.environment.neighbors;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.NeighborType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.NeighborsIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cell Index Method para queries de vecinos (sub-módulo 5.3).
 *
 * <p>Las paredes son estáticas y se indexan una sola vez en el constructor.
 * Los agentes se registran vía {@link #update(AgentState)} y se re-indexan
 * en forma perezosa la primera vez que se llama a {@link #neighborsOf}
 * después de algún update.</p>
 *
 * <p>El {@code rmax} del constructor dimensiona la grilla; cada query puede
 * usar un {@code rmax} ≤ al del ctor. Esto permite que distintos agentes
 * usen radios distintos sin reconstruir la grilla.</p>
 */
public final class CimNeighborsIndex implements NeighborsIndex {

    private final List<Wall> walls;
    private final double rmaxGlobal;
    private final double xMin, yMin, xMax, yMax;
    private final int mx, my;
    private final double cellSize;
    private final Set<Integer>[][] wallCells;

    private final Map<Integer, AgentState> registered = new LinkedHashMap<>();
    private boolean dirty = true;
    private List<AgentState>[][] agentCells;

    public CimNeighborsIndex(List<Wall> walls, double rmax) {
        this(walls, rmax, null);
    }

    public CimNeighborsIndex(List<Wall> walls, double rmax, Double cellSizeOverride) {
        if (rmax <= 0.0) {
            throw new IllegalArgumentException("rmax must be > 0 (got " + rmax + ")");
        }
        this.walls = List.copyOf(walls);
        this.rmaxGlobal = rmax;

        double xMinTmp = Double.POSITIVE_INFINITY, yMinTmp = Double.POSITIVE_INFINITY;
        double xMaxTmp = Double.NEGATIVE_INFINITY, yMaxTmp = Double.NEGATIVE_INFINITY;
        for (Wall w : walls) {
            xMinTmp = Math.min(xMinTmp, Math.min(w.p1().x(), w.p2().x()));
            yMinTmp = Math.min(yMinTmp, Math.min(w.p1().y(), w.p2().y()));
            xMaxTmp = Math.max(xMaxTmp, Math.max(w.p1().x(), w.p2().x()));
            yMaxTmp = Math.max(yMaxTmp, Math.max(w.p1().y(), w.p2().y()));
        }
        if (walls.isEmpty() || !Double.isFinite(xMinTmp)) {
            xMinTmp = 0.0; yMinTmp = 0.0; xMaxTmp = 1.0; yMaxTmp = 1.0;
        }

        double margin = rmax;
        this.xMin = xMinTmp - margin;
        this.yMin = yMinTmp - margin;
        this.xMax = xMaxTmp + margin;
        this.yMax = yMaxTmp + margin;

        double cs;
        if (cellSizeOverride != null) {
            if (cellSizeOverride <= rmax) {
                throw new IllegalArgumentException(
                        "cellSize (" + cellSizeOverride + ") must be > rmax (" + rmax + ")");
            }
            cs = cellSizeOverride;
        } else {
            cs = rmax * 1.1;
        }
        this.cellSize = cs;

        this.mx = Math.max(1, (int) Math.ceil((xMax - xMin) / cellSize));
        this.my = Math.max(1, (int) Math.ceil((yMax - yMin) / cellSize));

        @SuppressWarnings("unchecked")
        Set<Integer>[][] wc = (Set<Integer>[][]) new Set<?>[my][mx];
        this.wallCells = wc;
        for (int i = 0; i < this.walls.size(); i++) {
            indexWall(this.walls.get(i), i);
        }
    }

    @Override
    public void update(AgentState agent) {
        registered.put(agent.id(), agent);
        dirty = true;
    }

    @Override
    public void remove(int agentId) {
        if (registered.remove(agentId) != null) {
            dirty = true;
        }
    }

    @Override
    public List<Neighbor> neighborsOf(AgentState self, double rmax) {
        if (rmax <= 0.0) {
            throw new IllegalArgumentException("rmax must be > 0 (got " + rmax + ")");
        }
        if (rmax > rmaxGlobal) {
            throw new IllegalArgumentException(
                    "rmax (" + rmax + ") exceeds the rmax (" + rmaxGlobal
                            + ") used to size the grid");
        }
        if (dirty) {
            rebuildAgentGrid();
        }

        int cx = cellX(self.x());
        int cy = cellY(self.y());

        List<Neighbor> neighbors = new ArrayList<>();
        Set<Integer> seenWalls = new HashSet<>();
        Vec2 selfPos = new Vec2(self.x(), self.y());

        for (int dy = -1; dy <= 1; dy++) {
            int ny = cy + dy;
            if (ny < 0 || ny >= my) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cx + dx;
                if (nx < 0 || nx >= mx) continue;

                List<AgentState> agentBucket = agentCells[ny][nx];
                if (agentBucket != null) {
                    for (AgentState other : agentBucket) {
                        if (other.id() == self.id()) continue;
                        double ddx = self.x() - other.x();
                        double ddy = self.y() - other.y();
                        double dist = Math.sqrt(ddx * ddx + ddy * ddy);
                        if (dist <= rmax) {
                            neighbors.add(new Neighbor(other.id(), NeighborType.AGENT, dist, other));
                        }
                    }
                }

                Set<Integer> wallBucket = wallCells[ny][nx];
                if (wallBucket != null) {
                    for (int wallId : wallBucket) {
                        if (!seenWalls.add(wallId)) continue;
                        Wall w = walls.get(wallId);
                        double dist = w.distanceTo(selfPos);
                        if (dist <= rmax) {
                            neighbors.add(new Neighbor(wallId, NeighborType.WALL, dist, null));
                        }
                    }
                }
            }
        }

        neighbors.sort(Comparator
                .comparingDouble(Neighbor::distance)
                .thenComparing(n -> n.type().name())
                .thenComparingInt(Neighbor::id));
        return neighbors;
    }

    private int cellX(double x) {
        int c = (int) Math.floor((x - xMin) / cellSize);
        if (c < 0) return 0;
        if (c >= mx) return mx - 1;
        return c;
    }

    private int cellY(double y) {
        int c = (int) Math.floor((y - yMin) / cellSize);
        if (c < 0) return 0;
        if (c >= my) return my - 1;
        return c;
    }

    private void indexWall(Wall w, int wallId) {
        double dx = w.p2().x() - w.p1().x();
        double dy = w.p2().y() - w.p1().y();
        double length = Math.sqrt(dx * dx + dy * dy);
        double step = cellSize / 4.0;
        int samples = Math.max(2, (int) Math.ceil(length / step) + 1);
        int prevCx = Integer.MIN_VALUE;
        int prevCy = Integer.MIN_VALUE;
        for (int i = 0; i < samples; i++) {
            double t = (double) i / (samples - 1);
            double px = w.p1().x() + t * dx;
            double py = w.p1().y() + t * dy;
            int cx = cellX(px);
            int cy = cellY(py);
            if (cx != prevCx || cy != prevCy) {
                Set<Integer> bucket = wallCells[cy][cx];
                if (bucket == null) {
                    bucket = new HashSet<>();
                    wallCells[cy][cx] = bucket;
                }
                bucket.add(wallId);
                prevCx = cx;
                prevCy = cy;
            }
        }
    }

    private void rebuildAgentGrid() {
        @SuppressWarnings("unchecked")
        List<AgentState>[][] ac = (List<AgentState>[][]) new List<?>[my][mx];
        agentCells = ac;
        for (AgentState a : registered.values()) {
            int cx = cellX(a.x());
            int cy = cellY(a.y());
            List<AgentState> bucket = agentCells[cy][cx];
            if (bucket == null) {
                bucket = new ArrayList<>();
                agentCells[cy][cx] = bucket;
            }
            bucket.add(a);
        }
        dirty = false;
    }

    public double cellSize() { return cellSize; }
    public int mx() { return mx; }
    public int my() { return my; }
}
