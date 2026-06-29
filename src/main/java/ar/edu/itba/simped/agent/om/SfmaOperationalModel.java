package ar.edu.itba.simped.agent.om;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.NeighborType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.OperationalModel;
import ar.edu.itba.simped.environment.neighbors.Wall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Operational Model basado en Social Force Model con driving force y contacto.
 * Implementacion del Grupo 4 adaptada al port I16q/I15.
 *
 * <p>Lee el {@link AgentProfile} base de {@code state.profile()} y deriva los
 * parametros efectivos segun el {@link BehaviorState}. La StateMachine decide
 * el estado; este modelo decide como ese estado impacta en la fisica.</p>
 */
public final class SfmaOperationalModel implements OperationalModel {

    private static final double QUEUEING_DESIRED_SPEED = 0.55;
    private static final double QUEUE_SNAP_DISTANCE = 0.05;
    private static final double GEOMETRY_EPS = 1e-9;
    private static final double DEFAULT_NORMAL_STIFFNESS = 1.0e4;
    private static final double MAX_AVOIDANCE_ANGLE = Math.toRadians(60.0);
    private static final double WALL_TANGENTIAL_WEIGHT = 0.75;
    private static final double WALL_ENDPOINT_TANGENTIAL_WEIGHT = 1.0;
    private static final double WALL_ENDPOINT_LATERAL_WEIGHT = 0.35;
    private static final double MAX_WALL_AVOIDANCE_NORM = 1.5;
    private static final double PREDICTIVE_AVOIDANCE_TIME_HORIZON = 1.5;
    private static final double WALL_ENDPOINT_REGION_MARGIN = 0.05;
    private static final int MAX_AGENT_AVOIDANCE_NEIGHBORS = 2;
    private static final int MAX_WALL_AVOIDANCE_FEATURES = 3;

    private final double avoidanceWeight;
    private final double maxSpeedFactor;
    private final double normalStiffness;
    private final List<Wall> walls;

    public SfmaOperationalModel(
            List<Wall> walls,
            double avoidanceWeight,
            double maxSpeedFactor,
            double normalStiffness
    ) {
        if (walls == null) {
            throw new IllegalArgumentException("Walls list cannot be null");
        }
        if (!(avoidanceWeight >= 0.0) || Double.isInfinite(avoidanceWeight)) {
            throw new IllegalArgumentException("avoidanceWeight must be non-negative and finite");
        }
        if (!(normalStiffness >= 0.0) || Double.isInfinite(normalStiffness)) {
            throw new IllegalArgumentException("normalStiffness must be non-negative and finite");
        }
        this.walls = List.copyOf(walls);
        this.avoidanceWeight = avoidanceWeight;
        this.maxSpeedFactor = maxSpeedFactor;
        this.normalStiffness = normalStiffness;
    }

    public SfmaOperationalModel(List<Wall> walls, double avoidanceWeight, double maxSpeedFactor) {
        this(walls, avoidanceWeight, maxSpeedFactor, DEFAULT_NORMAL_STIFFNESS);
    }

    public SfmaOperationalModel(double avoidanceWeight, double maxSpeedFactor) {
        this(List.of(), avoidanceWeight, maxSpeedFactor);
    }

    public SfmaOperationalModel(List<Wall> walls) {
        this(walls, 1.2, 1.5);
    }

    public SfmaOperationalModel() {
        this(List.of());
    }

    @Override
    public double neighborQueryRadius(AgentState state, BehaviorState behavior) {
        return effectiveProfileFor(behavior, state.profile()).rmax();
    }

    @Override
    public void integrate(
            AgentState state,
            Vec2 footTarget,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            double dt
    ) {
        if (dt <= 0.0) {
            return;
        }

        if (behavior == BehaviorState.QUEUEING
                && isWithinQueueSnapDistance(state, footTarget)) {
            state.setPosition(footTarget.x(), footTarget.y());
            state.setVelocity(0.0, 0.0);
            return;
        }

        AgentProfile profile = effectiveProfileFor(behavior, state.profile());
        Vec2 position = new Vec2(state.x(), state.y());
        Vec2 velocity = new Vec2(state.vx(), state.vy());

        Step step = integrateStep(state, footTarget, behavior, neighbors, profile, position, velocity, dt);
        Vec2 nextPosition = step.position();
        Vec2 nextVelocity = step.velocity();

        if (behavior == BehaviorState.QUEUEING
                && queueStepReachesSlot(position, nextPosition, footTarget)) {
            nextPosition = footTarget;
            nextVelocity = Vec2.ZERO;
        }

        position = nextPosition;
        velocity = nextVelocity;

        state.setPosition(position.x(), position.y());
        state.setVelocity(velocity.x(), velocity.y());
    }

