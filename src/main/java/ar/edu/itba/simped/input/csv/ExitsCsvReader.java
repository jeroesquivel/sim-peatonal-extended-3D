package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.Vec2;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;

public final class ExitsCsvReader extends CsvReader<Exit> {

    @Override
    protected Exit parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 7) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 7 columns (block_name, x1, y1, z1, x2, y2, z2), got " + tokens.size());
        }
        String blockName = tokens.get(0);
        if (blockName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank block_name");
        }
        double x1 = CsvParse.parseDouble(tokens.get(1), path, lineNumber, layerName());
        double y1 = CsvParse.parseDouble(tokens.get(2), path, lineNumber, layerName());
        double x2 = CsvParse.parseDouble(tokens.get(4), path, lineNumber, layerName());
        double y2 = CsvParse.parseDouble(tokens.get(5), path, lineNumber, layerName());
        double z = CsvParse.parseFloorZ(tokens.get(3), tokens.get(6), path, lineNumber, layerName());
        return new Exit(blockName,
                new Segment(new Vec2(x1, y1), new Vec2(x2, y2)),
                z,
                OptionalDouble.empty());
    }

    @Override
    protected String layerName() {
        return "EXITS";
    }
}
