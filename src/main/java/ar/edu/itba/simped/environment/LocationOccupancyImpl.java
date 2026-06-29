package ar.edu.itba.simped.environment;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.LocationOccupancy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementación trivial del {@link LocationOccupancy}: capacidad 1 por
 * punto, mapa en memoria. El SimulationLoop es single-threaded, por lo que
 * no hace falta sincronización.
 */
public final class LocationOccupancyImpl implements LocationOccupancy {

    private final Map<Vec2, Integer> occupiedBy = new LinkedHashMap<>();

    @Override
    public boolean tryOccupy(Vec2 location, int agentId) {
        Integer current = occupiedBy.get(location);
        if (current == null) {
            occupiedBy.put(location, agentId);
            return true;
        }
        return current == agentId;
    }

    @Override
    public void release(Vec2 location, int agentId) {
        Integer current = occupiedBy.get(location);
        if (current != null && current == agentId) {
            occupiedBy.remove(location);
        }
    }
}
