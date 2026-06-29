package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import ar.edu.itba.simped.core.ServerType;
import ar.edu.itba.simped.core.TaskType;
import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioLoaderImplFormatBTest {

    @Test
    void loadsCompleteFormatBScenario(@TempDir Path dir) throws IOException {
        writeMinimalFormatB(dir);

        LoadedScenario scenario = new ScenarioLoaderImpl().load(dir);

        assertThat(scenario.geometry().walls()).hasSize(1);
        assertThat(scenario.geometry().locations()).hasSize(1);
        assertThat(scenario.geometry().exits()).hasSize(1);
        assertThat(scenario.geometry().generatorZones()).hasSize(1);
        assertThat(scenario.geometry().serverZones()).hasSize(1);

        assertThat(scenario.simParams().tTotal()).isEqualTo(1200.0);
        assertThat(scenario.simParams().dtOut()).isEqualTo(0.5);

        assertThat(scenario.legacy()).isPresent();
        assertThat(scenario.legacy().get().evacuateAt()).hasValue(1000.0);
        assertThat(scenario.legacy().get().blueprintName()).isEqualTo("PLANO_TEST");

        // dwell distribution applied via TargetDwellsAccumulator → assembler
        assertThat(scenario.geometry().locations().get(0).dwellTime()).isPresent();

        // ServerTypeStrategy (CSV-inferred) sees the QUEUE row → QUEUE type
        assertThat(scenario.geometry().serverZones().get(0).type()).isEqualTo(ServerType.QUEUE);

        // 1 RawGroupStep + 1 final RawAnyStep(EXIT) → 2 PlanSteps
        assertThat(scenario.planTemplates()).containsKey("PLAN_B");
        assertThat(scenario.planTemplates().get("PLAN_B").steps()).hasSize(2);
        assertThat(scenario.planTemplates().get("PLAN_B").steps().get(0).type())
                .isEqualTo(TaskType.LOCATION);
        // El dwell viaja en el candidato del PlanStep.
        assertThat(scenario.planTemplates().get("PLAN_B").steps().get(0)
                .candidates().get(0).dwellDistribution())
                .isPresent();
        assertThat(scenario.planTemplates().get("PLAN_B").steps().get(1).type())
                .isEqualTo(TaskType.EXIT);
    }

    private static void writeMinimalFormatB(Path dir) throws IOException {
        Files.writeString(dir.resolve("WALLS.csv"),
                "x1,y1,z1,x2,y2,z2\n"
                        + "0,0,0,10,0,0\n");

        Files.writeString(dir.resolve("TARGETS.csv"),
                "block_name,figure_type,radius,x1,y1,z1,x2,y2,z2\n"
                        + "PRODUCT1,CIRCLE,1.0,5,5,0,0,0,0\n");

        Files.writeString(dir.resolve("EXITS.csv"),
                "block_name,x1,y1,z1,x2,y2,z2\n"
                        + "EXIT1,9,0,0,10,0,0\n");

        Files.writeString(dir.resolve("GENERATORS.csv"),
                "block_name,x1,y1,z1,x2,y2,z2\n"
                        + "GEN1,0,8,0,2,10,0\n");

        Files.writeString(dir.resolve("SERVERS.csv"),
                "block_name,x1,y1,z1,x2,y2,z2\n"
                        + "CASHIER_1_SERVER,7,7,0,8,8,0\n"
                        + "CASHIER_1_QUEUE001,6,7,0,7,8,0\n");

        Files.writeString(dir.resolve("parameters.json"),
                "{\n"
                        + "  \"max_time\": 1200.0,\n"
                        + "  \"evacuate_at\": 1000.0,\n"
                        + "  \"blueprint_name\": \"PLANO_TEST\",\n"
                        + "  \"output_delta_time\": 0.5,\n"
                        + "  \"agents_generators\": [\n"
                        + "    {\n"
                        + "      \"block_name\": \"GEN1\",\n"
                        + "      \"plan\": \"PLAN_B\",\n"
                        + "      \"active_time\": 100.0,\n"
                        + "      \"inactive_time\": 0.0,\n"
                        + "      \"agents\": {\n"
                        + "        \"min_radius_distribution\": {\"type\": \"UNIFORM\", \"min\": 0.2, \"max\": 0.3},\n"
                        + "        \"max_radius_distribution\": {\"type\": \"UNIFORM\", \"min\": 0.3, \"max\": 0.4},\n"
                        + "        \"max_velocity\": 1.5\n"
                        + "      },\n"
                        + "      \"generation\": {\n"
                        + "        \"period\": 1.0,\n"
                        + "        \"quantity_distribution\": {\"type\": \"UNIFORM\", \"min\": 1.0, \"max\": 3.0}\n"
                        + "      }\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"targets\": [\n"
                        + "    {\n"
                        + "      \"block_name\": \"PRODUCT1\",\n"
                        + "      \"attending_time_distribution\": {\"type\": \"GAUSSIAN\", \"mean\": 5.0, \"std\": 1.0}\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"servers\": [\n"
                        + "    {\n"
                        + "      \"block_name\": \"CASHIER\",\n"
                        + "      \"attending_time_distribution\": {\"type\": \"GAUSSIAN\", \"mean\": 2.0, \"std\": 0.5},\n"
                        + "      \"max_capacity\": 2\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"plans\": [\n"
                        + "    {\n"
                        + "      \"name\": \"PLAN_B\",\n"
                        + "      \"exit_selection\": \"FIRST\",\n"
                        + "      \"objective_groups\": [\n"
                        + "        {\n"
                        + "          \"block_name\": \"PRODUCT1\",\n"
                        + "          \"layer\": \"TARGETS\",\n"
                        + "          \"objective_selection\": \"ALL\",\n"
                        + "          \"quantity_distribution\": {\"type\": \"UNIFORM\", \"min\": 1.0, \"max\": 1.0}\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}\n");
    }

    // === iter 9: tests del campo `mode` ===
    // (El cálculo del rate desde period+quantity lo hace G6 en App.effectiveFlowRatePerMin
    // — commit e5285e1 — no el loader. Por eso no se testea acá.)

    @Test
    void formatB_modeReadFromJsonInstantOccupation(@TempDir Path dir) throws IOException {
        writeWithGeneration(dir, 1.0, "\"UNIFORM\", \"min\": 1.0, \"max\": 1.0",
                "instant_occupation");
        LoadedScenario s = new ScenarioLoaderImpl().load(dir);
        assertThat(s.geometry().generatorZones().get(0).params().mode())
                .hasValue("instant_occupation");
    }

    @Test
    void formatB_modeReadFromJsonFlowrate(@TempDir Path dir) throws IOException {
        writeWithGeneration(dir, 1.0, "\"UNIFORM\", \"min\": 1.0, \"max\": 1.0", "flowrate");
        LoadedScenario s = new ScenarioLoaderImpl().load(dir);
        assertThat(s.geometry().generatorZones().get(0).params().mode())
                .hasValue("flowrate");
    }

    @Test
    void formatB_modeAbsentDefaultsEmpty(@TempDir Path dir) throws IOException {
        writeWithGeneration(dir, 1.0, "\"UNIFORM\", \"min\": 1.0, \"max\": 1.0", null);
        LoadedScenario s = new ScenarioLoaderImpl().load(dir);
        assertThat(s.geometry().generatorZones().get(0).params().mode()).isEmpty();
    }

    @Test
    void formatB_modeInvalidTriggersV14(@TempDir Path dir) throws IOException {
        writeWithGeneration(dir, 1.0, "\"UNIFORM\", \"min\": 1.0, \"max\": 1.0", "garbage");
        assertThatThrownBy(() -> new ScenarioLoaderImpl().load(dir))
                .isInstanceOf(ScenarioValidationException.class)
                .satisfies(ex -> assertThat(((ScenarioValidationException) ex).errors())
                        .anySatisfy(err -> assertThat(err.code()).isEqualTo(ValidationCode.V14)));
    }

    @Test
    void formatB_modeIsCaseInsensitive(@TempDir Path dir) throws IOException {
        writeWithGeneration(dir, 1.0, "\"UNIFORM\", \"min\": 1.0, \"max\": 1.0",
                "INSTANT_OCCUPATION");
        LoadedScenario s = new ScenarioLoaderImpl().load(dir);
        // Normalizado a lowercase
        assertThat(s.geometry().generatorZones().get(0).params().mode())
                .hasValue("instant_occupation");
    }

    /**
     * Variante del fixture mínimo donde se parametrizan period, quantity_distribution y mode.
     * quantityDistJsonInner es el cuerpo del objeto (sin las llaves externas), ej.
     * {@code "\"UNIFORM\", \"min\": 1.0, \"max\": 1.0"}.
     * Si {@code mode} es null, no se emite el campo (testea el default empty).
     */
    private static void writeWithGeneration(Path dir, double period, String quantityDistJsonInner,
                                            String mode) throws IOException {
        writeMinimalFormatB(dir);
        String modeLine = mode == null ? "" : "      \"mode\": \"" + mode + "\",\n";
        String json = Files.readString(dir.resolve("parameters.json"))
                .replace("\"plan\": \"PLAN_B\",\n",
                        "\"plan\": \"PLAN_B\",\n" + modeLine)
                .replace("\"period\": 1.0,\n"
                                + "        \"quantity_distribution\": {\"type\": \"UNIFORM\", \"min\": 1.0, \"max\": 3.0}",
                        "\"period\": " + period + ",\n"
                                + "        \"quantity_distribution\": {\"type\": " + quantityDistJsonInner + "}");
        Files.writeString(dir.resolve("parameters.json"), json);
    }
}
