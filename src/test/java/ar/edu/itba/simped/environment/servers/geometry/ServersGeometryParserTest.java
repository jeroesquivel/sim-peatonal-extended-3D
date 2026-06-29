package ar.edu.itba.simped.environment.servers.geometry;

import ar.edu.itba.simped.environment.servers.model.Server;
import ar.edu.itba.simped.environment.servers.model.ServerType;
import ar.edu.itba.simped.core.Vec2;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServersGeometryParserTest {

    // Legacy *_QUEUE-segment form (no queue direction vector), 2D (no z columns).
    private static final String CSV = """
            block_name, x1, y1, x2, y2
            CASHIER_1_SERVER, 1.0, 19.0, 4.0, 10.0
            CASHIER_1_QUEUE000, 4.8, 19.0, 4.8, 10.0
            CASHIER_2_SERVER, 36.0, 19.0, 39.0, 10.0
            CASHIER_2_QUEUE000, 39.8, 19.0, 39.8, 10.0
            PRESENTATION_1_SERVER, 11.0, 19.0, 14.0, 10.0
            PRESENTATION_2_SERVER, 46.0, 19.0, 49.0, 10.0
            """;

    private List<Server> parse() {
        return ServersGeometryParser.parse(new StringReader(CSV), 0.5);
    }

    @Test
    void groupsBlocksIntoFourServersInOrder() {
        List<Server> servers = parse();
        assertEquals(4, servers.size());
        assertEquals(List.of("CASHIER_1", "CASHIER_2", "PRESENTATION_1", "PRESENTATION_2"),
                servers.stream().map(Server::name).toList());
        // ids assigned by first appearance
        assertEquals(0, servers.get(0).id());
        assertEquals(3, servers.get(3).id());
    }

    @Test
    void cashiersAreQueueWithQueueLine() {
        Server cashier1 = parse().get(0);
        assertEquals(ServerType.QUEUE, cashier1.type());
        assertTrue(cashier1.hasQueue());
        // service position = centroid of (1,19)-(4,10)
        assertEquals(2.5, cashier1.servicePosition().x(), 1e-9);
        assertEquals(14.5, cashier1.servicePosition().y(), 1e-9);
    }

    @Test
    void presentationsAreClassroomWithoutQueue() {
        Server presentation1 = parse().get(2);
        assertEquals(ServerType.CLASSROOM, presentation1.type());
        assertFalse(presentation1.hasQueue());
    }

    @Test
    void queueFrontIsNearestEndpointAndSpacingDeterminesSlots() {
        Server cashier1 = parse().get(0);
        // Segment (4.8,19)-(4.8,10): both endpoints equidistant to service (tie)
        // -> front is the first endpoint (4.8,19).
        Vec2 slot0 = cashier1.queueLine().slotPosition(0);
        assertEquals(4.8, slot0.x(), 1e-9);
        assertEquals(19.0, slot0.y(), 1e-9);
        // length 9.0, spacing 0.5 -> 19 slots; slot 1 is 0.5 below the front.
        assertEquals(19, cashier1.queueLine().maxSlots());
        assertEquals(18.5, cashier1.queueLine().slotPosition(1).y(), 1e-9);
    }

    @Test
    void serverGroupWithoutServerBlockIsRejected() {
        String bad = "block_name, x1, y1, x2, y2\n"
                + "LONELY_QUEUE000, 0.0, 0.0, 0.0, 5.0\n";
        assertThrows(IllegalArgumentException.class,
                () -> ServersGeometryParser.parse(new StringReader(bad), 0.5));
    }

    @Test
    void withoutGroupColumnEachServerIsItsOwnGroup() {
        List<Server> servers = parse();
        // No group column -> logical group defaults to the server name.
        assertEquals(List.of("CASHIER_1", "CASHIER_2", "PRESENTATION_1", "PRESENTATION_2"),
                servers.stream().map(Server::group).toList());
    }

    @Test
    void explicitGroupColumnMapsServersToSharedGroup() {
        String csv = """
                block_name, x1, y1, x2, y2, group, qdx, qdy
                CASHIER_1_SERVER, 1.0, 19.0, 4.0, 10.0, checkin, 1.0, 0.0
                CASHIER_2_SERVER, 36.0, 19.0, 39.0, 10.0, checkin, 1.0, 0.0
                """;
        List<Server> servers = ServersGeometryParser.parse(new StringReader(csv), 0.5);
        assertEquals(2, servers.size());
        // Two distinct physical servers...
        assertEquals(List.of("CASHIER_1", "CASHIER_2"),
                servers.stream().map(Server::name).toList());
        // ...sharing one logical group.
        assertEquals(List.of("checkin", "checkin"),
                servers.stream().map(Server::group).toList());
    }

    @Test
    void queueDirectionVectorBuildsDirectedQueue() {
        String csv = """
                block_name, x1, y1, x2, y2, group, qdx, qdy
                CASHIER_1_SERVER, 0.0, 0.0, 2.0, 2.0, checkin, 0.0, -1.0
                """;
        Server cashier = ServersGeometryParser.parse(new StringReader(csv), 0.5, 10).get(0);
        assertEquals(ServerType.QUEUE, cashier.type());
        assertTrue(cashier.hasQueue());
        assertEquals(10, cashier.queueLine().maxSlots()); // capacity from maxQueueSlots

        // Service centroid (1,1); queue grows along (0,-1); slot 0 is one
        // slotSpacing (0.5) away from the service position.
        Vec2 slot0 = cashier.queueLine().slotPosition(0);
        assertEquals(1.0, slot0.x(), 1e-9);
        assertEquals(0.5, slot0.y(), 1e-9);
        Vec2 slot1 = cashier.queueLine().slotPosition(1);
        assertEquals(1.0, slot1.x(), 1e-9);
        assertEquals(0.0, slot1.y(), 1e-9);
    }
}
