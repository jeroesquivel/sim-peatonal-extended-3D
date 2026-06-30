package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Circle;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Shape;
import ar.edu.itba.simped.core.Vec2;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class TargetsCsvReader extends CsvReader<Location> {

    @Override
    protected Location parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 9) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 9 columns (block_name, figure_type, radius, x1, y1, z1, x2, y2, z2), got "
                            + tokens.size());
        }
        String blockName = tokens.get(0);
        if (blockName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank block_name");
        }
        String figureType = tokens.get(1);
        double radius = CsvParse.parseDouble(tokens.get(2), path, lineNumber, layerName());
        double x1 = CsvParse.parseDouble(tokens.get(3), path, lineNumber, layerName());
        double y1 = CsvParse.parseDouble(tokens.get(4), path, lineNumber, layerName());
        double x2 = CsvParse.parseDouble(tokens.get(6), path, lineNumber, layerName());
        double y2 = CsvParse.parseDouble(tokens.get(7), path, lineNumber, layerName());
        double z = CsvParse.parseFloorZ(tokens.get(5), tokens.get(8), path, lineNumber, layerName());

        Shape shape = switch (figureType) {
            case "CIRCLE" -> new Circle(new Vec2(x1, y1), radius);
            case "RECTANGLE" -> new Rectangle(new Vec2(x1, y1), new Vec2(x2, y2));
            default -> throw unsupportedEntityException(path, lineNumber,
                    "unknown figure_type '" + figureType + "', expected CIRCLE or RECTANGLE");
        };
        return new Location(blockName, shape, z, Optional.empty());
    }

    @Override
    protected String layerName() {
        return "TARGETS";
    }

    /**
     * TARGETS es opcional: hay escenarios de tránsito puro (p.ej. subte,
     * vía pública) donde los agentes van del generador al server/salida sin
     * waypoints intermedios. Una layer TARGETS vacía no es error.
     */
    @Override
    protected boolean allowEmpty() {
        return true;
    }
}
