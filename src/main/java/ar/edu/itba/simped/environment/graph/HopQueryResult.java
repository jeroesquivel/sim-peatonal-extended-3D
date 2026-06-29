package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;

import java.util.List;

/**
 * Resultado detallado de una consulta a {@code nextVisibleHop} (debug / visualización).
 */
public final class HopQueryResult {

    private final Vec2 hop;
    private final boolean targetVisible;
    private final int startNode;
    private final int endNode;
    /** Índices de nodos del grafo en el camino A* (vacío si el target es visible). */
    private final List<Integer> astarPath;
    /**
     * Si el hop cae sobre una arista del camino (FVP por búsqueda binaria),
     * índices en {@link #astarPath} del tramo {@code [segmentFrom, segmentTo]}.
     * Ambos -1 si el hop coincide con un nodo o con el target visible.
     */
    private final int segmentFrom;
    private final int segmentTo;

    public HopQueryResult(Vec2 hop,
                          boolean targetVisible,
                          int startNode,
                          int endNode,
                          List<Integer> astarPath,
                          int segmentFrom,
                          int segmentTo) {
        this.hop = hop;
        this.targetVisible = targetVisible;
        this.startNode = startNode;
        this.endNode = endNode;
        this.astarPath = List.copyOf(astarPath);
        this.segmentFrom = segmentFrom;
        this.segmentTo = segmentTo;
    }

    public Vec2 hop() {
        return hop;
    }

    public boolean targetVisible() {
        return targetVisible;
    }

    public int startNode() {
        return startNode;
    }

    public int endNode() {
        return endNode;
    }

    public List<Integer> astarPath() {
        return astarPath;
    }

    public int segmentFrom() {
        return segmentFrom;
    }

    public int segmentTo() {
        return segmentTo;
    }

    /** Camino A* como lista de ids separados por ';' (para CSV). */
    public String astarPathCsv() {
        if (astarPath.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < astarPath.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(astarPath.get(i));
        }
        return sb.toString();
    }
}
