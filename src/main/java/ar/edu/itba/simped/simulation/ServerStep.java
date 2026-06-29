package ar.edu.itba.simped.simulation;

import ar.edu.itba.simped.core.Vec2;

import java.util.Map;

/**
 * Hook que el {@link SimulationDriverImpl} invoca una vez por timestep para
 * avanzar el sub-módulo de Servers (5.5). Se mantiene como interfaz para que
 * el driver no dependa del {@code ServersModule} concreto de G0.
 *
 * <p>El driver pasa el reloj actual, el {@code dt} y las posiciones de todos
 * los agentes vivos (agentId → posición); el módulo decide servicios y
 * releases y empuja I13b/I13c por sus sinks.</p>
 */
@FunctionalInterface
public interface ServerStep {

    void step(double now, double dt, Map<Integer, Vec2> positions);
}
