package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.LoadedScenario;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Wall;
import ar.edu.itba.simped.core.ports.ScenarioLoader;
import ar.edu.itba.simped.environment.geometry.GeometryImpl;
import ar.edu.itba.simped.input.csv.ExitsCsvReader;
import ar.edu.itba.simped.input.csv.GeneratorsCsvReader;
import ar.edu.itba.simped.input.csv.GeneratorsCsvRow;
import ar.edu.itba.simped.input.csv.ServersCsvReader;
import ar.edu.itba.simped.input.csv.ServersCsvRow;
import ar.edu.itba.simped.input.csv.StairsCsvReader;
import ar.edu.itba.simped.input.csv.TargetsCsvReader;
import ar.edu.itba.simped.input.csv.WallsCsvReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestador del pipeline de carga de escenario (G3).
 *
 * <p>Soporta dos formatos de directorio:
 * <ul>
 *   <li><b>Formato A</b> (monorepo canónico): 9 CSVs (5 geometría + 4
 *       de parámetros). Ver {@code scenarios/example/}.</li>
 *   <li><b>Formato B</b> (JSON heredado): 5 CSVs de geometría +
 *       {@code parameters.json}.</li>
 * </ul>
 *
 * <p>{@link FormatDetector} elige el formato según los archivos
 * presentes. Si ambos coexisten dispara {@code V20}.</p>
 *
 * <p>Acumula errores con {@link ErrorAccumulator}; al final tira
 * {@link ar.edu.itba.simped.core.validation.ScenarioValidationException}
 * agregada si hubo alguno.</p>
 */
public final class ScenarioLoaderImpl implements ScenarioLoader {

    @Override
    public LoadedScenario load(Path scenarioDir) {
        ErrorAccumulator acc = new ErrorAccumulator();

        FormatDetector.Format format = FormatDetector.detect(scenarioDir, acc);

        List<Wall> walls = new WallsCsvReader().read(scenarioDir.resolve("WALLS.csv"), acc);
        List<Location> locations = new TargetsCsvReader().read(scenarioDir.resolve("TARGETS.csv"), acc);
        List<Exit> exits = new ExitsCsvReader().read(scenarioDir.resolve("EXITS.csv"), acc);
        List<GeneratorsCsvRow> generatorRows = new GeneratorsCsvReader()
                .read(scenarioDir.resolve("GENERATORS.csv"), acc);
        List<ServersCsvRow> serverRows = new ServersCsvReader()
                .read(scenarioDir.resolve("SERVERS.csv"), acc);
        // STAIRS.csv es opcional: los escenarios de una sola planta no lo traen.
        Path stairsPath = scenarioDir.resolve("STAIRS.csv");
        List<Stairs> stairs = Files.exists(stairsPath)
                ? new StairsCsvReader().read(stairsPath, acc)
                : List.of();

        RawParams params;
        Map<String, Distribution> dwellsByBlock;
        Optional<ServerTypeStrategy> serverTypeStrategy;

        if (format == FormatDetector.Format.FORMAT_A) {
            params = FormatALoader.load(scenarioDir, acc);
            dwellsByBlock = Map.of();
            serverTypeStrategy = Optional.empty();
        } else {
            FormatBLoader.TargetDwellsAccumulator dwellsAcc = new FormatBLoader.TargetDwellsAccumulator();
            params = FormatBLoader.load(scenarioDir, acc, dwellsAcc);
            dwellsByBlock = dwellsAcc.asMap();
            serverTypeStrategy = Optional.of(new CsvInferredServerTypeStrategy());
        }

        BlockInMultipleLayersValidator.validate(locations, exits, generatorRows, serverRows, acc);

        GeometryImpl geometry = GeometryAssembler.assemble(
                walls, locations, exits, stairs, generatorRows, serverRows,
                params, dwellsByBlock, serverTypeStrategy, acc);

        Map<String, PlanTemplate> templates = PlanTemplatesBuilder.build(
                params.planTemplatesByName(), geometry, acc);

        Joiners.validate(params, generatorRows, serverRows, format, acc);
        MaxRadiusVsSlotSpacingValidator.validate(params.generatorParamsByBlock(), acc);

        acc.throwIfAny();

        return new LoadedScenario(geometry, params.simParams(), templates, params.legacy());
    }
}
