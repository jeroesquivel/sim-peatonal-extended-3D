package ar.edu.itba.simped.agent.om;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.NeighborType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.environment.neighbors.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SfmaOperationalModelTest {

    private static final double EPS = 1e-9;

    private static AgentProfile profile() {
        return new AgentProfile(1.0, 1.0, 0.4, 3.0, 1.0, 1.0);
    }

    private static AgentState agent(int id, double x, double y, double vx, double vy, double radius) {
        AgentState state = new AgentState(id, "pedestrian");
        state.setPosition(x, y);
        state.setVelocity(vx, vy);
        state.setRadius(radius);
        state.setProfile(profile());
        state.setState(BehaviorState.WALKING);
        return state;
    }

    @Test
    void integratesTowardFootTargetWithoutNeighbors() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(), 0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.25, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void zeroDtDoesNotMoveOrChangeVelocity() {
        AgentState state = agent(1, 2.0, -1.0, 0.4, -0.2, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(), 0.0);

        assertThat(state.x()).isCloseTo(2.0, within(EPS));
        assertThat(state.y()).isCloseTo(-1.0, within(EPS));
        assertThat(state.vx()).isCloseTo(0.4, within(EPS));
        assertThat(state.vy()).isCloseTo(-0.2, within(EPS));
    }

    @Test
    void clampsVelocityToConfiguredMaxSpeedFactor() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentProfile fastRelaxation = new AgentProfile(2.0, 0.01, 0.4, 3.0, 1.0, 1.0);
        state.setProfile(fastRelaxation);
        SfmaOperationalModel model = new SfmaOperationalModel(1.2, 1.25);

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(), 1.0);

        assertThat(new Vec2(state.vx(), state.vy()).norm()).isCloseTo(2.5, within(EPS));
        assertThat(state.x()).isCloseTo(2.5, within(EPS));
    }

    @Test
    void closeFrontAgentWithoutOverlapChangesDesiredDirection() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, 0.7, 0.0, 0.0, 0.0, 0.2);
        Neighbor neighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                0.7,
                neighborState);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(neighbor), 0.5);

        assertThat(state.vx()).isLessThan(0.5);
        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isLessThan(0.25);
        assertThat(state.y()).isGreaterThan(0.0);
    }

    @Test
    void ignoresNeighborsOutsideInteractionRadius() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, 10.0, 0.0, 0.0, 0.0, 0.2);
        Neighbor farNeighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                10.0,
                neighborState);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(farNeighbor), 0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.25, within(EPS));
    }

    @Test
    void predictedHeadOnCollisionChangesDesiredDirectionBeforeClearanceIsSmall() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, 2.0, 0.0, -1.0, 0.0, 0.2);
        Neighbor neighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                2.0,
                neighborState);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(neighbor), 0.5);

        assertThat(state.vx()).isLessThan(0.5);
        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isLessThan(0.25);
        assertThat(state.y()).isGreaterThan(0.0);
    }

    @Test
    void predictedDistantCrossingWithoutCollisionDoesNotChangeDesiredDirection() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, 2.0, 2.0, -1.0, 0.0, 0.2);
        Neighbor neighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                Math.sqrt(8.0),
                neighborState);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(neighbor), 0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.25, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void predictedHeadOnCollisionOutsideTimeHorizonDoesNotChangeDesiredDirection() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, 4.0, 0.0, -1.0, 0.0, 0.2);
        Neighbor neighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                4.0,
                neighborState);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(neighbor), 0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.25, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void agentBehindDoesNotChangeDesiredDirection() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, -0.5, 0.0, 0.0, 0.0, 0.2);
        Neighbor neighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                0.5,
                neighborState);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(neighbor), 0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.25, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void frontAgentMovingAwayDoesNotChangeDesiredDirection() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, 0.7, 0.0, 2.0, 0.0, 0.2);
        Neighbor neighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                0.7,
                neighborState);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.WALKING, List.of(neighbor), 0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.25, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void wallNearWithoutOverlapChangesDesiredDirection() {
        AgentState state = agent(1, 9.5, 0.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.5, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(20.0, 0.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isLessThan(0.5);
        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isLessThan(9.75);
        assertThat(state.y()).isGreaterThan(0.0);
    }

    @Test
    void wallOutsideAvoidanceClearanceAndFuturePathDoesNotChangeDesiredDirection() {
        AgentState state = agent(1, 9.3, 0.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.7, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(0.0, 0.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isCloseTo(-0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(9.05, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void predictedWallCollisionChangesDirectionBeforeCurrentClearanceIsSmall() {
        AgentState state = agent(1, 8.7, 0.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 1.3, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(20.0, 0.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isLessThan(0.5);
        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isLessThan(8.95);
        assertThat(state.y()).isGreaterThan(0.0);
    }

    @Test
    void predictedEndpointPassChangesDirectionBeforeCurrentClearanceIsSmall() {
        AgentState state = agent(1, 8.7, 5.3, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, 0.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 1.4, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(20.0, 5.3),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isLessThan(0.5);
        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isLessThan(8.95);
        assertThat(state.y()).isGreaterThan(5.3);
    }

    @Test
    void predictedWallAvoidanceIgnoresParallelDistantTrajectory() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(0.0, 5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 5.0, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(10.0, 0.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.25, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void agentAndWallAvoidanceCombineWithoutSteeringIntoTheWall() {
        AgentState state = agent(1, 9.65, 0.0, 0.0, 0.0, 0.2);
        AgentState neighborState = agent(2, 9.65, 0.7, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor neighbor = new Neighbor(
                neighborState.id(),
                NeighborType.AGENT,
                0.7,
                neighborState);
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.35, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(9.65, 10.0),
                BehaviorState.WALKING,
                List.of(neighbor, wallNeighbor),
                0.5);

        assertThat(state.vx()).isLessThan(0.0);
        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isLessThan(9.65);
        assertThat(state.y()).isGreaterThan(0.0);
    }

    @Test
    void upperWallEndpointSteersOutwardAroundTheCorner() {
        AgentState state = agent(1, 9.75, 5.30, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, 0.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.39, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(12.0, 4.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isGreaterThan(0.0);
        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isGreaterThan(9.75);
        assertThat(state.y()).isGreaterThan(5.30);
    }

    @Test
    void lowerWallEndpointSteersOutwardAroundTheCorner() {
        AgentState state = agent(1, 9.75, -0.30, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, 0.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.39, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(12.0, 1.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isGreaterThan(0.0);
        assertThat(state.vy()).isLessThan(0.0);
        assertThat(state.x()).isGreaterThan(9.75);
        assertThat(state.y()).isLessThan(-0.30);
    }

    @Test
    void farEndpointDoesNotChangeDesiredDirection() {
        AgentState state = agent(1, 9.3, 5.8, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, 0.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.99, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(20.0, 5.8),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(9.55, within(EPS));
        assertThat(state.y()).isCloseTo(5.8, within(EPS));
    }

    @Test
    void adjacentWallEndpointsCombineWithoutSteeringIntoTheCorner() {
        AgentState state = agent(1, 9.75, 5.25, 0.0, 0.0, 0.2);
        Wall verticalWall = new Wall(new Vec2(10.0, 0.0), new Vec2(10.0, 5.0));
        Wall horizontalWall = new Wall(new Vec2(10.0, 5.0), new Vec2(15.0, 5.0));
        List<Neighbor> wallNeighbors = List.of(
                new Neighbor(0, NeighborType.WALL, 0.35, null),
                new Neighbor(1, NeighborType.WALL, 0.35, null));
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(verticalWall, horizontalWall));

        model.integrate(
                state,
                new Vec2(11.0, 4.5),
                BehaviorState.WALKING,
                wallNeighbors,
                0.5);

        assertThat(state.vy()).isGreaterThan(0.0);
        assertThat(state.x()).isGreaterThan(9.75);
        assertThat(state.y()).isGreaterThan(5.25);
    }

    @Test
    void rightHorizontalEndpointUsesLateralBypassWhenOutwardTangentOpposesTarget() {
        AgentState state = agent(2, 24.25, 10.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(17.0, 10.0), new Vec2(24.0, 10.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.25, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall), 0.2, 1.5);

        model.integrate(
                state,
                new Vec2(7.0, 10.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isLessThan(0.0);
        assertThat(state.vy()).isLessThan(-0.02);
        assertThat(state.x()).isLessThan(24.25);
        assertThat(state.y()).isLessThan(10.0);
    }

    @Test
    void leftHorizontalEndpointUsesLateralBypassWhenOutwardTangentOpposesTarget() {
        AgentState state = agent(2, 16.75, 10.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(17.0, 10.0), new Vec2(24.0, 10.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.25, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall), 0.2, 1.5);

        model.integrate(
                state,
                new Vec2(27.0, 10.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isGreaterThan(0.0);
        assertThat(state.vy()).isGreaterThan(0.02);
        assertThat(state.x()).isGreaterThan(16.75);
        assertThat(state.y()).isGreaterThan(10.0);
    }

    @Test
    void symmetricEndpointBypassUsesAgentIdAsStableTieBreaker() {
        AgentState evenAgent = agent(2, 24.25, 10.0, 0.0, 0.0, 0.2);
        AgentState oddAgent = agent(3, 24.25, 10.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(17.0, 10.0), new Vec2(24.0, 10.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.25, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall), 0.2, 1.5);

        model.integrate(
                evenAgent,
                new Vec2(7.0, 10.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);
        model.integrate(
                oddAgent,
                new Vec2(7.0, 10.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.5);

        assertThat(evenAgent.vy()).isLessThan(-0.02);
        assertThat(oddAgent.vy()).isGreaterThan(0.02);
    }

    @Test
    void queueingIgnoresEndpointAvoidanceWithoutContact() {
        AgentState state = agent(1, 9.75, 5.30, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, 0.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.39, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(12.0, 4.0),
                BehaviorState.QUEUEING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isGreaterThan(0.0);
        assertThat(state.vy()).isLessThan(0.0);
        assertThat(state.x()).isGreaterThan(9.75);
        assertThat(state.y()).isLessThan(5.30);
    }

    @Test
    void queueingIgnoresPredictedWallAvoidanceWithoutContact() {
        AgentState state = agent(1, 8.7, 0.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 1.3, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall));

        model.integrate(
                state,
                new Vec2(20.0, 0.0),
                BehaviorState.QUEUEING,
                List.of(wallNeighbor),
                0.5);

        assertThat(state.vx()).isCloseTo(0.55, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(8.975, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void wallContactForceIsProportionalToOverlap() {
        AgentState state = agent(1, 9.9, 0.0, 0.0, 0.0, 0.2);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.5, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall), 0.0, 10.0, 4.0);

        model.integrate(
                state,
                new Vec2(9.9, 0.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                1e-3);

        assertThat(state.vx()).isCloseTo(-4.0e-4, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(9.8999996, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void wallContactForcesFromACornerAreSummed() {
        AgentState state = agent(1, 9.9, 9.9, 0.0, 0.0, 0.2);
        Wall verticalWall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 15.0));
        Wall horizontalWall = new Wall(new Vec2(-5.0, 10.0), new Vec2(15.0, 10.0));
        List<Neighbor> wallNeighbors = List.of(
                new Neighbor(0, NeighborType.WALL, 0.1, null),
                new Neighbor(1, NeighborType.WALL, 0.1, null));
        SfmaOperationalModel model = new SfmaOperationalModel(
                List.of(verticalWall, horizontalWall), 0.0, 10.0, 4.0);

        model.integrate(state, new Vec2(9.9, 9.9), BehaviorState.WALKING, wallNeighbors, 1e-3);

        assertThat(state.vx()).isCloseTo(-4.0e-4, within(EPS));
        assertThat(state.vy()).isCloseTo(-4.0e-4, within(EPS));
        assertThat(state.x()).isCloseTo(9.8999996, within(EPS));
        assertThat(state.y()).isCloseTo(9.8999996, within(EPS));
    }

    @Test
    void hardContactUsesProfessorSuggestedKnAtOneMillisecondDt() {
        AgentState state = agent(1, 9.99, 0.0, 0.0, 0.0, 0.02);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.01, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall), 0.0, 1.5, 1.0e4);

        model.integrate(
                state,
                new Vec2(9.99, 0.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                1e-3);

        assertThat(state.vx()).isCloseTo(-0.1, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(9.9899, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void hardContactUsesTheProvidedDtWithoutInternalSubsteps() {
        AgentState state = agent(1, 9.99, 0.0, 0.0, 0.0, 0.02);
        Wall wall = new Wall(new Vec2(10.0, -5.0), new Vec2(10.0, 5.0));
        Neighbor wallNeighbor = new Neighbor(0, NeighborType.WALL, 0.01, null);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(wall), 0.0, 10.0, 1.0e4);

        model.integrate(
                state,
                new Vec2(9.99, 0.0),
                BehaviorState.WALKING,
                List.of(wallNeighbor),
                0.01);

        assertThat(state.vx()).isCloseTo(-1.0, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(9.98, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void hardContactRemainsActiveBetweenQueueingAgents() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState queueNeighbor = agent(2, 0.3, 0.0, 0.0, 0.0, 0.2);
        queueNeighbor.setState(BehaviorState.QUEUEING);
        Neighbor neighbor = new Neighbor(
                queueNeighbor.id(),
                NeighborType.AGENT,
                0.3,
                queueNeighbor);
        SfmaOperationalModel model = new SfmaOperationalModel(List.of(), 0.0, 10.0, 100.0);

        model.integrate(
                state,
                new Vec2(10.0, 0.0),
                BehaviorState.QUEUEING,
                List.of(neighbor),
                0.01);

        assertThat(state.vx()).isLessThan(0.0);
        assertThat(state.x()).isLessThan(0.0);
    }

    @Test
    void targetAtCurrentPositionDoesNotProduceNonFiniteValues() {
        AgentState state = agent(1, 1.0, 2.0, 1.0, 0.0, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(1.0, 2.0), BehaviorState.WALKING, List.of(), 0.5);

        assertThat(state.x()).isFinite();
        assertThat(state.y()).isFinite();
        assertThat(state.vx()).isFinite();
        assertThat(state.vy()).isFinite();
        assertThat(state.vx()).isCloseTo(0.5, within(EPS));
    }

    @Test
    void queueingMovesTowardAssignedSlot() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.QUEUEING, List.of(), 0.5);

        assertThat(state.vx()).isCloseTo(0.55, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.275, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void queueingSnapsToAssignedSlotAndStopsWithinFiveCentimeters() {
        AgentState state = agent(1, 0.03, 0.0, 1.0, 0.0, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, Vec2.ZERO, BehaviorState.QUEUEING, List.of(), 0.5);

        assertThat(state.x()).isCloseTo(0.0, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
        assertThat(state.vx()).isCloseTo(0.0, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.radius()).isCloseTo(0.2, within(EPS));
    }

    @Test
    void queueingSnapsWhenAWholeIntegrationStepCrossesTheSlot() {
        AgentState state = agent(1, 0.06, 0.0, -1.0, 0.0, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, Vec2.ZERO, BehaviorState.QUEUEING, List.of(), 0.2);

        assertThat(state.x()).isCloseTo(0.0, within(EPS));
        assertThat(state.y()).isCloseTo(0.0, within(EPS));
        assertThat(state.vx()).isCloseTo(0.0, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
    }

    @Test
    void queueingIgnoresDirectionalAvoidanceFromOtherQueueingAgents() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        AgentState queueNeighbor = agent(2, 0.7, 0.0, 0.0, 0.0, 0.2);
        queueNeighbor.setState(BehaviorState.QUEUEING);
        Neighbor neighbor = new Neighbor(
                queueNeighbor.id(),
                NeighborType.AGENT,
                0.7,
                queueNeighbor);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(
                state,
                new Vec2(10.0, 0.0),
                BehaviorState.QUEUEING,
                List.of(neighbor),
                0.5);

        assertThat(state.vx()).isCloseTo(0.55, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(0.275, within(EPS));
    }

    @Test
    void approachingAppliesSfmaBehaviorMultipliersInsideTheOm() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        model.integrate(state, new Vec2(10.0, 0.0), BehaviorState.APPROACHING, List.of(), 1.0);

        double expectedVelocity = 0.65 / 1.35;
        assertThat(state.vx()).isCloseTo(expectedVelocity, within(EPS));
        assertThat(state.vy()).isCloseTo(0.0, within(EPS));
        assertThat(state.x()).isCloseTo(expectedVelocity, within(EPS));
    }

    @Test
    void neighborQueryRadiusUsesEffectiveRmaxForBehavior() {
        AgentState state = agent(1, 0.0, 0.0, 0.0, 0.0, 0.2);
        SfmaOperationalModel model = new SfmaOperationalModel();

        double radius = model.neighborQueryRadius(state, BehaviorState.APPROACHING);

        assertThat(radius).isCloseTo(3.3, within(EPS));
    }
}
