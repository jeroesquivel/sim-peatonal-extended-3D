package ar.edu.itba.simped.environment.servers.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Per-server timing configuration. One variant per {@link ServerType}; the
 * variant must match the {@code Server.type} (validated at construction time).
 */
public sealed interface ServerConfig {

    /**
     * Service times sampled from {@code Exp(1/tMean)}, one per client.
     *
     * @param tMean mean service time [s]; must be positive and finite
     */
    record Queue(double tMean) implements ServerConfig {
        public Queue {
            requirePositive("tMean", tMean);
        }
    }

    /**
     * Traffic-light cycle. The light is GREEN when
     * {@code ((t - offset) mod period) < green}, and RED otherwise. While
     * green, every agent waiting at the server is released (allowed to cross);
     * while red they are held. The {@code offset} staggers different semaphores
     * so they are not all green at the same time.
     *
     * @param period full cycle length (green + red) [s]; positive and finite
     * @param green  green duration within the cycle [s]; in {@code (0, period]}
     * @param offset phase shift of the cycle [s]; finite and {@code >= 0}
     */
    record Semaphore(double period, double green, double offset) implements ServerConfig {
        public Semaphore {
            requirePositive("period", period);
            if (!(green > 0.0) || green > period || Double.isInfinite(green)) {
                throw new IllegalArgumentException(
                        "green must be in (0, period], got " + green);
            }
            if (!(offset >= 0.0) || Double.isInfinite(offset)) {
                throw new IllegalArgumentException(
                        "offset must be finite and >= 0, got " + offset);
            }
        }

        /** Whether the light is green at simulation time {@code t}. */
        public boolean isGreen(double t) {
            double phase = ((t - offset) % period + period) % period;
            return phase < green;
        }
    }

    /**
     * Scheduled batch releases: at each {@code tInit[i] + tMean} every agent in
     * the region leaves at once. {@code tInit} must be strictly increasing.
     *
     * @param tInit start times of each session [s]; must be non-empty and
     *              strictly increasing
     * @param tMean duration of every session [s]; must be positive and finite
     */
    record Classroom(double[] tInit, double tMean) implements ServerConfig {
        public Classroom {
            Objects.requireNonNull(tInit, "tInit must not be null");
            if (tInit.length == 0) {
                throw new IllegalArgumentException("tInit must have at least one entry");
            }
            for (int i = 0; i < tInit.length; i++) {
                if (!(tInit[i] >= 0.0) || Double.isInfinite(tInit[i])) {
                    throw new IllegalArgumentException(
                            "tInit[" + i + "] must be finite and non-negative, got " + tInit[i]);
                }
                if (i > 0 && !(tInit[i] > tInit[i - 1])) {
                    throw new IllegalArgumentException(
                            "tInit must be strictly increasing at index " + i);
                }
            }
            requirePositive("tMean", tMean);
            tInit = tInit.clone();
        }

        @Override
        public double[] tInit() {
            return tInit.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Classroom c && Arrays.equals(tInit, c.tInit) && tMean == c.tMean;
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(tInit) + Double.hashCode(tMean);
        }

        @Override
        public String toString() {
            return "Classroom[tInit=" + Arrays.toString(tInit) + ", tMean=" + tMean + "]";
        }
    }

    private static void requirePositive(String name, double v) {
        if (!(v > 0.0) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be positive and finite, got " + v);
        }
    }
}
