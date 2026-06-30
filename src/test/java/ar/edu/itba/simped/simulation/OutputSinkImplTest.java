package ar.edu.itba.simped.simulation;

import ar.edu.itba.simped.core.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputSinkImplTest {

    @Test
    void rowHasZAfterY(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("output.csv");
        AgentState a = new AgentState(7, "ped");
        a.setPosition(1.5, 2.5, 3.0);
        a.setVelocity(0.4, -0.2);

        try (OutputSinkImpl sink = new OutputSinkImpl(out)) {
            sink.writeStep(0.1, List.of(a));
        }

        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(1);
        String[] cols = lines.get(0).split(";");
        // tout; x; y; z; vx; vy; state; id
        assertThat(cols).hasSize(8);
        assertThat(Double.parseDouble(cols[0].trim())).isEqualTo(0.1);
        assertThat(Double.parseDouble(cols[1].trim())).isEqualTo(1.5);
        assertThat(Double.parseDouble(cols[2].trim())).isEqualTo(2.5);
        assertThat(Double.parseDouble(cols[3].trim())).isEqualTo(3.0); // z
        assertThat(Double.parseDouble(cols[4].trim())).isEqualTo(0.4); // vx
        assertThat(Double.parseDouble(cols[5].trim())).isEqualTo(-0.2); // vy
        assertThat(cols[6].trim()).isEqualTo("IDLE"); // state default
        assertThat(Integer.parseInt(cols[7].trim())).isEqualTo(7); // id
    }
}
