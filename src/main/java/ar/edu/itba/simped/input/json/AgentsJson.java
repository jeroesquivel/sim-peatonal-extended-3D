package ar.edu.itba.simped.input.json;

public record AgentsJson(
        DistributionConfig minRadiusDistribution,
        DistributionConfig maxRadiusDistribution,
        double maxVelocity) {
}
