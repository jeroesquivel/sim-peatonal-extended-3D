package ar.edu.itba.simped.core;

/**
 * Perfil físico del agente. Propiedad del individuo (no del comportamiento ni
 * del modelo de movimiento): cada agente trae su propio profile al nacer y no
 * cambia durante la simulación.
 *
 * <p>El OM lo lee del {@link AgentState} (vía {@code state.profile()}) y lo
 * combina con el {@code BehaviorState} para obtener los parámetros efectivos
 * de ese {@code dt}. La SM no tiene nada que decir sobre estos valores.</p>
 *
 * <p>Incluye campos que algunos modelos no usan (ej. {@code beta} y {@code ve}
 * solo aplican al CPM); los OMs ignoran lo que no necesitan.</p>
 */
public record AgentProfile(
        double vd,
        double tau,
        double rmin,
        double rmax,
        double beta,
        double ve
) {
}
