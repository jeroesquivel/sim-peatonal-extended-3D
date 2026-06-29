package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.ServerParams;
import ar.edu.itba.simped.core.ServerType;
import ar.edu.itba.simped.core.SimulationParameters;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.input.csv.GeneratorsCsvRow;
import ar.edu.itba.simped.input.csv.ServersCsvRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class JoinersTest {

    @Test
    void firesV4WhenGeneratorParamHasNoCsvCounterpart() {
        RawParams params = new RawParams(
                new SimulationParameters(0.1, 0.1, 1.0),
                Map.of("GHOST_GEN", generatorParams("agent", "PLAN_A")),
                Map.of(),
                Map.of("PLAN_A", List.of()),
                Optional.empty());

        ErrorAccumulator acc = new ErrorAccumulator();
        Joiners.validate(params, List.of(), List.<ServersCsvRow>of(),
                FormatDetector.Format.FORMAT_A, acc);

        assertThat(acc.errors())
                .extracting(e -> e.code())
                .contains(ValidationCode.V4);
    }

    @Test
    void firesV19InFormatAWhenServerParamHasNoCsvCounterpart() {
        RawParams params = new RawParams(
                new SimulationParameters(0.1, 0.1, 1.0),
                Map.of(),
                Map.of("GHOST_SRV", new ServerSpec(ServerType.QUEUE, ServerParams.empty())),
                Map.of(),
                Optional.empty());

        ErrorAccumulator acc = new ErrorAccumulator();
        Joiners.validate(params, List.of(), List.<ServersCsvRow>of(),
                FormatDetector.Format.FORMAT_A, acc);

        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V19);
    }

    @Test
    void firesV5InFormatBWhenServerParamHasNoCsvCounterpart() {
        RawParams params = new RawParams(
                new SimulationParameters(0.1, 0.1, 1.0),
                Map.of(),
                Map.of("GHOST_SRV", new ServerSpec(ServerType.QUEUE, ServerParams.empty())),
                Map.of(),
                Optional.empty());

        ErrorAccumulator acc = new ErrorAccumulator();
        Joiners.validate(params, List.of(), List.<ServersCsvRow>of(),
                FormatDetector.Format.FORMAT_B, acc);

        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V5);
    }

    @Test
    void firesV18InFormatAForUndefinedPlanReference() {
        RawParams params = new RawParams(
                new SimulationParameters(0.1, 0.1, 1.0),
                Map.of("GEN", generatorParams("agent", "MISSING_PLAN")),
                Map.of(),
                Map.of("OTHER_PLAN", List.of()),
                Optional.empty());

        GeneratorsCsvRow row = new GeneratorsCsvRow("GEN",
                new Rectangle(new Vec2(0, 0), new Vec2(1, 1)));

        ErrorAccumulator acc = new ErrorAccumulator();
        Joiners.validate(params, List.of(row), List.<ServersCsvRow>of(),
                FormatDetector.Format.FORMAT_A, acc);

        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V18);
    }

    @Test
    void firesV8InFormatBForUndefinedPlanReference() {
        RawParams params = new RawParams(
                new SimulationParameters(0.1, 0.1, 1.0),
                Map.of("GEN", generatorParams("agent", "MISSING_PLAN")),
                Map.of(),
                Map.of("OTHER_PLAN", List.of()),
                Optional.empty());

        GeneratorsCsvRow row = new GeneratorsCsvRow("GEN",
                new Rectangle(new Vec2(0, 0), new Vec2(1, 1)));

        ErrorAccumulator acc = new ErrorAccumulator();
        Joiners.validate(params, List.of(row), List.<ServersCsvRow>of(),
                FormatDetector.Format.FORMAT_B, acc);

        assertThat(acc.errors())
                .extracting(e -> e.code())
                .containsExactly(ValidationCode.V8);
    }

    @Test
    void noErrorsWhenAllReferencesResolve() {
        GeneratorsCsvRow genRow = new GeneratorsCsvRow("GEN",
                new Rectangle(new Vec2(0, 0), new Vec2(1, 1)));
        ServersCsvRow.ServerRow srvRow = new ServersCsvRow.ServerRow(
                "SRV", 1, new Rectangle(new Vec2(2, 2), new Vec2(3, 3)));
        ServersCsvRow.QueueRow qRow = new ServersCsvRow.QueueRow(
                "SRV", 1, 1, new Segment(new Vec2(2.5, 2), new Vec2(2.5, 3)));

        RawParams params = new RawParams(
                new SimulationParameters(0.1, 0.1, 1.0),
                Map.of("GEN", generatorParams("agent", "PLAN_A")),
                Map.of("SRV", new ServerSpec(ServerType.QUEUE, ServerParams.empty())),
                Map.of("PLAN_A", List.of()),
                Optional.empty());

        ErrorAccumulator acc = new ErrorAccumulator();
        Joiners.validate(params, List.of(genRow), List.of(srvRow, qRow),
                FormatDetector.Format.FORMAT_A, acc);

        assertThat(acc.hasErrors()).isFalse();
    }

    private static GeneratorRawParams generatorParams(String agentType, String planName) {
        return new GeneratorRawParams(
                agentType, planName,
                Optional.of("flowrate"),
                OptionalDouble.of(1.0),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalDouble.empty());
    }
}
