package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Vec2;

import java.nio.file.Path;
import java.util.List;

public final class GeneratorsCsvReader extends CsvReader<GeneratorsCsvRow> {

    @Override
    protected GeneratorsCsvRow parseRow(List<String> tokens, int lineNumber, Path path) {
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
        return new GeneratorsCsvRow(blockName,
                new Rectangle(new Vec2(x1, y1), new Vec2(x2, y2)));
    }

    @Override
    protected String layerName() {
        return "GENERATORS";
    }
}
