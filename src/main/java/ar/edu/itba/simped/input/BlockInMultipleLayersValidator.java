package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.input.csv.GeneratorsCsvRow;
import ar.edu.itba.simped.input.csv.ServersCsvRow;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detecta block_names que aparecen en más de una layer (V12).
 *
 * <p>Las layers consideradas: TARGETS (Locations), EXITS,
 * GENERATORS, SERVERS. La unidad de comparación en SERVERS es
 * {@code baseName} (sin sufijo {@code _id_SERVER/QUEUE}).</p>
 */
public final class BlockInMultipleLayersValidator {

    private BlockInMultipleLayersValidator() {
    }

    public static void validate(
            List<Location> locations,
            List<Exit> exits,
            List<GeneratorsCsvRow> generators,
            List<ServersCsvRow> servers,
            ErrorAccumulator acc) {

        Map<String, Set<String>> blockToLayers = new LinkedHashMap<>();
        for (Location l : locations) {
            blockToLayers.computeIfAbsent(l.blockName(), k -> new LinkedHashSet<>()).add("TARGETS");
        }
        for (Exit e : exits) {
            blockToLayers.computeIfAbsent(e.blockName(), k -> new LinkedHashSet<>()).add("EXITS");
        }
        for (GeneratorsCsvRow g : generators) {
            blockToLayers.computeIfAbsent(g.blockName(), k -> new LinkedHashSet<>()).add("GENERATORS");
        }
        for (ServersCsvRow s : servers) {
            blockToLayers.computeIfAbsent(s.base(), k -> new LinkedHashSet<>()).add("SERVERS");
        }

        for (Map.Entry<String, Set<String>> entry : blockToLayers.entrySet()) {
            if (entry.getValue().size() > 1) {
                acc.add(ValidationCode.V12,
                        "block_name=" + entry.getKey(),
                        "block appears in multiple layers: " + entry.getValue());
            }
        }
    }
}
