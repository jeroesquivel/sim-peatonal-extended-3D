package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Vec3;

import java.nio.file.Path;
import java.util.List;

/**
 * Reader del nuevo {@code STAIRS.csv} (ver D4 en {@code .claude/DECISIONES.md}).
 *
 * <p>Formato de cada fila — el eje de la escalera del pie al tope:</p>
 * <pre>block_name, x1, y1, z1, x2, y2, z2, width[, speed_factor]</pre>
 * El extremo 1 {@code (x1,y1,z1)} es el pie y el 2 {@code (x2,y2,z2)} el tope
 * (con {@code z1 != z2}). {@code speed_factor} es opcional (default
 * {@link Stairs#DEFAULT_SPEED_FACTOR}).
 *
 * <p>Es una layer <b>opcional</b>: los escenarios de una sola planta no la
 * tienen ({@link #allowEmpty()} y, si el archivo no existe, el loader la
 * saltea).</p>
 */
public final class StairsCsvReader extends CsvReader<Stairs> {

    @Override
    protected Stairs parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 8 && tokens.size() != 9) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 8 or 9 columns (block_name, x1, y1, z1, x2, y2, z2, width[, speed_factor]), got "
                            + tokens.size());
        }
        String blockName = tokens.get(0);
        if (blockName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank block_name");
        }
        double x1 = CsvParse.parseDouble(tokens.get(1), path, lineNumber, layerName());
        double y1 = CsvParse.parseDouble(tokens.get(2), path, lineNumber, layerName());
        double z1 = CsvParse.parseDouble(tokens.get(3), path, lineNumber, layerName());
        double x2 = CsvParse.parseDouble(tokens.get(4), path, lineNumber, layerName());
        double y2 = CsvParse.parseDouble(tokens.get(5), path, lineNumber, layerName());
        double z2 = CsvParse.parseDouble(tokens.get(6), path, lineNumber, layerName());
        double width = CsvParse.parseDouble(tokens.get(7), path, lineNumber, layerName());
        double speedFactor = tokens.size() == 9
                ? CsvParse.parseDouble(tokens.get(8), path, lineNumber, layerName())
                : Stairs.DEFAULT_SPEED_FACTOR;

        try {
            return new Stairs(blockName,
                    new Vec3(x1, y1, z1),
                    new Vec3(x2, y2, z2),
                    width,
                    speedFactor);
        } catch (IllegalArgumentException e) {
            throw unsupportedEntityException(path, lineNumber, e.getMessage());
        }
    }

    @Override
    protected String layerName() {
        return "STAIRS";
    }

    /** STAIRS es opcional: escenarios de una sola planta no la tienen. */
    @Override
    protected boolean allowEmpty() {
        return true;
    }
}
