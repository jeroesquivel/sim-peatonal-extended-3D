package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Circle;
import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.input.csv.GeneratorsCsvRow;
import ar.edu.itba.simped.input.csv.ServersCsvRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class BlockInMultipleLayersValidatorTest {

    @Test
    void firesV12WhenSameBlockNameAppearsInTwoLayers() {
        Location loc = new Location("SHARED",
                new Circle(new Vec2(0, 0), 1), Optional.empty());
        Exit exit = new Exit("SHARED",
                new Segment(new Vec2(0, 0), new Vec2(2, 0)), OptionalDouble.empty());

        ErrorAccumulator acc = new ErrorAccumulator();
        BlockInMultipleLayersValidator.validate(
                List.of(loc), List.of(exit), List.of(), List.of(), acc);

        assertThat(acc.errors()).extracting(e -> e.code())
                .containsExactly(ValidationCode.V12);
    }

    @Test
    void noErrorWhenBlocksAreDisjoint() {
        Location loc = new Location("T1",
                new Circle(new Vec2(0, 0), 1), Optional.empty());
        Exit exit = new Exit("E1",
                new Segment(new Vec2(0, 0), new Vec2(2, 0)), OptionalDouble.empty());
        GeneratorsCsvRow gen = new GeneratorsCsvRow("G1",
                new Rectangle(new Vec2(5, 5), new Vec2(6, 6)));
        ServersCsvRow.ServerRow srv = new ServersCsvRow.ServerRow(
                "S1", 1, new Rectangle(new Vec2(10, 10), new Vec2(11, 11)));

        ErrorAccumulator acc = new ErrorAccumulator();
        BlockInMultipleLayersValidator.validate(
                List.of(loc), List.of(exit), List.of(gen), List.of(srv), acc);

        assertThat(acc.hasErrors()).isFalse();
    }
}
