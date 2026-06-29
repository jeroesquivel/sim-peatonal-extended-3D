package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.ServerType;
import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.core.validation.ValidationError;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;

public final class ServerParamsCsvReader extends CsvReader<ServerParamsRow> {

    @Override
    protected ServerParamsRow parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 5) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 5 columns (block_name, type, server_time_param, green_duration, t_init), got "
                            + tokens.size());
        }
        String blockName = tokens.get(0);
        if (blockName.isBlank()) {
            throw unsupportedEntityException(path, lineNumber, "blank block_name");
        }
        ServerType type = parseType(tokens.get(1), path, lineNumber);
        double serverTimeParam = CsvParse.parseDouble(tokens.get(2), path, lineNumber, layerName());
        // green_duration solo lo usa semaphore (duración del verde); vacío en queue/classroom.
        OptionalDouble greenDuration = tokens.get(3).isBlank()
                ? OptionalDouble.empty()
                : OptionalDouble.of(CsvParse.parseDouble(tokens.get(3), path, lineNumber, layerName()));
        // t_init: offset en semaphore, inicio de sesión en classroom (una fila por sesión),
        // vacío en queue.
        OptionalDouble tInit = tokens.get(4).isBlank()
                ? OptionalDouble.empty()
                : OptionalDouble.of(CsvParse.parseDouble(tokens.get(4), path, lineNumber, layerName()));
        return new ServerParamsRow(blockName, type, serverTimeParam, greenDuration, tInit);
    }

    @Override
    protected String layerName() {
        return "SERVER_PARAMS";
    }

    private ServerType parseType(String raw, Path path, int lineNumber) {
        return switch (raw.toLowerCase()) {
            case "queue" -> ServerType.QUEUE;
            case "semaphore" -> ServerType.SEMAPHORE;
            case "classroom" -> ServerType.CLASSROOM;
            default -> throw new ScenarioValidationException(List.of(
                    new ValidationError(ValidationCode.V15,
                            path.getFileName() + ":" + lineNumber,
                            "type '" + raw + "' invalid; valid: queue, semaphore, classroom")));
        };
    }
}
