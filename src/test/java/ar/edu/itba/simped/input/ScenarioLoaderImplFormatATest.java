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

class ScenarioLoaderImplFormatATest {

    @Test
    void loadsCompleteFormatAScenario(@TempDir Path dir) throws IOException {
        writeMinimalFormatA(dir);

        LoadedScenario scenario = new ScenarioLoaderImpl().load(dir);

        assertThat(scenario.geometry().walls()).hasSize(1);
        assertThat(scenario.geometry().locations()).hasSize(1);
        assertThat(scenario.geometry().exits()).hasSize(1);
        assertThat(scenario.geometry().generatorZones()).hasSize(1);
        assertThat(scenario.geometry().serverZones()).hasSize(1);

        assertThat(scenario.simParams().dt()).isEqualTo(0.1);
        assertThat(scenario.simParams().dtOut()).isEqualTo(0.5);
        assertThat(scenario.simParams().tTotal()).isEqualTo(100.0);

        assertThat(scenario.planTemplates()).containsKey("PLAN_A");
        assertThat(scenario.planTemplates().get("PLAN_A").steps()).hasSize(2);
        assertThat(scenario.planTemplates().get("PLAN_A").steps().get(0).type())
                .isEqualTo(TaskType.LOCATION);
        assertThat(scenario.planTemplates().get("PLAN_A").steps().get(1).type())
                .isEqualTo(TaskType.EXIT);

        assertThat(scenario.geometry().serverZones().get(0).type()).isEqualTo(ServerType.QUEUE);
    }

    @Test
    void aggregatesV4WhenGeneratorParamReferencesUnknownBlock(@TempDir Path dir) throws IOException {
        writeMinimalFormatA(dir);
        // GENERATOR_PARAMS references a generator block that is not in GENERATORS.csv.
        Files.writeString(dir.resolve("GENERATOR_PARAMS.csv"),
                "block_name,mode,rate_or_count,agent_type,plan_template\n"
                        + "GEN1,flowrate,1.0,agentA,PLAN_A\n"
                        + "GHOST,flowrate,1.0,agentA,PLAN_A\n");

        assertThatThrownBy(() -> new ScenarioLoaderImpl().load(dir))
                .isInstanceOf(ScenarioValidationException.class)
                .satisfies(e -> {
                    ScenarioValidationException sve = (ScenarioValidationException) e;
                    assertThat(sve.errors())
                            .extracting(ve -> ve.code())
                            .contains(ValidationCode.V4);
                });
    }

    @Test
    void aggregatesV18WhenGeneratorReferencesUndefinedPlan(@TempDir Path dir) throws IOException {
        writeMinimalFormatA(dir);
        Files.writeString(dir.resolve("GENERATOR_PARAMS.csv"),
                "block_name,mode,rate_or_count,agent_type,plan_template\n"
                        + "GEN1,flowrate,1.0,agentA,UNDEFINED_PLAN\n");

        assertThatThrownBy(() -> new ScenarioLoaderImpl().load(dir))
                .isInstanceOf(ScenarioValidationException.class)
                .satisfies(e -> {
                    ScenarioValidationException sve = (ScenarioValidationException) e;
                    assertThat(sve.errors())
                            .extracting(ve -> ve.code())
                            .contains(ValidationCode.V18);
                });
    }

    private static void writeMinimalFormatA(Path dir) throws IOException {
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

        Files.writeString(dir.resolve("SIM_PARAMS.csv"),
                "key,value\n"
                        + "dt,0.1\n"
                        + "dt_out,0.5\n"
                        + "t_total,100.0\n");

        Files.writeString(dir.resolve("GENERATOR_PARAMS.csv"),
                "block_name,mode,rate_or_count,agent_type,plan_template\n"
                        + "GEN1,flowrate,1.0,agentA,PLAN_A\n");

        Files.writeString(dir.resolve("SERVER_PARAMS.csv"),
                "block_name,type,server_time_param,green_duration,t_init\n"
                        + "CASHIER,queue,2.0,,\n");

        Files.writeString(dir.resolve("PLANS.csv"),
                "template_name,step_order,target_type,target_block_name\n"
                        + "PLAN_A,1,TARGET,PRODUCT1\n"
                        + "PLAN_A,2,EXIT,EXIT1\n");
    }
}
