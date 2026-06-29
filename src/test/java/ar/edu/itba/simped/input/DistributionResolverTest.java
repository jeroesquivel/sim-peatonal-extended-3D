package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Gaussian;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.validation.ValidationCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DistributionResolverTest {

    @Test
    void resolvesUniform() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                "UNIFORM", 1.0, 5.0, null, null, "loc", acc);

        assertThat(d).get().isInstanceOf(Uniform.class);
        assertThat(acc.hasErrors()).isFalse();
    }

    @Test
    void resolvesGaussian() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                "GAUSSIAN", null, null, 10.0, 1.0, "loc", acc);

        assertThat(d).get().isInstanceOf(Gaussian.class);
        assertThat(acc.hasErrors()).isFalse();
    }

    @Test
    void firesV10WhenTypeIsNull() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                null, 1.0, 2.0, null, null, "loc", acc);

        assertThat(d).isEmpty();
        assertThat(acc.errors()).extracting(e -> e.code()).containsExactly(ValidationCode.V10);
    }

    @Test
    void firesV10WhenTypeIsUnsupported() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                "POISSON", null, null, null, null, "loc", acc);

        assertThat(d).isEmpty();
        assertThat(acc.errors()).extracting(e -> e.code()).containsExactly(ValidationCode.V10);
    }

    @Test
    void firesV11WhenRequiredFieldMissing() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                "UNIFORM", null, 5.0, null, null, "loc", acc);

        assertThat(d).isEmpty();
        assertThat(acc.errors()).extracting(e -> e.code()).containsExactly(ValidationCode.V11);
    }

    @Test
    void firesV11WhenGaussianStdIsNegative() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                "GAUSSIAN", null, null, 10.0, -1.0, "loc", acc);

        assertThat(d).isEmpty();
        assertThat(acc.errors()).extracting(e -> e.code()).containsExactly(ValidationCode.V11);
    }

    @Test
    void resolvesGaussianWithZeroStdAsDeterministic() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                "GAUSSIAN", null, null, 10.0, 0.0, "loc", acc);

        assertThat(d).get().isInstanceOf(ar.edu.itba.simped.core.Deterministic.class);
        assertThat(acc.hasErrors()).isFalse();
    }

    @Test
    void firesV11WhenUniformMinExceedsMax() {
        ErrorAccumulator acc = new ErrorAccumulator();
        Optional<Distribution> d = DistributionResolver.resolve(
                "UNIFORM", 9.0, 1.0, null, null, "loc", acc);

        assertThat(d).isEmpty();
        assertThat(acc.errors()).extracting(e -> e.code()).containsExactly(ValidationCode.V11);
    }
}
