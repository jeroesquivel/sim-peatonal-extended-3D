package ar.edu.itba.simped.input.json;

public record ServerJson(
        String blockName,
        String type,
        DistributionConfig attendingTimeDistribution,
        int maxCapacity,
        Double startTime,
        Double inactiveTime,
        Double greenDuration) {
}
