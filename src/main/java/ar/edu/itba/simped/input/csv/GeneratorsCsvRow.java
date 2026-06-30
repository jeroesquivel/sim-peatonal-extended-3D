package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Rectangle;

/**
 * Row interno de GENERATORS.csv (geometría únicamente). Los params
 * vienen aparte en GENERATOR_PARAMS.csv, unidos por blockName.
 *
 * @param z planta a la que pertenece la zona de spawn (0 = planta baja).
 */
public record GeneratorsCsvRow(String blockName, Rectangle area, double z) {

    public GeneratorsCsvRow(String blockName, Rectangle area) {
        this(blockName, area, 0.0);
    }
}