    private Step integrateStep(
            AgentState state,
            Vec2 footTarget,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            AgentProfile profile,
            Vec2 position,
            Vec2 velocity,
            double dt
    ) {
        Vec2 targetDirection = footTarget.sub(position).normalized();
        Vec2 desiredDirection = computeDesiredDirection(
                state, targetDirection, behavior, neighbors, profile, position, velocity);
        Vec2 desiredVelocity = desiredDirection.scale(profile.vd());
        Vec2 drivingAcceleration = desiredVelocity.sub(velocity).scale(1.0 / profile.tau());
        Vec2 contactAcceleration = computeContactAcceleration(
                state, position, targetDirection, neighbors);
        Vec2 acceleration = drivingAcceleration.add(contactAcceleration);
        Vec2 nextVelocity = velocity.add(acceleration.scale(dt))
                .clampNorm(profile.vd() * maxSpeedFactor);
        Vec2 nextPosition = position.add(nextVelocity.scale(dt));

        return new Step(nextPosition, nextVelocity);
    }

    private static boolean isWithinQueueSnapDistance(AgentState state, Vec2 footTarget) {
        return footTarget != null
                && new Vec2(state.x(), state.y()).distanceTo(footTarget) <= QUEUE_SNAP_DISTANCE;
    }

    private static boolean queueStepReachesSlot(Vec2 from, Vec2 to, Vec2 slot) {
        Vec2 step = to.sub(from);
        double stepLengthSquared = step.dot(step);
        if (stepLengthSquared < 1e-12) {
            return to.distanceTo(slot) <= QUEUE_SNAP_DISTANCE;
        }

        double projection = slot.sub(from).dot(step) / stepLengthSquared;
        double clampedProjection = Math.max(0.0, Math.min(1.0, projection));
        Vec2 closestPoint = from.add(step.scale(clampedProjection));
        return closestPoint.distanceTo(slot) <= QUEUE_SNAP_DISTANCE;
    }

    private static AgentProfile effectiveProfileFor(BehaviorState behavior, AgentProfile baseProfile) {
        BehaviorState safeBehavior = behavior != null ? behavior : BehaviorState.IDLE;
        return switch (safeBehavior) {
            case WALKING -> baseProfile;
            case APPROACHING -> scaledProfile(baseProfile, 0.65, 1.35, 1.15, 1.10);
            case ARRIVED -> scaledProfile(baseProfile, 0.25, 1.60, 1.20, 1.10);
            case OCCUPYING -> scaledProfile(baseProfile, 0.0, 0.35, 1.25, 1.10);
            case LEAVING -> scaledProfile(baseProfile, 1.10, 0.90, 1.00, 1.00);
            case QUEUEING -> new AgentProfile(
                    QUEUEING_DESIRED_SPEED,
                    Math.max(1e-6, baseProfile.tau() * 0.50),
                    baseProfile.rmin() * 1.20,
                    baseProfile.rmax() * 1.10,
                    baseProfile.beta(),
                    baseProfile.ve());
            case IDLE, DEAD -> scaledProfile(baseProfile, 0.0, 0.50, 1.00, 1.00);
        };
    }

    private static AgentProfile scaledProfile(
            AgentProfile baseProfile,
            double vdFactor,
            double tauFactor,
            double rminFactor,
            double rmaxFactor
    ) {
        return new AgentProfile(
                baseProfile.vd() * vdFactor,
                Math.max(1e-6, baseProfile.tau() * tauFactor),
                baseProfile.rmin() * rminFactor,
                baseProfile.rmax() * rmaxFactor,
                baseProfile.beta(),
                baseProfile.ve()
        );
    }

