package ar.edu.itba.simped.input.json;

import java.util.List;

public record ParametersJson(
        double maxTime,
        Double evacuateAt,
        String blueprintName,
        double outputDeltaTime,
        List<GeneratorJson> agentsGenerators,
        List<TargetJson> targets,
        List<ServerJson> servers,
        List<PlanJson> plans) {
}
