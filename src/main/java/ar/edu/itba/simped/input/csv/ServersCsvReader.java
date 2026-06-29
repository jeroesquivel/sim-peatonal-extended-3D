package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.Vec2;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class ServersCsvReader extends CsvReader<ServersCsvRow> {

    @Override
    protected ServersCsvRow parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 7) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 7 columns (block_name, x1, y1, z1, x2, y2, z2), got " + tokens.size());
        }
        String fullName = tokens.get(0);
        if (fullName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank block_name");
        }
        Optional<ParsedServerName> parsed = ServersBlockNameParser.parse(fullName);
        if (parsed.isEmpty()) {
            throw unsupportedEntityException(path, lineNumber,
                    "block_name '" + fullName + "' does not match SERVERS suffix pattern "
                            + "(expected <base>_<id>_SERVER or <base>_<id>_QUEUE<nnn>)");
        }
        ParsedServerName p = parsed.get();
        double x1 = CsvParse.parseDouble(tokens.get(1), path, lineNumber, layerName());
        double y1 = CsvParse.parseDouble(tokens.get(2), path, lineNumber, layerName());
        double x2 = CsvParse.parseDouble(tokens.get(4), path, lineNumber, layerName());
        double y2 = CsvParse.parseDouble(tokens.get(5), path, lineNumber, layerName());

        return switch (p.kind()) {
            case SERVER -> new ServersCsvRow.ServerRow(p.base(), p.id(),
                    new Rectangle(new Vec2(x1, y1), new Vec2(x2, y2)));
            case QUEUE -> new ServersCsvRow.QueueRow(p.base(), p.id(),
                    p.queueIndex().orElseThrow(),
                    new Segment(new Vec2(x1, y1), new Vec2(x2, y2)));
        };
    }

    @Override
    protected String layerName() {
        return "SERVERS";
    }
}
