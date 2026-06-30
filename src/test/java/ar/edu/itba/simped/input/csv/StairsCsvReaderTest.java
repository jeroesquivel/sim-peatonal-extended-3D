package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StairsCsvReaderTest {

    private Path write(Path dir, String content) throws IOException {
        Path p = dir.resolve("STAIRS.csv");
        Files.writeString(p, content);
        return p;
    }

    @Test
    void parsesAxisWithPerEndpointZAndExplicitSpeedFactor(@TempDir Path dir) throws IOException {
        Path p = write(dir, """
                block_name, x1, y1, z1, x2, y2, z2, width, speed_factor
                MAIN, 10, 5, 0.0, 10, 9, 3.0, 2.0, 0.4
                """);

        List<Stairs> stairs = new StairsCsvReader().read(p);

        assertThat(stairs).hasSize(1);
        Stairs s = stairs.get(0);
        assertThat(s.blockName()).isEqualTo("MAIN");
        assertThat(s.foot().z()).isEqualTo(0.0, within(1e-9));
        assertThat(s.top().z()).isEqualTo(3.0, within(1e-9));
        assertThat(s.width()).isEqualTo(2.0, within(1e-9));
        assertThat(s.speedFactor()).isEqualTo(0.4, within(1e-9));
    }

    @Test
    void usesDefaultSpeedFactorWhenColumnOmitted(@TempDir Path dir) throws IOException {
        Path p = write(dir, """
                block_name, x1, y1, z1, x2, y2, z2, width
                MAIN, 10, 5, 0.0, 10, 9, 3.0, 2.0
                """);

        List<Stairs> stairs = new StairsCsvReader().read(p);

        assertThat(stairs).hasSize(1);
        assertThat(stairs.get(0).speedFactor()).isEqualTo(Stairs.DEFAULT_SPEED_FACTOR);
    }

    @Test
    void emptyLayerIsAllowed(@TempDir Path dir) throws IOException {
        Path p = write(dir, "block_name, x1, y1, z1, x2, y2, z2, width\n");
        assertThat(new StairsCsvReader().read(p)).isEmpty();
    }

    @Test
    void rejectsStairConnectingSameFloor(@TempDir Path dir) throws IOException {
        Path p = write(dir, """
                block_name, x1, y1, z1, x2, y2, z2, width
                FLAT, 10, 5, 1.0, 10, 9, 1.0, 2.0
                """);
        assertThatThrownBy(() -> new StairsCsvReader().read(p))
                .isInstanceOf(ScenarioValidationException.class);
    }

    @Test
    void rejectsWrongColumnCount(@TempDir Path dir) throws IOException {
        Path p = write(dir, """
                block_name, x1, y1, z1, x2, y2, z2
                MAIN, 10, 5, 0.0, 10, 9, 3.0
                """);
        assertThatThrownBy(() -> new StairsCsvReader().read(p))
                .isInstanceOf(ScenarioValidationException.class);
    }
}
