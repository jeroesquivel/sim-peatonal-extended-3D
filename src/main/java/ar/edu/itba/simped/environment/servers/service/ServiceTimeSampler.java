package ar.edu.itba.simped.environment.servers.service;

import java.util.Objects;
import java.util.Random;

/**
 * Samples service durations from an exponential distribution.
 *
 * <p>The {@link Random} is injected so that two samplers built with the same
 * seed produce byte-identical sequences (reproducibility contract shared with
 * the rest of the simulator).</p>
 */
public final class ServiceTimeSampler {

    private final Random rng;

    public ServiceTimeSampler(Random rng) {
        this.rng = Objects.requireNonNull(rng, "rng must not be null");
    }

    /**
     * Draws a service duration from {@code Exp(1/mean)} (i.e. with the given
     * mean).
     *
     * @param mean mean service time; must be positive and finite
     */
    public double sampleExponential(double mean) {
        if (!(mean > 0.0) || Double.isInfinite(mean)) {
            throw new IllegalArgumentException(
                    "mean must be positive and finite, got " + mean);
        }
        // 1 - U avoids log(0) when U == 0; U in [0,1).
        return -mean * Math.log(1.0 - rng.nextDouble());
    }

    /** Draws a value uniformly from {@code [min, max)}. */
    public double sampleUniform(double min, double max) {
        if (!(max >= min)) {
            throw new IllegalArgumentException("max must be >= min, got [" + min + ", " + max + "]");
        }
        return min + rng.nextDouble() * (max - min);
    }
}
