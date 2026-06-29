package ar.edu.itba.simped.core;

import java.util.random.RandomGenerator;

public record Gaussian(double mean, double std) implements Distribution {

    public Gaussian {
        if (std <= 0) {
            throw new IllegalArgumentException(
                    "Gaussian distribution requires std > 0, got std=" + std);
        }
    }

    @Override
    public double sample(RandomGenerator rng) {
        return mean + std * rng.nextGaussian();
    }
}
