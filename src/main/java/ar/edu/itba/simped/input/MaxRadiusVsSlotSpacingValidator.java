package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Gaussian;
import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.validation.ValidationCode;

import java.util.Map;
import java.util.Optional;

/**
 * V25 — el radio máximo declarado para un generador no debe permitir que
 * dos agentes ocupando puestos contiguos de cola se solapen.
 *
 * <p>El espaciado entre slots de la cola lo decide G0 (Servers); el
 * mail 2026-06-04 fija el default en 1.0 m. La condición para que no
 * haya solape es {@code 2*maxRadius < slotSpacing}. Esta validación
 * asume el default G0 (1.0 m); si G0 lo expone como parámetro del
 * escenario en el futuro, ajustar.</p>
 *
 * <p>Solo aplica a Formato B, donde {@code maxRadiusDistribution} viene
 * en {@code agents_generators[].agents}. Formato A no declara radio.</p>
 */
public final class MaxRadiusVsSlotSpacingValidator {

    /** Espaciado default entre slots de cola en G0 (m). */
    public static final double DEFAULT_SLOT_SPACING_M = 1.0;

    /** Cuantiles para tomar el "max" de una distribución gaussiana. */
    private static final double GAUSSIAN_TAIL_K = 3.0;

    private MaxRadiusVsSlotSpacingValidator() {
    }

    public static void validate(Map<String, GeneratorRawParams> generatorsByBlock,
                                ErrorAccumulator acc) {
        for (Map.Entry<String, GeneratorRawParams> entry : generatorsByBlock.entrySet()) {
            Optional<Distribution> maxRadius = entry.getValue().maxRadiusDistribution();
            if (maxRadius.isEmpty()) {
                continue;
            }
            double upperBound = upperBoundOf(maxRadius.get());
            if (2.0 * upperBound >= DEFAULT_SLOT_SPACING_M) {
                acc.add(ValidationCode.V25,
                        "agents_generators[" + entry.getKey() + "].max_radius_distribution",
                        String.format(
                                "2 * maxRadius = %.3fm >= slot spacing %.1fm — agentes en puestos contiguos de la cola se solaparían",
                                2.0 * upperBound, DEFAULT_SLOT_SPACING_M));
            }
        }
    }

    private static double upperBoundOf(Distribution d) {
        if (d instanceof Uniform u) {
            return u.max();
        }
        if (d instanceof Gaussian g) {
            return g.mean() + GAUSSIAN_TAIL_K * g.std();
        }
        if (d instanceof Deterministic det) {
            return det.value();
        }
        throw new IllegalStateException("unknown Distribution subtype: " + d.getClass());
    }
}
