package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.TaskType;
import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.core.validation.ValidationError;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PlansCsvReader extends CsvReader<PlanStepRow> {

    @Override
    protected PlanStepRow parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 4) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 4 columns (template_name, step_order, target_type, target_block_name), got "
                            + tokens.size());
        }
        String templateName = tokens.get(0);
        if (templateName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank template_name");
        }
        int stepOrder = CsvParse.parseInt(tokens.get(1), path, lineNumber, layerName());
        TaskType type = parseTargetType(tokens.get(2), path, lineNumber);
        String targetBlockName = tokens.get(3);
        if (targetBlockName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank target_block_name");
        }
        return new PlanStepRow(templateName, stepOrder, type, targetBlockName);
    }

    @Override
    protected String layerName() {
        return "PLANS";
    }

    private TaskType parseTargetType(String raw, Path path, int lineNumber) {
        return switch (raw) {
            case "TARGET" -> TaskType.LOCATION;
            case "SERVER" -> TaskType.SERVER;
            case "EXIT" -> TaskType.EXIT;
            default -> throw new ScenarioValidationException(List.of(
                    new ValidationError(ValidationCode.V16,
                            path.getFileName() + ":" + lineNumber,
                            "target_type '" + raw + "' invalid; valid: TARGET, SERVER, EXIT")));
        };
    }

    /**
     * Agrupa por {@code templateName} y ordena por {@code stepOrder}.
     * Preserva el orden de aparición de las templates (LinkedHashMap).
     */
    public Map<String, List<PlanStepRow>> groupByTemplate(List<PlanStepRow> rows) {
        Map<String, List<PlanStepRow>> grouped = rows.stream().collect(Collectors.groupingBy(
                PlanStepRow::templateName,
                LinkedHashMap::new,
                Collectors.toList()));
        grouped.replaceAll((k, v) -> v.stream()
                .sorted(Comparator.comparingInt(PlanStepRow::stepOrder))
                .toList());
        return grouped;
    }
}
