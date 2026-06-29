package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Gaussian;
import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.validation.ValidationCode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica V25 — el max radius del agente no debe permitir que dos
 * agentes en puestos contiguos de cola se solapen. Threshold actual:
 * 1.0 m (default G0). Condición: {@code 2 * maxRadius < 1.0}.
 */
class MaxRadiusVsSlotSpacingValidatorTest {

    @Test
    void noErrorWhenUniformMaxBelowThreshold() {
        ErrorAccumulator acc = new ErrorAccumulator();
        MaxRadiusVsSlotSpacingValidator.validate(
                Map.of("GEN1", generatorWithMaxRadius(new Uniform(0.20, 0.32))), acc);
        assertThat(acc.hasErrors()).isFalse();
    }

    @Test
    void firesV25WhenUniformMaxExceedsHalfSpacing() {
        ErrorAccumulator acc = new ErrorAccumulator();
        // max = 0.6 → 2*0.6 = 1.2 > 1.0
        MaxRadiusVsSlotSpacingValidator.validate(
                Map.of("GEN1", generatorWithMaxRadius(new Uniform(0.30, 0.60))), acc);
        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V25);
    }

    @Test
    void firesV25WhenUniformMaxEqualsHalfSpacing() {
        // borde estricto: >= dispara
        ErrorAccumulator acc = new ErrorAccumulator();
        MaxRadiusVsSlotSpacingValidator.validate(
                Map.of("GEN1", generatorWithMaxRadius(new Uniform(0.50, 0.50))), acc);
        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V25);
    }

    @Test
    void firesV25WhenGaussianTailExceedsHalfSpacing() {
        ErrorAccumulator acc = new ErrorAccumulator();
        // mean + 3*std = 0.3 + 3*0.1 = 0.6 → 1.2 > 1.0
        MaxRadiusVsSlotSpacingValidator.validate(
                Map.of("GEN1", generatorWithMaxRadius(new Gaussian(0.3, 0.1))), acc);
        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V25);
    }

    @Test
    void noErrorWhenMaxRadiusDistributionAbsent() {
        ErrorAccumulator acc = new ErrorAccumulator();
        MaxRadiusVsSlotSpacingValidator.validate(
                Map.of("GEN1", generatorWithoutMaxRadius()), acc);
        assertThat(acc.hasErrors()).isFalse();
    }

    @Test
    void noErrorWhenDeterministicMaxBelowThreshold() {
        ErrorAccumulator acc = new ErrorAccumulator();
        MaxRadiusVsSlotSpacingValidator.validate(
                Map.of("GEN1", generatorWithMaxRadius(new Deterministic(0.4))), acc);
        assertThat(acc.hasErrors()).isFalse();
    }

    private static GeneratorRawParams generatorWithMaxRadius(Distribution maxRadius) {
        return new GeneratorRawParams(
                "agent", "PLAN_A",
                Optional.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                Optional.empty(), Optional.empty(), Optional.of(maxRadius),
                OptionalDouble.empty());
    }

    private static GeneratorRawParams generatorWithoutMaxRadius() {
        return new GeneratorRawParams(
                "agent", "PLAN_A",
                Optional.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                OptionalDouble.empty());
    }
}
