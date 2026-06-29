package ar.edu.itba.simped.environment.generator;

public enum GeneratorPhase {
    /** Generando agentes. */
    ACTIVE,
    /** Período de apagado entre ciclos (inactiveTime). */
    INACTIVE,
    /** Pausado manualmente via {@code pause()}. */
    MANUALLY_PAUSED,
    /** Terminó (sin más ciclos). */
    DONE
}
