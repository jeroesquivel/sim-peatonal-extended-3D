package ar.edu.itba.simped.core;

import java.util.random.RandomGenerator;

public record Uniform(double min, double max) implements Distribution {

    public Uniform {
        if (min > max) {
            throw new IllegalArgumentException(
                    "Uniform distribution requires min <= max, got min=" + min + ", max=" + max);
        }
    }

    @Override
    public double sample(RandomGenerator rng) {
        if (min == max) {
            return min;
        }
        return min + (max - min) * rng.nextDouble();
    }
}
