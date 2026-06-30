package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Wall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WallsCsvReaderTest {

    private Path write(Path dir, String content) throws IOException {
        Path p = dir.resolve("WALLS.csv");
        Files.writeString(p, content);
        return p;
    }

    @Test
    void propagatesFloorZFromCsv(@TempDir Path dir) throws IOException {
        Path p = write(dir, """
                x1, y1, z1, x2, y2, z2
                0.0, 0.0, 2.0, 5.0, 0.0, 2.0
                """);

        List<Wall> walls = new WallsCsvReader().read(p);

        assertThat(walls).hasSize(1);
        assertThat(walls.get(0).z()).isEqualTo(2.0, within(1e-9));
    }

    @Test
    void usesZ1WhenEndpointsDifferInZ(@TempDir Path dir) throws IOException {
        // Elemento planar mal declarado (z1 != z2): se usa z1 y se emite warning.
        Path p = write(dir, """
                x1, y1, z1, x2, y2, z2
                0.0, 0.0, 1.0, 5.0, 0.0, 4.0
                """);

        List<Wall> walls = new WallsCsvReader().read(p);

        assertThat(walls).hasSize(1);
        assertThat(walls.get(0).z()).isEqualTo(1.0, within(1e-9));
    }
}
