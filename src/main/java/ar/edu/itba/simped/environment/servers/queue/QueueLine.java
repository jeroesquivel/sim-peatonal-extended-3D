package ar.edu.itba.simped.environment.servers.queue;

import ar.edu.itba.simped.core.Vec2;

/**
 * The queue of a {@code QUEUE} server: a finite set of slots starting at the
 * {@code front} and growing along a fixed {@code direction}, spaced
 * {@code slotSpacing} apart.
 *
 * <p>Slot 0 is the <strong>front</strong> of the queue (closest to the service
 * spot). Two ways to build one:</p>
 * <ul>
 *   <li>{@link #directed(Vec2, Vec2, double, int)} — explicit start, direction
 *       vector and slot count (the input gives the queue's growth direction);</li>
 *   <li>{@link #QueueLine(Vec2, Vec2, double)} — legacy {@code *_QUEUE} segment
 *       (front + back endpoints; direction and slot count are derived).</li>
 * </ul>
 */
public final class QueueLine {

    private final Vec2 front;
    private final Vec2 back;
    private final double slotSpacing;
    private final Vec2 direction;
    private final int slots;

    /**
     * Legacy segment form: slots run from {@code front} to {@code back}, one
     * every {@code slotSpacing}; the count is derived from the segment length.
     */
    public QueueLine(Vec2 front, Vec2 back, double slotSpacing) {
        this(front, requireNonNull(front, back).sub(front), slotSpacing,
                Math.max(1, (int) Math.floor(front.distanceTo(back) / slotSpacing) + 1));
    }

    /**
     * Directed form (the input gives the queue's growth direction): {@code slots}
     * positions starting at {@code front} and stepping {@code slotSpacing} along
     * {@code direction}.
     *
     * @param direction growth direction of the queue (need not be normalised)
     * @param slots     number of slots (queue capacity); clamped to at least 1
     */
    public static QueueLine directed(Vec2 front, Vec2 direction, double slotSpacing, int slots) {
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        if (direction.norm() == 0.0) {
            throw new IllegalArgumentException("queue direction must not be the zero vector");
        }
        return new QueueLine(front, direction, slotSpacing, slots);
    }

    private QueueLine(Vec2 front, Vec2 rawDirection, double slotSpacing, int slots) {
        if (front == null || rawDirection == null) {
            throw new IllegalArgumentException("front/direction must not be null");
        }
        if (!(slotSpacing > 0.0) || Double.isInfinite(slotSpacing)) {
            throw new IllegalArgumentException(
                    "slotSpacing must be positive and finite, got " + slotSpacing);
        }
        double length = rawDirection.norm();
        this.front = front;
        this.slotSpacing = slotSpacing;
        this.direction = length == 0.0 ? new Vec2(0.0, 0.0) : rawDirection.scale(1.0 / length);
        this.slots = Math.max(1, slots);
        // Derived end-point, kept for callers that still read back().
        this.back = front.add(this.direction.scale((this.slots - 1) * slotSpacing));
    }

    private static Vec2 requireNonNull(Vec2 front, Vec2 back) {
        if (front == null || back == null) {
            throw new IllegalArgumentException("front/back must not be null");
        }
        return back;
    }

    /** Position of slot {@code i} (0 = front). */
    public Vec2 slotPosition(int i) {
        if (i < 0 || i >= slots) {
            throw new IndexOutOfBoundsException(
                    "slot " + i + " out of bounds, queue has " + slots + " slots");
        }
        return front.add(direction.scale(i * slotSpacing));
    }

    public int maxSlots() {
        return slots;
    }

    public Vec2 front() {
        return front;
    }

    public Vec2 back() {
        return back;
    }

    public double slotSpacing() {
        return slotSpacing;
    }
}
