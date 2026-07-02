package ar.edu.itba.simped.core;

import java.util.Random;

/**
 * Semilla reproducible OPT-IN para las fuentes de aleatoriedad del simulador.
 *
 * <p>Por defecto (sin configuración) el comportamiento es exactamente el de
 * hoy: cada {@link Random} se crea sin sembrar, así que dos corridas del mismo
 * escenario pueden diferir. Si se setea la propiedad de sistema
 * {@code simped.seed} (p. ej. {@code -Dsimped.seed=42}) con un valor parseable
 * a {@code long}, todos los {@link Random} construidos vía {@link #rng(String)}
 * quedan sembrados de forma determinística y dos corridas del mismo escenario
 * producen el mismo output.</p>
 *
 * <p>El parámetro {@code salt} distingue streams entre instancias distintas
 * (p. ej. dos generadores de peatones, o el grafo de navegación): cada salt
 * combina con la semilla base vía XOR de su {@code hashCode()}, de modo que
 * instancias con salts distintos no comparten la misma secuencia aunque usen
 * la misma semilla base.</p>
 */
public final class Seeds {

    private Seeds() {
    }

    /**
     * Crea un {@link Random}. Si la propiedad de sistema {@code simped.seed}
     * está seteada y parsea a {@code long}, el resultado es determinístico
     * (sembrado con {@code seed ^ salt.hashCode()}). Si no está seteada, es
     * blanca, o no parsea, se devuelve un {@link Random} sin sembrar (default
     * de hoy, sin cambios de comportamiento).
     *
     * @param salt identificador que diferencia el stream de esta instancia.
     * @return un {@link Random}, sembrado o no según {@code simped.seed}.
     */
    public static Random rng(String salt) {
        String prop = System.getProperty("simped.seed");
        if (prop != null && !prop.isBlank()) {
            try {
                long seed = Long.parseLong(prop.trim());
                return new Random(seed ^ (long) salt.hashCode());
            } catch (NumberFormatException ignored) {
                // No parseable: cae al comportamiento default (sin sembrar).
            }
        }
        return new Random();
    }
}
