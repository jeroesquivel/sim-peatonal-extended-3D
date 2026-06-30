package ar.edu.itba.simped.core;

/**
 * Sub-componente 5.1.4 del contract v4.
 *
 * <p>Zona de spawn de un generador, con sus parámetros raw.
 * Origen: cada fila de {@code GENERATORS.csv} es un rectángulo,
 * fusionado con la fila correspondiente de {@code GENERATOR_PARAMS.csv}
 * por {@code blockName}.</p>
 *
 * <p>Consumido por G9 (PG, ancla espacial + params para
 * configurar el ConfigurablePedestrianGenerator).</p>
 */
public record GeneratorZone(
        String blockName,
        Rectangle spawnArea,
        double z,
        GeneratorRawParams params) {

    public GeneratorZone {
        if (blockName == null || blockName.isBlank()) {
            throw new IllegalArgumentException("GeneratorZone requires a non-blank blockName");
        }
        if (spawnArea == null) {
            throw new IllegalArgumentException("GeneratorZone requires a non-null spawnArea");
        }
        if (params == null) {
            throw new IllegalArgumentException("GeneratorZone requires non-null params");
        }
    }

    /** Zona de spawn en la planta baja ({@code z = 0}). */
    public GeneratorZone(String blockName, Rectangle spawnArea, GeneratorRawParams params) {
        this(blockName, spawnArea, 0.0, params);
    }
}
