package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;

import java.util.*;

/**
 * A* sobre un grafo de navegación con heurística de distancia euclidiana.
 */
public final class AStarPathfinder {

    private AStarPathfinder() {
    }

    /**
     * Encuentra el camino de costo mínimo entre {@code start} y {@code end}.
     *
     * @return lista ordenada de índices de nodos [start, ..., end], o {@code null} si no hay camino.
     */
    public static List<Integer> findPath(int start, int end,
                                         List<Vec2> nodes,
                                         List<Map<Integer, Double>> adjacency) {
        if (start == end) return List.of(start);

        PriorityQueue<long[]> open = new PriorityQueue<>(
            Comparator.comparingLong(a -> a[0])
        );
        Map<Integer, Double> gScore = new HashMap<>();
        Map<Integer, Integer> cameFrom = new HashMap<>();
        Set<Integer> closed = new HashSet<>();

        gScore.put(start, 0.0);
        open.add(encode(0.0, start));

        Vec2 goal = nodes.get(end);

        while (!open.isEmpty()) {
            long[] entry = open.poll();
            int current = decodeIndex(entry);

            if (!closed.add(current)) continue;
            if (current == end) return reconstructPath(cameFrom, end);

            double currentG = gScore.getOrDefault(current, Double.MAX_VALUE);

            Map<Integer, Double> neighbors = adjacency.get(current);
            if (neighbors == null) continue;

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
        }
        return null;
    }

    /**
     * Codifica (fCost, nodeIndex) en un long[] para la priority queue.
     * Se usa long con los bits del double para orden correcto.
     */
    private static long[] encode(double fCost, int index) {
        return new long[]{ Double.doubleToLongBits(fCost), index };
    }

    private static int decodeIndex(long[] entry) {
        return (int) entry[1];
    }

    private static List<Integer> reconstructPath(Map<Integer, Integer> cameFrom, int end) {
        List<Integer> path = new ArrayList<>();
        int current = end;
        while (cameFrom.containsKey(current)) {
            path.add(current);
            current = cameFrom.get(current);
        }
        path.add(current);
        Collections.reverse(path);
        return path;
    }
}
