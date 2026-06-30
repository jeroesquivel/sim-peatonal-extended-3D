package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.ServerParams;
import ar.edu.itba.simped.core.ServerType;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Wall;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.environment.geometry.GeometryImpl;
import ar.edu.itba.simped.input.csv.GeneratorsCsvRow;
import ar.edu.itba.simped.input.csv.ServersCsvRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Arma {@link GeometryImpl} a partir de los rows raw de los 5 CSV de
 * geometría más los params resueltos.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Enriquecer Locations con {@code dwellTime} cuando
 *       {@code dwellsByBlock} lo provee (Formato B).</li>
 *   <li>Agrupar {@link ServersCsvRow} por {@code (base, id)} y armar
 *       {@link ServerZone}. Si hay {@link ServerTypeStrategy} (Formato
 *       B), se usa para inferir el type; si no, se respeta el type del
 *       spec (Formato A).</li>
 *   <li>Construir {@link GeneratorZone} uniendo el rectángulo de
 *       {@code GENERATORS.csv} con los params. CSV-side blocks sin
 *       counterpart en params se saltean (el joiner V4 detecta el caso
 *       opuesto).</li>
 * </ul>
 */
public final class GeometryAssembler {

    private GeometryAssembler() {
    }

    public static GeometryImpl assemble(
            List<Wall> walls,
            List<Location> locationsRaw,
            List<Exit> exits,
            List<Stairs> stairs,
            List<GeneratorsCsvRow> generatorRows,
            List<ServersCsvRow> serverRows,
            RawParams params,
            Map<String, Distribution> dwellsByBlock,
            Optional<ServerTypeStrategy> serverTypeStrategy,
            ErrorAccumulator acc) {

        List<Location> locations = enrichLocations(locationsRaw, dwellsByBlock);
        List<GeneratorZone> generatorZones = buildGenerators(generatorRows, params, acc);
        List<ServerZone> serverZones = buildServers(serverRows, params, serverTypeStrategy, acc);
        return new GeometryImpl(walls, locations, exits, generatorZones, serverZones, stairs);
    }

    private static List<Location> enrichLocations(List<Location> raw, Map<String, Distribution> dwellsByBlock) {
        if (dwellsByBlock == null || dwellsByBlock.isEmpty()) {
            return raw;
        }
        List<Location> out = new ArrayList<>(raw.size());
        for (Location l : raw) {
            Distribution dwell = dwellsByBlock.get(l.blockName());
            out.add(dwell == null ? l : new Location(l.blockName(), l.shape(), l.z(), Optional.of(dwell)));
        }
        return out;
    }

    private static List<GeneratorZone> buildGenerators(
            List<GeneratorsCsvRow> rows, RawParams params, ErrorAccumulator acc) {
        List<GeneratorZone> out = new ArrayList<>(rows.size());
        for (GeneratorsCsvRow row : rows) {
            GeneratorRawParams gParams = params.generatorParamsByBlock().get(row.blockName());
            if (gParams == null) {
                continue;
            }
            try {
                out.add(new GeneratorZone(row.blockName(), row.area(), gParams));
            } catch (IllegalArgumentException e) {
                acc.add(ValidationCode.V11,
                        "GeneratorZone[" + row.blockName() + "]",
                        e.getMessage());
            }
        }
        return out;
    }

    private static List<ServerZone> buildServers(
            List<ServersCsvRow> rows,
            RawParams params,
            Optional<ServerTypeStrategy> strategy,
            ErrorAccumulator acc) {

        Map<String, List<ServersCsvRow>> grouped = new LinkedHashMap<>();
        for (ServersCsvRow row : rows) {
            String key = row.base() + "#" + row.id();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<ServerZone> out = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<ServersCsvRow>> entry : grouped.entrySet()) {
            List<ServersCsvRow> group = entry.getValue();
            ServersCsvRow.ServerRow serverRow = group.stream()
                    .filter(r -> r instanceof ServersCsvRow.ServerRow)
                    .map(r -> (ServersCsvRow.ServerRow) r)
                    .findFirst()
                    .orElse(null);

            if (serverRow == null) {
                acc.add(ValidationCode.V2,
                        "SERVERS.csv[" + entry.getKey() + "]",
                        "server group has QUEUE entries but no SERVER (rectangle) entry");
                continue;
            }

            List<Segment> queues = group.stream()
                    .filter(r -> r instanceof ServersCsvRow.QueueRow)
                    .map(r -> ((ServersCsvRow.QueueRow) r).segment())
                    .toList();

            ServerSpec spec = params.serverSpecsByBase().get(serverRow.base());
            ServerType type;
            ServerParams sParams;
            if (spec == null) {
                // CSV server sin counterpart en params: usar strategy (Formato B)
                // o defaults (Formato A sin SERVER_PARAMS).
                type = strategy
                        .map(s -> s.resolve(serverRow.base(), !queues.isEmpty()))
                        .orElse(ServerType.CLASSROOM);
                sParams = ServerParams.empty();
            } else if (spec.explicitType()) {
                // Type declarado por el escenario (Formato A, o Formato B con
                // campo 'type'): se respeta, NO se infiere.
                sParams = spec.params();
                type = spec.type();
            } else {
                // Placeholder de Formato B sin 'type': inferir por queues.
                sParams = spec.params();
                type = strategy
                        .map(s -> s.resolve(serverRow.base(), !queues.isEmpty()))
                        .orElse(spec.type());
            }

            try {
                out.add(new ServerZone(serverRow.base(), serverRow.id(),
                        serverRow.area(), queues, type, sParams));
            } catch (IllegalArgumentException e) {
                acc.add(ValidationCode.V11,
                        "ServerZone[" + serverRow.base() + "#" + serverRow.id() + "]",
                        e.getMessage());
            }
        }
        return out;
    }
}
