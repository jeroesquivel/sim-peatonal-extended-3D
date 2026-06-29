package ar.edu.itba.simped.environment.servers.assignment;

import ar.edu.itba.simped.environment.servers.model.Server;
import ar.edu.itba.simped.core.Vec2;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.ToIntFunction;

/**
 * <strong>Optional, not part of the standard delegation flow.</strong>
 *
 * <p>In the interface contract the Plan/StateMachine decides which server an
 * agent goes to (the task references a concrete {@code Server}). This class is
 * offered as a utility for the case where several equivalent servers exist and
 * a caller would rather delegate the choice — it ports the softmax policy of
 * the group's TP4:</p>
 *
 * <pre>
 *     cost_i = meanService * L_i + alpha * d_i / v0
 *     P(i)   &#8733; exp(-cost_i / tau)
 * </pre>
 *
 * <p>The RNG is injected for reproducibility; the softmax is computed with a
 * max-shift so even large costs produce finite weights.</p>
 */
public final class SoftmaxServerAssigner {

    private final Random rng;
    private final double tau;

    public SoftmaxServerAssigner(Random rng, double tau) {
        this.rng = Objects.requireNonNull(rng, "rng must not be null");
        if (!(tau > 0.0) || Double.isInfinite(tau)) {
            throw new IllegalArgumentException("tau must be positive and finite, got " + tau);
        }
        this.tau = tau;
    }

    /**
     * Samples a server id from the softmax distribution over {@code candidates}.
     *
     * @param spawnPos      position the distance term is measured from
     * @param candidates    non-empty list of candidate servers
     * @param queueLengthOf supplies the current visible queue length {@code L_i}
     *                      of a server
     * @param meanService   global mean service time used uniformly
     * @param v0            desired walking speed (&gt; 0)
     * @param alpha         distance weight (&ge; 0)
     * @return the chosen server's id
     */
    public int assign(Vec2 spawnPos, List<Server> candidates,
                      ToIntFunction<Server> queueLengthOf,
                      double meanService, double v0, double alpha) {
        Objects.requireNonNull(spawnPos, "spawnPos must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(queueLengthOf, "queueLengthOf must not be null");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        if (!(v0 > 0.0)) {
            throw new IllegalArgumentException("v0 must be positive, got " + v0);
        }
        if (!(alpha >= 0.0)) {
            throw new IllegalArgumentException("alpha must be non-negative, got " + alpha);
        }

        int k = candidates.size();
        double[] z = new double[k];
        double zMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            Server s = candidates.get(i);
            double l = queueLengthOf.applyAsInt(s);
            double d = spawnPos.distanceTo(s.servicePosition());
            double cost = meanService * l + alpha * d / v0;
            z[i] = -cost / tau;
            if (z[i] > zMax) {
                zMax = z[i];
            }
        }

        double total = 0.0;
        double[] w = new double[k];
        for (int i = 0; i < k; i++) {
            w[i] = Math.exp(z[i] - zMax);
            total += w[i];
        }
        if (!(total > 0.0) || Double.isNaN(total) || Double.isInfinite(total)) {
            return candidates.get(rng.nextInt(k)).id(); // degenerate fallback
        }

        double u = rng.nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < k; i++) {
            acc += w[i];
            if (u < acc) {
                return candidates.get(i).id();
            }
        }
        return candidates.get(k - 1).id();
    }
}
