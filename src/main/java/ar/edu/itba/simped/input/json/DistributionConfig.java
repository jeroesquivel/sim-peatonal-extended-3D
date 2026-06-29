package ar.edu.itba.simped.input.json;

public record DistributionConfig(
        String type,
        Double min,
        Double max,
        Double mean,
        Double std) {
}
