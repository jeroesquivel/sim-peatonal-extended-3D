package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.Vec2;

import java.util.List;

/**
 * Sub-módulo 4.6 del contract v4 (Operational Model).
 *
 * <p>Modelo de movimiento de bajo nivel. Integra fuerzas cada dt y escribe
 * los kinemáticos actualizados de vuelta en {@link AgentState}.</p>
 *
 * <p>Lee {@link AgentProfile} de {@code state.profile()} (propiedad del agente,
 * no del comportamiento) y lo combina con el {@link BehaviorState} que provee
 * la SM para derivar los parámetros efectivos de ese {@code dt}. Esa combinación
 * es responsabilidad del OM, no de la SM.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I15a: recibe foot-target {@code (xvt, yvt)} desde {@link PreOM}.</li>
 *   <li>I12:  recibe {@link BehaviorState} desde {@link StateMachine}.</li>
 *   <li>I16q: recibe lista de vecinos desde {@link NeighborsIndex}.</li>
 *   <li>I15:  escribe {@code x, y, vx, vy} en {@link AgentState}.</li>
 * </ul>
 * </p>
 */
public interface OperationalModel {

    /**
     * Radio con el que el contenedor de Agent debe consultar vecinos antes de
     * llamar a {@link #integrate}. Por defecto usa el rmax base del profile;
     * los modelos que escalan rmax por comportamiento pueden sobrescribirlo.
     */
    default double neighborQueryRadius(AgentState state, BehaviorState behavior) {
        return state.profile().rmax();
    }

    /**
     * dt máximo que el modelo considera numéricamente estable para {@code profile}.
     * El loop usa {@code min(dt del escenario, recommendedDt)}: el OM acota el paso,
     * no el escenario. Por defecto no impone cota ({@code +∞}); los modelos con
     * restricción de estabilidad (p. ej. CPM, por su radio de contacto) la sobrescriben.
     */
    default double recommendedDt(AgentProfile profile) {
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Integra el paso de tiempo {@code dt} y muta el {@link AgentState} con
     * los nuevos valores de posición y velocidad.
     */
    void integrate(
            AgentState state,
            Vec2 footTarget,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            double dt
    );
}