    private Vec2 computeDesiredDirection(
            AgentState state,
            Vec2 targetDirection,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            AgentProfile profile,
            Vec2 position,
            Vec2 velocity
    ) {
        if (targetDirection.equals(Vec2.ZERO)
                || avoidanceWeight == 0.0
                || !usesDirectionalAvoidance(behavior)) {
            return targetDirection;
        }

        Vec2 avoidance = computeAgentAvoidance(
                state, targetDirection, neighbors, profile, position, velocity)
                .add(computeWallAvoidance(state, targetDirection, neighbors, profile, position, velocity));
        if (avoidance.equals(Vec2.ZERO)) {
            return targetDirection;
        }

        Vec2 desired = targetDirection.add(avoidance.scale(avoidanceWeight));
        if (desired.norm() <= GEOMETRY_EPS) {
            return targetDirection;
        }
        return limitDirection(targetDirection, desired.normalized());
    }

    private static boolean usesDirectionalAvoidance(BehaviorState behavior) {
        BehaviorState safeBehavior = behavior != null ? behavior : BehaviorState.IDLE;
        return safeBehavior == BehaviorState.WALKING
                || safeBehavior == BehaviorState.APPROACHING
                || safeBehavior == BehaviorState.LEAVING;
    }

    private Vec2 computeAgentAvoidance(
            AgentState state,
            Vec2 targetDirection,
            List<Neighbor> neighbors,
            AgentProfile profile,
            Vec2 position,
            Vec2 velocity
    ) {
        List<AgentAvoidanceCandidate> candidates = new ArrayList<>();
        Vec2 desiredVelocity = targetDirection.scale(profile.vd());
        for (Neighbor neighbor : neighbors) {
            if (neighbor.type() != NeighborType.AGENT || neighbor.agent() == null) {
                continue;
            }

            AgentState other = neighbor.agent();
            Vec2 otherPosition = new Vec2(other.x(), other.y());
            Vec2 toOther = otherPosition.sub(position);
            double centerDistance = toOther.norm();
            if (centerDistance <= GEOMETRY_EPS) {
                continue;
            }

            Vec2 toOtherDirection = toOther.scale(1.0 / centerDistance);
            if (targetDirection.dot(toOtherDirection) <= 0.0) {
                continue;
            }

            Vec2 otherVelocity = new Vec2(other.vx(), other.vy());
            double closingSpeed = desiredVelocity.sub(otherVelocity).dot(toOtherDirection);
            if (closingSpeed <= 0.0) {
                continue;
            }

            double combinedRadius = state.radius() + other.radius();
            double clearance = centerDistance - combinedRadius;
            double weight = Math.max(
                    compactAvoidanceWeight(clearance, profile.rmin()),
                    predictedAgentAvoidanceWeight(
                            toOther, otherVelocity.sub(desiredVelocity), combinedRadius, profile.rmin()));
            if (weight <= 0.0) {
                continue;
            }

            Vec2 lateral = lateralAwayDirection(targetDirection, toOtherDirection);
            candidates.add(new AgentAvoidanceCandidate(centerDistance, lateral.scale(weight)));
        }

        candidates.sort(Comparator.comparingDouble(AgentAvoidanceCandidate::distance));
        Vec2 sum = Vec2.ZERO;
        int limit = Math.min(MAX_AGENT_AVOIDANCE_NEIGHBORS, candidates.size());
        for (int i = 0; i < limit; i++) {
            sum = sum.add(candidates.get(i).contribution());
        }
        return sum;
    }

    private Vec2 computeWallAvoidance(
            AgentState state,
            Vec2 targetDirection,
            List<Neighbor> neighbors,
            AgentProfile profile,
            Vec2 position,
            Vec2 velocity
    ) {
        List<WallAvoidanceCandidate> candidates = new ArrayList<>();
        for (Neighbor neighbor : neighbors) {
            if (neighbor.type() != NeighborType.WALL) {
                continue;
            }

            Wall wall = wallFor(neighbor.id());
            WallAvoidanceCandidate segmentCandidate = wallSegmentAvoidanceCandidate(
                    state, targetDirection, profile, position, wall);
            if (segmentCandidate != null) {
                candidates.add(segmentCandidate);
            }
            WallAvoidanceCandidate predictedCandidate = predictedWallAvoidanceCandidate(
                    state, targetDirection, profile, position, wall);
            if (predictedCandidate != null) {
                candidates.add(predictedCandidate);
            }
            candidates.addAll(wallEndpointAvoidanceCandidates(
                    state, targetDirection, profile, position, velocity, wall));
        }

        candidates.sort(Comparator.comparingDouble(WallAvoidanceCandidate::clearance));
        Vec2 sum = Vec2.ZERO;
        int limit = Math.min(MAX_WALL_AVOIDANCE_FEATURES, candidates.size());
        for (int i = 0; i < limit; i++) {
            sum = sum.add(candidates.get(i).contribution());
        }
        return clampAvoidanceNorm(sum, MAX_WALL_AVOIDANCE_NORM);
    }

