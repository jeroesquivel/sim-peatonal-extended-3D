package ar.edu.itba.simped.input.csv;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsea los nombres de block del CSV SERVERS, que siguen el formato
 * {@code <BASENAME>_<id>_SERVER} o {@code <BASENAME>_<id>_QUEUE<nnn>}
 * (con nnn zero-padded a 3 dígitos).
 *
 * <p>Regex anclada al sufijo, {@code <base>} admite cualquier carácter
 * incluyendo {@code _} (e.g. "VENDING MACHINE_1_SERVER").</p>
 */
public final class ServersBlockNameParser {

    private static final Pattern PATTERN =
            Pattern.compile("^(?<base>.+)_(?<id>\\d+)_(?<kind>SERVER|QUEUE(?<qidx>\\d{3}))$");

    private ServersBlockNameParser() {
    }

    public static Optional<ParsedServerName> parse(String blockName) {
        if (blockName == null) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(blockName);
        if (!m.matches()) {
            return Optional.empty();
        }
        String base = m.group("base");
        int id = Integer.parseInt(m.group("id"));
        String kindStr = m.group("kind");
        if (kindStr.equals("SERVER")) {
            return Optional.of(new ParsedServerName(base, id, ServerKind.SERVER, OptionalInt.empty()));
        }
        int qidx = Integer.parseInt(m.group("qidx"));
        return Optional.of(new ParsedServerName(base, id, ServerKind.QUEUE, OptionalInt.of(qidx)));
    }
}
