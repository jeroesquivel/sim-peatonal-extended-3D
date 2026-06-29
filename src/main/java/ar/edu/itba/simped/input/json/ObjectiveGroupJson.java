package ar.edu.itba.simped.input.json;

public record ObjectiveGroupJson(
        String blockName,
        String layer,
        String objectiveSelection,
        DistributionConfig quantityDistribution) {
}
