package ar.edu.itba.simped.core;

/**
 * Cómo elige un agente, entre los blocks que matchean un paso de plan, a
 * cuál(es) ir. Resuelto por-agente al construir su plan (la posición del
 * agente importa para {@link #CLOSEST}).
 */
public enum ObjectiveSelection {
    /** El/los más cercano(s) a la posición actual del agente. */
    CLOSEST,
    /** Elección aleatoria (sin reemplazo). */
    RANDOM,
    /** Todos los matches, en el orden del escenario. */
    ALL;

    /**
     * Mapea el string del escenario ({@code objective_selection} /
     * {@code exit_selection}) al enum. {@code null} o desconocido → {@code def}.
     */
    public static ObjectiveSelection fromString(String raw, ObjectiveSelection def) {
        if (raw == null) {
            return def;
        }
        return switch (raw.trim().toUpperCase()) {
            case "CLOSEST" -> CLOSEST;
            case "RANDOM" -> RANDOM;
            case "ALL" -> ALL;
            default -> def;
        };
    }
}