    private WallAvoidanceCandidate wallSegmentAvoidanceCandidate(
            AgentState state,
            Vec2 targetDirection,
            AgentProfile profile,
            Vec2 position,
            Wall wall
    ) {
        double projection = projectionParameterOnWall(wall, position);
        if (projection < 0.0 || projection > 1.0) {
            return null;
        }

        double wallDistance = wall.distanceTo(position);
        double clearance = wallDistance - state.radius();
        double weight = compactAvoidanceWeight(clearance, profile.rmin());
        if (weight <= 0.0) {
            return null;
        }

        Vec2 away = wallAwayDirection(position, targetDirection, wall);
        double intoWall = targetDirection.dot(away.scale(-1.0));
        boolean movingTowardWall = intoWall > 0.0;
        boolean laterallyClose = clearance <= profile.rmin() * 0.5;
        if (!movingTowardWall && !laterallyClose) {
            return null;
        }

        Vec2 contribution = wallFeatureContribution(
                away, wall, targetDirection, weight, movingTowardWall, 0.0);
        return new WallAvoidanceCandidate(clearance, contribution);
    }

    private WallAvoidanceCandidate predictedWallAvoidanceCandidate(
            AgentState state,
            Vec2 targetDirection,
            AgentProfile profile,
            Vec2 position,
            Wall wall
    ) {
        Vec2 displacement = targetDirection.scale(
                profile.vd() * PREDICTIVE_AVOIDANCE_TIME_HORIZON);
        if (displacement.norm() <= GEOMETRY_EPS) {
            return null;
        }

        ClosestSegmentPoints closest = closestPointsBetweenSegments(
                position, position.add(displacement), wall.p1(), wall.p2());
        if (closest.firstParameter() <= GEOMETRY_EPS) {
            return null;
        }

        double clearance = closest.firstPoint().distanceTo(closest.secondPoint()) - state.radius();
        double weight = timeWeightedAvoidanceWeight(
                clearance, profile.rmin(), closest.firstParameter());
        if (weight <= 0.0) {
            return null;
        }

        Vec2 away = closest.firstPoint().sub(closest.secondPoint()).normalized();
        if (away.equals(Vec2.ZERO)) {
            away = wallAwayDirection(position, targetDirection, wall);
        }
        double intoWall = Math.max(0.0, targetDirection.dot(away.scale(-1.0)));
        boolean nearEndpoint = closest.secondParameter() <= WALL_ENDPOINT_REGION_MARGIN
                || closest.secondParameter() >= 1.0 - WALL_ENDPOINT_REGION_MARGIN;
        Vec2 contribution = wallFeatureContribution(
                away, wall, targetDirection, weight, intoWall > 0.0 || nearEndpoint, nearEndpoint ? 0.5 : 0.0);
        return new WallAvoidanceCandidate(clearance, contribution);
    }

    private List<WallAvoidanceCandidate> wallEndpointAvoidanceCandidates(
            AgentState state,
            Vec2 targetDirection,
            AgentProfile profile,
            Vec2 position,
            Vec2 velocity,
            Wall wall
    ) {
        List<WallAvoidanceCandidate> candidates = new ArrayList<>(2);
        WallAvoidanceCandidate p1Candidate = wallEndpointAvoidanceCandidate(
                state, targetDirection, profile, position, velocity, wall, wall.p1(), false);
        if (p1Candidate != null) {
            candidates.add(p1Candidate);
        }

        if (wall.p1().distanceTo(wall.p2()) <= GEOMETRY_EPS) {
            return candidates;
        }

        WallAvoidanceCandidate p2Candidate = wallEndpointAvoidanceCandidate(
                state, targetDirection, profile, position, velocity, wall, wall.p2(), true);
        if (p2Candidate != null) {
            candidates.add(p2Candidate);
        }
        return candidates;
    }

