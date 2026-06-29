package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.core.validation.ValidationError;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class GeneratorParamsCsvReader extends CsvReader<GeneratorParamsRow> {

    private static final Set<String> VALID_MODES = Set.of("flowrate", "instant_occupation");

    @Override
    protected GeneratorParamsRow parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 5) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 5 columns (block_name, mode, rate_or_count, agent_type, plan_template), got "
                            + tokens.size());
        }
        String blockName = tokens.get(0);
        if (blockName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank block_name");
        }
        String mode = tokens.get(1);
        if (!VALID_MODES.contains(mode)) {
            throw new ScenarioValidationException(List.of(
                    new ValidationError(ValidationCode.V14,
                            path.getFileName() + ":" + lineNumber,
                            "mode '" + mode + "' invalid; valid: " + VALID_MODES)));
        }
        double rateOrCount = CsvParse.parseDouble(tokens.get(2), path, lineNumber, layerName());
        String agentType = tokens.get(3);
        String planTemplate = tokens.get(4);
        return new GeneratorParamsRow(blockName, mode, rateOrCount, agentType, planTemplate);
    }

    @Override
    protected String layerName() {
        return "GENERATOR_PARAMS";
    }
}
