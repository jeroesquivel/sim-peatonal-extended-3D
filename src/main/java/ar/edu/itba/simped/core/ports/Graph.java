package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.Vec3;

/**
 * Sub-módulo 5.2 del contract v4. Navigation mesh para path planning.
 * Construido una vez en init desde {@link Geometry}; nunca modificado en
 * runtime.
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I17: init desde Geometry (mesh).</li>
 *   <li>I14: consulta de {@link PreOM} (hop visible hacia el target).</li>
 * </ul>
 * </p>
 */
public interface Graph {

    /**
     * I14: dado el agente en {@code agentPosition} y su target final
     * {@code target}, devuelve el próximo hop visible {@code (xvt, yvt)}.
     * <p>
     * Si {@code target} es visible desde el agente, retorna {@code target}.
     * Si no, calcula el camino óptimo con A* sobre el grafo y devuelve el
     * punto visible más lejano al agente sobre ese camino (Furthest Visible Point).
     * </p>
     */
    Vec3 nextVisibleHop(Vec3 agentPosition, Vec3 target);
}
