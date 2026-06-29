package ar.edu.itba.simped.input.json;

public record GeneratorJson(
        String blockName,
        String plan,
        String mode,
        AgentsJson agents,
        double activeTime,
        double inactiveTime,
        GenerationJson generation) {
}
