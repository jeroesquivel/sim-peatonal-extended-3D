package ar.edu.itba.simped.input.csv;

/**
 * Row interno de GENERATOR_PARAMS.csv (Formato A):
 * {@code block_name, mode, rate_or_count, agent_type, plan_template}.
 *
 * <p>mode válido: {@code flowrate} | {@code instant_occupation}.</p>
 */
public record GeneratorParamsRow(
        String blockName,
        String mode,
        double rateOrCount,
        String agentType,
        String planTemplate) {
}
