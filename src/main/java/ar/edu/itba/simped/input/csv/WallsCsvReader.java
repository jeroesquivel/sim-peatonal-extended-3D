package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Wall;

import java.nio.file.Path;
import java.util.List;

public final class WallsCsvReader extends CsvReader<Wall> {

    @Override
    protected Wall parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 6) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 6 columns (x1, y1, z1, x2, y2, z2), got " + tokens.size());
        }
        double x1 = CsvParse.parseDouble(tokens.get(0), path, lineNumber, layerName());
        double y1 = CsvParse.parseDouble(tokens.get(1), path, lineNumber, layerName());
        double x2 = CsvParse.parseDouble(tokens.get(3), path, lineNumber, layerName());
        double y2 = CsvParse.parseDouble(tokens.get(4), path, lineNumber, layerName());
        double z = CsvParse.parseFloorZ(tokens.get(2), tokens.get(5), path, lineNumber, layerName());
        return new Wall(new Vec2(x1, y1), new Vec2(x2, y2), z);
    }

    @Override
    protected String layerName() {
        return "WALLS";
    }
}
