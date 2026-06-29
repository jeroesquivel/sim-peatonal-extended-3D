package ar.edu.itba.simped.environment.servers.service;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTimeSamplerTest {

    @Test
    void sameSeedProducesIdenticalSequence() {
        ServiceTimeSampler a = new ServiceTimeSampler(new Random(7));
        ServiceTimeSampler b = new ServiceTimeSampler(new Random(7));
        for (int i = 0; i < 100; i++) {
            assertEquals(a.sampleExponential(3.0), b.sampleExponential(3.0), 0.0);
        }
    }

    @Test
    void samplesArePositiveAndMeanIsApproximate() {
        ServiceTimeSampler s = new ServiceTimeSampler(new Random(123));
        double mean = 2.0;
        int n = 200_000;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double x = s.sampleExponential(mean);
            assertTrue(x > 0.0, "service time must be positive");
            sum += x;
        }
        assertEquals(mean, sum / n, 0.05); // law of large numbers
    }

    @Test
    void invalidMeanThrows() {
        ServiceTimeSampler s = new ServiceTimeSampler(new Random(1));
        assertThrows(IllegalArgumentException.class, () -> s.sampleExponential(0.0));
        assertThrows(IllegalArgumentException.class, () -> s.sampleExponential(-1.0));
    }
}
