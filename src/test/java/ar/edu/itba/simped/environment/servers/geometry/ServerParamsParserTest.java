package ar.edu.itba.simped.environment.servers.geometry;

import ar.edu.itba.simped.environment.servers.model.ServerConfig;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerParamsParserTest {

    @Test
    void parsesEachTypeAndKeysByServerNameWithoutSuffix() {
        String csv = """
                block_name, type, server_time_param, t_init
                CASHIER_1_SERVER, queue, 8.0,
                SEMAPHORE_1_SERVER, semaphore, 5.0,
                PRESENTATION_1_SERVER, classroom, 30.0, 0.0
                PRESENTATION_1_SERVER, classroom, 30.0, 45.0
                """;
        Map<String, ServerConfig> out = ServerParamsParser.parse(new StringReader(csv));

        assertTrue(out.containsKey("CASHIER_1"));
        assertTrue(out.containsKey("SEMAPHORE_1"));
        assertTrue(out.containsKey("PRESENTATION_1"));

        ServerConfig.Queue q = assertInstanceOf(ServerConfig.Queue.class, out.get("CASHIER_1"));
        assertEquals(8.0, q.tMean(), 1e-9);

        ServerConfig.Semaphore s = assertInstanceOf(ServerConfig.Semaphore.class, out.get("SEMAPHORE_1"));
        assertEquals(5.0, s.period(), 1e-9);

        ServerConfig.Classroom c = assertInstanceOf(ServerConfig.Classroom.class, out.get("PRESENTATION_1"));
        assertEquals(30.0, c.tMean(), 1e-9);
        assertArrayEquals(new double[]{0.0, 45.0}, c.tInit(), 1e-9);
    }

    @Test
    void classroomWithMixedTimeParamIsRejected() {
        String csv = """
                block_name, type, server_time_param, t_init
                P_SERVER, classroom, 30.0, 0.0
                P_SERVER, classroom, 25.0, 45.0
                """;
        assertThrows(IllegalArgumentException.class,
                () -> ServerParamsParser.parse(new StringReader(csv)));
    }

    @Test
    void queueWithTInitIsRejected() {
        String csv = """
                block_name, type, server_time_param, t_init
                C_SERVER, queue, 8.0, 12.0
                """;
        assertThrows(IllegalArgumentException.class,
                () -> ServerParamsParser.parse(new StringReader(csv)));
    }

    @Test
    void classroomWithNonIncreasingTInitIsRejected() {
        String csv = """
                block_name, type, server_time_param, t_init
                P_SERVER, classroom, 30.0, 45.0
                P_SERVER, classroom, 30.0, 0.0
                """;
        // ServerConfig.Classroom's own validation triggers this.
        assertThrows(IllegalArgumentException.class,
                () -> ServerParamsParser.parse(new StringReader(csv)));
    }

    @Test
    void blockNameWithoutSuffixIsRejected() {
        String csv = """
                block_name, type, server_time_param, t_init
                CASHIER_1, queue, 8.0,
                """;
        assertThrows(IllegalArgumentException.class,
                () -> ServerParamsParser.parse(new StringReader(csv)));
    }
}
