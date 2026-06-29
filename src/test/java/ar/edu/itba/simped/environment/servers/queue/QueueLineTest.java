package ar.edu.itba.simped.environment.servers.queue;

import ar.edu.itba.simped.core.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueLineTest {

    @Test
    void slotsAreEvenlySpacedFromFrontToBack() {
        QueueLine ql = new QueueLine(new Vec2(0, 0), new Vec2(0, 10), 1.0);
        assertEquals(11, ql.maxSlots()); // 0..10 inclusive
        assertEquals(0.0, ql.slotPosition(0).y(), 1e-9);
        assertEquals(1.0, ql.slotPosition(1).y(), 1e-9);
        assertEquals(10.0, ql.slotPosition(10).y(), 1e-9);
    }

    @Test
    void outOfRangeSlotThrows() {
        QueueLine ql = new QueueLine(new Vec2(0, 0), new Vec2(0, 10), 1.0);
        assertThrows(IndexOutOfBoundsException.class, () -> ql.slotPosition(11));
        assertThrows(IndexOutOfBoundsException.class, () -> ql.slotPosition(-1));
    }

    @Test
    void invalidSpacingThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueueLine(new Vec2(0, 0), new Vec2(0, 10), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new QueueLine(new Vec2(0, 0), new Vec2(0, 10), -1.0));
    }

    @Test
    void diagonalSegmentSpacingFollowsDirection() {
        QueueLine ql = new QueueLine(new Vec2(0, 0), new Vec2(3, 4), 5.0); // length 5
        assertEquals(2, ql.maxSlots()); // floor(5/5)+1
        Vec2 slot1 = ql.slotPosition(1);
        assertEquals(3.0, slot1.x(), 1e-9);
        assertEquals(4.0, slot1.y(), 1e-9);
    }

    @Test
    void directedQueueGrowsAlongTheGivenVector() {
        // Direction need not be normalised; capacity is given explicitly.
        QueueLine ql = QueueLine.directed(new Vec2(1, 1), new Vec2(0, -2), 0.5, 4);
        assertEquals(4, ql.maxSlots());
        assertEquals(1.0, ql.slotPosition(0).x(), 1e-9);
        assertEquals(1.0, ql.slotPosition(0).y(), 1e-9);   // front
        assertEquals(0.5, ql.slotPosition(1).y(), 1e-9);   // (1,1)+(0,-1)*0.5
        assertEquals(-0.5, ql.slotPosition(3).y(), 1e-9);  // (1,1)+(0,-1)*1.5
    }

    @Test
    void directedQueueRejectsZeroVector() {
        assertThrows(IllegalArgumentException.class,
                () -> QueueLine.directed(new Vec2(0, 0), new Vec2(0, 0), 0.5, 4));
    }
}
