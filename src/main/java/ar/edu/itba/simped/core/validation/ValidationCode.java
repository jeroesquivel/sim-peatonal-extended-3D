package ar.edu.itba.simped.core.validation;

/**
 * Códigos de validación V1..V20 producidos por el ScenarioLoader (G3)
 * y consumibles por cualquier otro módulo que necesite reportar errores
 * agregados de entrada.
 */
public enum ValidationCode {
    V1("Layer is empty"),
    V2("Unsupported entity type in layer"),
    V3("targets[] block_name has no geometric counterpart in TARGETS.csv"),
    V4("GENERATOR_PARAMS.block_name has no counterpart in GENERATORS.csv"),
    V5("servers[] block_name has no geometric counterpart in SERVERS.csv"),
    V6("plans[].objective_groups[] references a block_name that does not exist in the declared layer"),
    V7("plans[].objective_groups[] layer/block_name inconsistency"),
    V8("agents_generators[].plan references a non-existing plan template"),
    V9("Plan template has no resolvable steps"),
    V10("Unsupported distribution type"),
    V11("Malformed distribution parameters"),
    V12("Block present in more than one layer"),
    V13("SIM_PARAMS.csv: unknown key"),
    V14("GENERATOR_PARAMS.mode invalid"),
    V15("SERVER_PARAMS.type invalid"),
    V16("PLANS.target_type invalid"),
    V17("PLANS.target_block_name does not exist in referenced CSV"),
    V18("GENERATOR_PARAMS.plan_template not defined in PLANS.csv"),
    V19("SERVER_PARAMS.block_name has no _SERVER counterpart in SERVERS.csv"),
    V20("Scenario directory contains both Format A (CSV params) and Format B (parameters.json)"),
    V25("Agent max radius too large for default queue slot spacing (1.0m): 2*maxRadius >= 1.0");

    private final String description;

    ValidationCode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
