package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Rectangle;

/**
 * Row interno de GENERATORS.csv (geometría únicamente). Los params
 * vienen aparte en GENERATOR_PARAMS.csv, unidos por blockName.
 */
public record GeneratorsCsvRow(String blockName, Rectangle area) {
}