    private WallAvoidanceCandidate wallEndpointAvoidanceCandidate(
            AgentState state,
            Vec2 targetDirection,
            AgentProfile profile,
            Vec2 position,
            Vec2 velocity,
            Wall wall,
            Vec2 endpoint,
            boolean endpointIsP2
    ) {
        Vec2 toEndpoint = endpoint.sub(position);
        double endpointDistance = toEndpoint.norm();
        double clearance = endpointDistance - state.radius();
        double weight = compactAvoidanceWeight(clearance, profile.rmin());
        if (weight <= 0.0) {
            return null;
        }

        Vec2 toEndpointDirection = endpointDistance > GEOMETRY_EPS
                ? toEndpoint.scale(1.0 / endpointDistance)
                : targetDirection.scale(-1.0);
        double forwardness = targetDirection.dot(toEndpointDirection);
        boolean aheadOrLateral = forwardness >= -0.15;
        boolean nearlyTouching = clearance <= profile.rmin() * 0.25;
        if (!aheadOrLateral && !nearlyTouching) {
            return null;
        }

        Vec2 away = endpointDistance > GEOMETRY_EPS
                ? position.sub(endpoint).scale(1.0 / endpointDistance)
                : targetDirection.scale(-1.0);
        Vec2 tangent = endpointBypassTangent(wall, endpointIsP2);
        Vec2 lateral = endpointLateralBypassDirection(
                state, targetDirection, position, velocity, wall, endpoint, toEndpointDirection);
        Vec2 contribution = away.scale(weight)
                .add(tangent.scale(weight * WALL_ENDPOINT_TANGENTIAL_WEIGHT))
                .add(lateral.scale(weight * WALL_ENDPOINT_LATERAL_WEIGHT));
        return new WallAvoidanceCandidate(clearance, contribution);
    }

    private static Vec2 endpointBypassTangent(Wall wall, boolean endpointIsP2) {
        Vec2 tangent = wall.p2().sub(wall.p1()).normalized();
        if (tangent.equals(Vec2.ZERO)) {
            return Vec2.ZERO;
        }
        return endpointIsP2 ? tangent : tangent.scale(-1.0);
    }

    private static Vec2 endpointLateralBypassDirection(
            AgentState state,
            Vec2 targetDirection,
            Vec2 position,
            Vec2 velocity,
            Wall wall,
            Vec2 endpoint,
            Vec2 toEndpointDirection
    ) {
        Vec2 left = new Vec2(-targetDirection.y(), targetDirection.x());
        double obstacleSide = cross(targetDirection, toEndpointDirection);
        if (Math.abs(obstacleSide) > GEOMETRY_EPS) {
            return obstacleSide > 0.0 ? left.scale(-1.0) : left;
        }

        Vec2 tangent = wall.p2().sub(wall.p1()).normalized();
        if (!tangent.equals(Vec2.ZERO)) {
            Vec2 normal = new Vec2(-tangent.y(), tangent.x());
            double normalSide = position.sub(endpoint).dot(normal);
            if (Math.abs(normalSide) > GEOMETRY_EPS) {
                return normalSide > 0.0 ? normal : normal.scale(-1.0);
            }
        }

        double velocitySide = velocity.dot(left);
        if (Math.abs(velocitySide) > GEOMETRY_EPS) {
            return velocitySide > 0.0 ? left : left.scale(-1.0);
        }
        return state.id() % 2 == 0 ? left : left.scale(-1.0);
    }

    private static double projectionParameterOnWall(Wall wall, Vec2 position) {
        Vec2 segment = wall.p2().sub(wall.p1());
        double lengthSquared = segment.dot(segment);
        if (lengthSquared <= GEOMETRY_EPS * GEOMETRY_EPS) {
            return 0.5;
        }
        return position.sub(wall.p1()).dot(segment) / lengthSquared;
    }

    private static Vec2 clampAvoidanceNorm(Vec2 avoidance, double maxNorm) {
        if (avoidance.norm() <= maxNorm) {
            return avoidance;
        }
        return avoidance.normalized().scale(maxNorm);
    }

    private static double compactAvoidanceWeight(double clearance, double rmin) {
        if (rmin <= GEOMETRY_EPS || clearance > rmin) {
            return 0.0;
        }
        double normalizedClearance = Math.max(0.0, clearance) / rmin;
        double closeness = 1.0 - normalizedClearance;
        return closeness * closeness;
    }

