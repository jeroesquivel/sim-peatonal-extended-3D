package ar.edu.itba.simped.environment.generator;

public enum GenerationMode {
    /** Un agente por vez, de forma ordenada. */
    CALM,
    /** Todos los agentes del ciclo de una sola vez al inicio de cada período activo. */
    BATCH
}
