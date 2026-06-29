package ar.edu.itba.simped.environment.servers.geometry;

import ar.edu.itba.simped.core.Vec2;

/**
 * One raw row of {@code SERVERS.csv}: a named rectangular block given by two
 * opposite corners (2D, the {@code z} columns were dropped), plus an optional
 * logical {@code group} and an optional {@code queueDirection} vector. Several
 * blocks sharing a prefix make up one server; several servers sharing a
 * {@code group} make up one delegation target for the StateMachine.
 *
 * <p>{@code group} / {@code queueDirection} are {@code null} when the CSV row
 * omits those columns. {@code queueDirection} is the direction in which the
 * queue grows out of the service point (given on the {@code *_SERVER} row).</p>
 */
public record ServerBlock(String blockName, Vec2 corner1, Vec2 corner2, String group,
                          Vec2 queueDirection) {

    private static final String SERVER_SUFFIX = "_SERVER";
    private static final String QUEUE_MARKER = "_QUEUE";

    public boolean isServerBlock() {
        return blockName.endsWith(SERVER_SUFFIX);
    }

    public boolean isQueueBlock() {
        return blockName.contains(QUEUE_MARKER);
    }

    /**
     * Grouping key shared by all blocks of the same server, e.g. both
     * {@code CASHIER_1_SERVER} and {@code CASHIER_1_QUEUE000} map to
     * {@code CASHIER_1}.
     */
    public String groupKey() {
        if (isServerBlock()) {
            return blockName.substring(0, blockName.length() - SERVER_SUFFIX.length());
        }
        int idx = blockName.indexOf(QUEUE_MARKER);
        if (idx >= 0) {
            return blockName.substring(0, idx);
        }
        return blockName;
    }
}