    private static double predictedAgentAvoidanceWeight(
            Vec2 relativePosition,
            Vec2 relativeVelocity,
            double combinedRadius,
            double rmin
    ) {
        double relativeSpeedSquared = relativeVelocity.dot(relativeVelocity);
        if (relativeSpeedSquared <= GEOMETRY_EPS * GEOMETRY_EPS) {
            return 0.0;
        }

        double timeToClosestApproach = -relativePosition.dot(relativeVelocity) / relativeSpeedSquared;
        if (timeToClosestApproach <= 0.0
                || timeToClosestApproach > PREDICTIVE_AVOIDANCE_TIME_HORIZON) {
            return 0.0;
        }

        Vec2 closestRelativePosition = relativePosition.add(
                relativeVelocity.scale(timeToClosestApproach));
        double predictedClearance = closestRelativePosition.norm() - combinedRadius;
        return timeWeightedAvoidanceWeight(
                predictedClearance, rmin, timeToClosestApproach / PREDICTIVE_AVOIDANCE_TIME_HORIZON);
    }

    private static double timeWeightedAvoidanceWeight(double clearance, double rmin, double timeFraction) {
        double spatialWeight = compactAvoidanceWeight(clearance, rmin);
        if (spatialWeight <= 0.0) {
            return 0.0;
        }
        double clampedTimeFraction = Math.max(0.0, Math.min(1.0, timeFraction));
        return spatialWeight * (1.0 - clampedTimeFraction);
    }

    private static Vec2 lateralAwayDirection(Vec2 targetDirection, Vec2 toObstacleDirection) {
        Vec2 left = new Vec2(-targetDirection.y(), targetDirection.x());
        double side = cross(targetDirection, toObstacleDirection);
        if (side > GEOMETRY_EPS) {
            return left.scale(-1.0);
        }
        return left;
    }

    private static Vec2 wallTangentAlongTarget(Wall wall, Vec2 targetDirection) {
        Vec2 tangent = wall.p2().sub(wall.p1()).normalized();
        if (tangent.equals(Vec2.ZERO)) {
            return Vec2.ZERO;
        }
        return tangent.dot(targetDirection) >= 0.0 ? tangent : tangent.scale(-1.0);
    }

    private static Vec2 wallFeatureContribution(
            Vec2 away,
            Wall wall,
            Vec2 targetDirection,
            double weight,
            boolean addTangent,
            double minimumTangentStrength
    ) {
        Vec2 contribution = away.scale(weight);
        if (!addTangent) {
            return contribution;
        }

        double intoWall = Math.max(0.0, targetDirection.dot(away.scale(-1.0)));
        double tangentStrength = Math.max(minimumTangentStrength, Math.min(1.0, intoWall));
        if (tangentStrength <= 0.0) {
            return contribution;
        }
        return contribution.add(wallTangentAlongTarget(wall, targetDirection).scale(
                weight * WALL_TANGENTIAL_WEIGHT * tangentStrength));
    }

