package ar.edu.itba.simped.core;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Parámetros raw de un {@link GeneratorZone}. G3 entrega esto "tal cual"
 * para que G9 (PG) haga su propio mapping al modelo interno.
 *
 * <ul>
 *   <li><b>Formato A</b> ({@code GENERATOR_PARAMS.csv}): pobla
 *       {@code mode} y {@code rateOrCount}; el resto queda
 *       {@code empty()}.</li>
 *   <li><b>Formato B</b> ({@code parameters.json:agents_generators[]}):
 *       pobla {@code activeTime}, {@code inactiveTime}, {@code period},
 *       {@code quantityDistribution}, {@code minRadiusDistribution},
 *       {@code maxRadiusDistribution}, {@code maxVelocity}; {@code mode}
 *       y {@code rateOrCount} quedan {@code empty()}.</li>
 * </ul>
 *
 * <p>Campos universales (siempre presentes): {@code agentType},
 * {@code planTemplateName}.</p>
 */
public record GeneratorRawParams(
        String agentType,
        String planTemplateName,
        Optional<String> mode,
        OptionalDouble rateOrCount,
        OptionalDouble activeTime,
        OptionalDouble inactiveTime,
        OptionalDouble period,
        Optional<Distribution> quantityDistribution,
        Optional<Distribution> minRadiusDistribution,
        Optional<Distribution> maxRadiusDistribution,
        OptionalDouble maxVelocity) {

    public GeneratorRawParams {
        if (agentType == null || agentType.isBlank()) {
            throw new IllegalArgumentException("agentType required");
        }
        if (planTemplateName == null || planTemplateName.isBlank()) {
            throw new IllegalArgumentException("planTemplateName required");
        }
        if (mode == null || rateOrCount == null || activeTime == null || inactiveTime == null
                || period == null || quantityDistribution == null
                || minRadiusDistribution == null || maxRadiusDistribution == null
                || maxVelocity == null) {
            throw new IllegalArgumentException("Optional fields must be empty(), not null");
        }
    }
}