    private static ClosestSegmentPoints closestPointsBetweenSegments(
            Vec2 p1,
            Vec2 q1,
            Vec2 p2,
            Vec2 q2
    ) {
        Vec2 d1 = q1.sub(p1);
        Vec2 d2 = q2.sub(p2);
        Vec2 r = p1.sub(p2);
        double a = d1.dot(d1);
        double e = d2.dot(d2);
        double f = d2.dot(r);

        double s;
        double t;
        if (a <= GEOMETRY_EPS * GEOMETRY_EPS && e <= GEOMETRY_EPS * GEOMETRY_EPS) {
            return new ClosestSegmentPoints(p1, p2, 0.0, 0.0);
        }
        if (a <= GEOMETRY_EPS * GEOMETRY_EPS) {
            s = 0.0;
            t = clamp01(f / e);
        } else {
            double c = d1.dot(r);
            if (e <= GEOMETRY_EPS * GEOMETRY_EPS) {
                t = 0.0;
                s = clamp01(-c / a);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                if (Math.abs(denom) > GEOMETRY_EPS * GEOMETRY_EPS) {
                    s = clamp01((b * f - c * e) / denom);
                } else {
                    s = 0.0;
                }

                double tNumerator = b * s + f;
                if (tNumerator < 0.0) {
                    t = 0.0;
                    s = clamp01(-c / a);
                } else if (tNumerator > e) {
                    t = 1.0;
                    s = clamp01((b - c) / a);
                } else {
                    t = tNumerator / e;
                }
            }
        }

        return new ClosestSegmentPoints(
                p1.add(d1.scale(s)),
                p2.add(d2.scale(t)),
                s,
                t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Vec2 limitDirection(Vec2 targetDirection, Vec2 desiredDirection) {
        double dot = Math.max(-1.0, Math.min(1.0, targetDirection.dot(desiredDirection)));
        double angle = Math.acos(dot);
        if (angle <= MAX_AVOIDANCE_ANGLE) {
            return desiredDirection;
        }

        double side = cross(targetDirection, desiredDirection);
        double signedAngle = side < 0.0 ? -MAX_AVOIDANCE_ANGLE : MAX_AVOIDANCE_ANGLE;
        return rotate(targetDirection, signedAngle);
    }

    private static Vec2 rotate(Vec2 vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec2(
                vector.x() * cos - vector.y() * sin,
                vector.x() * sin + vector.y() * cos
        );
    }

    private static double cross(Vec2 a, Vec2 b) {
        return a.x() * b.y() - a.y() * b.x();
    }

    private Vec2 computeContactAcceleration(
            AgentState state,
            Vec2 position,
            Vec2 targetDirection,
            List<Neighbor> neighbors
    ) {
        Vec2 sum = Vec2.ZERO;
        for (Neighbor neighbor : neighbors) {
            Contact contact = contactFor(state, position, targetDirection, neighbor);
            double overlap = contact.overlap();
            if (overlap <= 0.0) {
                continue;
            }

            sum = sum.add(contact.normal().scale(normalStiffness * overlap));
        }
        return sum;
    }

    private Contact contactFor(
            AgentState state,
            Vec2 position,
            Vec2 targetDirection,
            Neighbor neighbor
    ) {
        if (neighbor.type() == NeighborType.AGENT && neighbor.agent() != null) {
            AgentState other = neighbor.agent();
            Vec2 otherPosition = new Vec2(other.x(), other.y());
            double centerDistance = position.distanceTo(otherPosition);
            double overlap = state.radius() + other.radius() - centerDistance;
            return new Contact(overlap, agentAwayDirection(position, targetDirection, otherPosition));
        }

        Wall wall = wallFor(neighbor.id());
        double overlap = state.radius() - wall.distanceTo(position);
        return new Contact(overlap, wallAwayDirection(position, targetDirection, wall));
    }

    private Wall wallFor(int wallId) {
        if (wallId < 0 || wallId >= walls.size()) {
            throw new IllegalStateException(
                    "Wall neighbor ID " + wallId + " is out of bounds for the loaded walls list.");
        }
        return walls.get(wallId);
    }

    private static Vec2 wallAwayDirection(Vec2 position, Vec2 targetDirection, Wall wall) {
        Vec2 away = position.sub(wall.closestPointTo(position));
        if (away.norm() > GEOMETRY_EPS) {
            return away.normalized();
        }

        Vec2 tangent = wall.p2().sub(wall.p1()).normalized();
        if (tangent.equals(Vec2.ZERO)) {
            return targetDirection.scale(-1.0);
        }
        Vec2 normal = new Vec2(-tangent.y(), tangent.x());
        return normal.dot(targetDirection) <= 0.0 ? normal : normal.scale(-1.0);
    }

    private static Vec2 agentAwayDirection(Vec2 position, Vec2 targetDirection, Vec2 otherPosition) {
        Vec2 away = position.sub(otherPosition);
        if (away.norm() > GEOMETRY_EPS) {
            return away.normalized();
        }
        return targetDirection.scale(-1.0);
    }

    private record Contact(double overlap, Vec2 normal) {
    }

    private record Step(Vec2 position, Vec2 velocity) {
    }

    private record AgentAvoidanceCandidate(double distance, Vec2 contribution) {
    }

    private record WallAvoidanceCandidate(double clearance, Vec2 contribution) {
    }

    private record ClosestSegmentPoints(
            Vec2 firstPoint,
            Vec2 secondPoint,
            double firstParameter,
            double secondParameter) {
    }
}
