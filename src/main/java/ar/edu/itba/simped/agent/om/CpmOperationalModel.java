package ar.edu.itba.simped.agent.om;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.NeighborType;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.core.ports.OperationalModel;
import ar.edu.itba.simped.environment.neighbors.Wall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anisotropic Contractile Particle Model with Avoidance (AACPM)
 * based on Martin & Parisi (2024).
 *
 * <p>Extends the base Contractile Particle Model by incorporating angular
 * avoidance maneuvers exclusively through desired velocity dynamic adjustments,
 * calibrated against experimental pedestrian navigation data.</p>
 */
public final class CpmOperationalModel implements OperationalModel {

    /**
     * Distancia al slot bajo la cual el agente se ancla (snapping). Debe ser
     * fina (bastante menor que el arrivalThreshold del server, ~0.5 m) para
     * que el agente quede exactamente sobre el slot/posición que el server
     * chequea, y no a mitad de camino.
     */
    private static final double QUEUE_SNAP_DISTANCE = 0.05;

    /** Velocidad lenta de acercamiento al slot en cola (evita orbitar). */
    private static final double QUEUE_APPROACH_SPEED = 0.55;

    // --- Carriles subida/bajada en escalera (D19, contraflujo) ---
    /** Ancho mínimo del tramo para separar dos carriles; por debajo, un solo
     *  carril compartido (no hay espacio para el bias sin estrangular el paso). */
    private static final double STAIR_LANE_MIN_WIDTH = 2.5;
    /** Peso máximo (saturado) del bias de carril sobre la dirección deseada
     *  {@code e_a}. Calibrado por el chief (D19): con 0.20 el bias era demasiado
     *  débil y los carriles no se separaban (subida/bajada quedaban mezclados);
     *  con 0.45 el contraflujo separa ~0.34 m (subida en +perp, bajada en -perp)
     *  sin estrangular la evacuación densa (la repulsión entre agentes sigue
     *  dominando y el tramo se usa completo). No afecta el baseline unidireccional
     *  ni los tests (gateado por {@code stairLanes}, OFF por defecto). */
    private static final double LANE_BIAS_WEIGHT = 0.45;

    // --- Regla de prioridad asimétrica en contacto (experimento Grupo 7) ---
    /** Diferencia de impulso (m/s) bajo la cual se considera "empate" y decide
     *  el desempate determinista por id. */
    private static final double PRIORITY_TIE = 0.05;
    /** Fuerza del desempate: en empate, el de menor id cede {@code 1-DELTA} y el
     *  de mayor id cede {@code 1+DELTA} (rompe el bloqueo mutuo del gridlock). */
    private static final double PRIORITY_TIE_DELTA = 0.5;
    private static final double PRIORITY_EPS = 1e-3;

    /**
     * Lista <b>global</b> de paredes (todas las plantas), en el mismo orden/espacio
     * de ids que el {@code FloorAwareNeighborsIndex} (D8). Sirve sólo para resolver
     * el {@code wallId} de los vecinos {@code WALL} ({@code walls.get(id)}); esos
     * vecinos ya vienen filtrados por planta desde el CIM.
     */
    private final List<Wall> walls;

    /**
     * Paredes por planta (paralelo a {@link #floorLevels}), para el anti-tunneling
     * geométrico, que debe usar <b>sólo las paredes de la planta actual</b> del
     * agente (paso 6). {@code null} en el ctor legacy/1-planta → se cae a
     * {@link #walls} global (comportamiento idéntico al de antes).
     */
    private final List<List<Wall>> floorWalls;
    private final double[] floorLevels;

    /** Escaleras (vacío ⇒ sin física de escalera). */
    private final List<Stairs> stairs;

    /**
     * Por cada escalera, la unión de las paredes de las dos plantas que conecta:
     * anti-tunneling de un agente <b>sobre</b> la escalera (cerca de cualquiera de
     * los dos descansos). Keyed por identidad de la instancia de {@link Stairs}.
     */
    private final Map<Stairs, List<Wall>> stairWalls;

    /** Si está activa, el escape en contacto entre agentes es ASIMÉTRICO: el de
     *  mayor "fuerza de decisión" (impulso actual) prevalece y empuja hacia su
     *  objetivo, el otro cede más. Activable por env {@code SIMPED_CPM_PRIORITY}
     *  (on/1/true). Default off → escape simétrico clásico (baseline intacto). */
    private final boolean usePriority;

    /** rmin alternativo (experimento Grupo 7), seleccionable por env
     *  {@code SIMPED_CPM_SET} (set1|set2|tight|mid) SIN tocar App: se aplica acá
     *  sobre el perfil del agente, manteniendo rmax (así la grilla del CIM, que
     *  App dimensiona con el rmax del preset default, sigue siendo válida).
     *  null = usar el rmin que trae el perfil del agente. */
    private final Double rminOverride;

    /** Bias lateral de carril en escalera (D19), gateado: OFF en el ctor legacy y
     *  en {@link #fromGeometry(Geometry)} (delega con {@code false}) para que el
     *  comportamiento por defecto —y todos los tests que usan esos dos— sea
     *  byte-idéntico al de antes de los carriles. Sólo ON si se pide explícito
     *  vía {@link #fromGeometry(Geometry, boolean)}. */
    private final boolean stairLanes;

    /**
     * Constructor con lista de paredes explícita (1 planta / tests). Sin info de
     * plantas ni escaleras: el anti-tunneling usa la lista global y no hay física
     * de escalera. Comportamiento idéntico al 2D previo.
     */
    public CpmOperationalModel(List<Wall> walls) {
        this(walls, null, null, List.of(), Map.of(), false);
    }

    /**
     * Constructor multiplanta (paso 6, D9): toma las paredes <b>por planta</b> y
     * las escaleras desde {@link Geometry}. La lista global de paredes se arma con
     * el mismo orden que {@code FloorAwareNeighborsIndex.globalWalls} (concatenación
     * de {@code wallsOn(z)} sobre {@code floors()}) para que los {@code wallId} de
     * los vecinos resuelvan. Carriles de escalera OFF (ver {@link #fromGeometry(Geometry, boolean)}).
     */
    public static CpmOperationalModel fromGeometry(Geometry geometry) {
        return fromGeometry(geometry, false);
    }

    /**
     * Igual que {@link #fromGeometry(Geometry)}, con el bias lateral de carril en
     * escalera (D19) gateado por {@code stairLanes}. Con {@code false} el
     * comportamiento es idéntico al de siempre; con {@code true}, en tramos
     * anchos (≥ {@link #STAIR_LANE_MIN_WIDTH}) se separan los carriles de
     * subida/bajada (contraflujo).
     */
    public static CpmOperationalModel fromGeometry(Geometry geometry, boolean stairLanes) {
        List<Double> floors = geometry.floors();
        double[] levels = new double[floors.size()];
        List<List<Wall>> perFloor = new ArrayList<>(floors.size());
        List<Wall> global = new ArrayList<>();
        for (int fi = 0; fi < floors.size(); fi++) {
            double z = floors.get(fi);
            levels[fi] = z;
            List<Wall> fw = new ArrayList<>();
            for (ar.edu.itba.simped.core.Wall cw : geometry.wallsOn(z)) {
                Wall nw = new Wall(cw.p1(), cw.p2());
                fw.add(nw);
                global.add(nw);
            }
            perFloor.add(fw);
        }
        List<Stairs> stairs = geometry.stairs();
        Map<Stairs, List<Wall>> stairWalls = new IdentityHashMap<>();
        for (Stairs s : stairs) {
            int fa = nearestFloorIndex(levels, s.foot().z());
            int fb = nearestFloorIndex(levels, s.top().z());
            List<Wall> union = new ArrayList<>(perFloor.get(fa));
            if (fb != fa) union.addAll(perFloor.get(fb));
            stairWalls.put(s, union);
        }
        return new CpmOperationalModel(global, levels, perFloor, stairs, stairWalls, stairLanes);
    }

    private CpmOperationalModel(List<Wall> walls, double[] floorLevels,
                                List<List<Wall>> floorWalls, List<Stairs> stairs,
                                Map<Stairs, List<Wall>> stairWalls, boolean stairLanes) {
        if (walls == null) {
            throw new IllegalArgumentException("Walls list cannot be null");
        }
        this.walls = walls;
        this.stairLanes = stairLanes;
        this.floorLevels = floorLevels;
        this.floorWalls = floorWalls;
        this.stairs = stairs;
        this.stairWalls = stairWalls;
        String flag = System.getenv("SIMPED_CPM_PRIORITY");
        this.usePriority = flag != null
                && (flag.equalsIgnoreCase("on") || flag.equals("1") || flag.equalsIgnoreCase("true"));
        String set = System.getenv("SIMPED_CPM_SET");
        this.rminOverride = (set == null || set.isBlank()) ? null : CpmParameters.bySet(set).rmin();
    }

    private static int nearestFloorIndex(double[] levels, double z) {
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < levels.length; i++) {
            double d = Math.abs(levels[i] - z);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public double recommendedDt(AgentProfile profile) {
        return CpmParameters.recommendedDt(profile);
    }

    @Override
    public double neighborQueryRadius(AgentState state, BehaviorState behavior) {
        // El avoidance angular usa los 2 vecinos frontales más cercanos, pero
        // necesita "verlos" con suficiente antelación para anticipar la colisión
        // y maniobrar a tiempo. Un radio chico (rmax·2 ≈ 0.64 m) los detecta tarde
        // y los agentes alcanzan a chocarse antes de esquivar; rmax·8 (≈ 2.56 m)
        // da margen para la maniobra. App dimensiona la grilla del CIM con el
        // máximo entre su margen y este radio (Math.max en App), así que pedir más
        // acá no rompe el CIM: la grilla se adapta.
        double rmax = state.profile() != null ? state.profile().rmax() : 0.32;
        return rmax * 8.0;
    }

    @Override
    public void integrate(
            AgentState state,
            Vec3 footTarget3,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            double dt
    ) {
        // Paso 6: ¿el agente está sobre una escalera? Si sí, su velocidad deseada
        // se reduce por speedFactor y, tras mover en el plano, su z se interpola a
        // lo largo del tramo (D2). El anti-tunneling usa las paredes de la planta
        // actual (o la unión de las dos plantas de la escalera).
        Stairs onStair = locateStair(state, footTarget3);
        double speedScale = onStair != null ? onStair.speedFactor() : 1.0;
        List<Wall> floorWalls = floorWallsFor(state, onStair);

        integratePlanar(state, footTarget3, behavior, neighbors, dt, speedScale, floorWalls, onStair);

        if (onStair != null) {
            // z = lerp(foot.z, top.z, avance planar): la altura sigue al progreso
            // (x,y) del agente sobre el eje de la escalera (D2). Como el agente entra
            // al tramo por el PIE (avance ≈0, ver D21: exclusión de la huella del
            // grafo + STAIR_FOOT_REACH chico), la z engancha desde el nivel del piso y
            // crece suave; no hay salto por frame.
            state.setZ(onStair.zAt(state.x(), state.y()));
        }
    }

    /**
     * Escalera que el agente está <b>recorriendo</b>, o {@code null} si está en una
     * planta plana. El agente recorre el tramo {@code st} si está sobre su huella
     * ({@link Stairs#containsXy}) y, además, o bien su {@code z} ya está entre las dos
     * plantas (a mitad de escalera), o bien su {@code footTarget} apunta a otra planta
     * (acaba de pisar el pie/tope y va a cruzar). Esto último distingue "subir/bajar"
     * de "cruzar la huella en horizontal sobre una planta" (mismo {@code z} que el
     * footTarget → no se interpola z).
     */
    private Stairs locateStair(AgentState s, Vec3 footTarget) {
        if (stairs.isEmpty()) return null;
        double z = s.z();
        for (Stairs st : stairs) {
            if (!st.containsXy(s.x(), s.y())) continue;
            double zlo = Math.min(st.foot().z(), st.top().z());
            double zhi = Math.max(st.foot().z(), st.top().z());
            if (z < zlo - Geometry.FLOOR_EPS || z > zhi + Geometry.FLOOR_EPS) continue;
            boolean midStair = z > zlo + Geometry.FLOOR_EPS && z < zhi - Geometry.FLOOR_EPS;
            boolean headingAcross = footTarget != null
                    && Math.abs(footTarget.z() - z) > Geometry.FLOOR_EPS;
            if (midStair || headingAcross) return st;
        }
        return null;
    }

    /** Paredes a usar para el anti-tunneling de {@code state} este paso. */
    private List<Wall> floorWallsFor(AgentState state, Stairs onStair) {
        if (floorWalls == null || floorWalls.isEmpty()) {
            return walls; // ctor legacy / 1 planta: lista global
        }
        if (onStair != null) {
            return stairWalls.get(onStair);
        }
        return floorWalls.get(nearestFloorIndex(floorLevels, state.z()));
    }

    private void integratePlanar(
            AgentState state,
            Vec3 footTarget3,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            double dt,
            double speedScale,
            List<Wall> floorWalls,
            Stairs onStair
    ) {
        // La dinámica del CPM es planar (D1): se opera sobre la proyección xy del
        // foot-target. La z del agente (planta / escalera) la maneja el wrapper
        // integrate (interpolación en la escalera), no las fuerzas de este modelo.
        Vec2 footTarget = footTarget3 == null ? null : footTarget3.xy();
        AgentProfile profile = state.profile();
        // Experimento Grupo 7: si SIMPED_CPM_SET pidió otro rmin, lo aplicamos acá
        // (manteniendo el resto del perfil, sobre todo rmax). Así barremos rmin sin
        // tocar App ni el código de otros grupos.
        if (rminOverride != null) {
            profile = new AgentProfile(profile.vd(), profile.tau(), rminOverride,
                    profile.rmax(), profile.beta(), profile.ve());
        }

        // 0. DEAD state -> el agente terminó su plan y será removido (harvest
        //    al final del tick por el Driver). No se mueve ni interactúa.
        if (behavior == BehaviorState.DEAD) {
            state.setVelocity(0.0, 0.0);
            return;
        }

        // 1. OCCUPYING state -> stationary, expand radius
        if (behavior == BehaviorState.OCCUPYING) {
            state.setVelocity(0.0, 0.0);
            double newR = state.radius() + profile.rmax() / (profile.tau() / dt);
            if (newR > profile.rmax()) newR = profile.rmax();
            state.setRadius(newR);
            return;
        }

        // 2. LEAVING state -> move directly to target, ignore avoidance
        if (behavior == BehaviorState.LEAVING) {
            state.setRadius(profile.rmax());
            if (footTarget != null) {
                Vec2 pos = new Vec2(state.x(), state.y());
                Vec2 toTarget = footTarget.sub(pos);
                double n = toTarget.norm();
                if (n > 0.0) {
                    Vec2 v = toTarget.scale(profile.vd() * speedScale / n);
                    state.setVelocity(v.x(), v.y());
                    moveWithWallCheck(state, v, dt, floorWalls);
                }
            }
            return;
        }

        // 1.5. QUEUEING state -> el agente se forma en la fila. Manejo dedicado:
        //   - Si está sobre su slot (dentro de SNAP_DISTANCE), se ancla: vel 0,
        //     posición exacta, radio mínimo. Así no oscila en la cabecera y el
        //     server lo detecta como llegado para atenderlo.
        //   - Si no, avanza DESPACIO y directo hacia el slot (no a vd completo,
        //     que lo haría pasarse de largo y orbitar sin estabilizarse nunca).
        //     Conserva el contacto de núcleo duro contra otros agentes (no se
        //     atraviesan, indicación de cátedra), pero sin la velocidad de
        //     escape alta del CPM, que es la que dispersaba la fila.
        if (behavior == BehaviorState.QUEUEING && footTarget != null) {
            Vec2 pos = new Vec2(state.x(), state.y());
            Vec2 toSlot = footTarget.sub(pos);
            double distToSlot = toSlot.norm();
            state.setRadius(profile.rmin());

            // Separación dura por solapamiento de núcleos físicos (2·rmin):
            // desplazamiento POSICIONAL que saca al agente del solapamiento. Como
            // el vecino puede estar fijo (snappeado en su slot), separamos la
            // penetración COMPLETA, no la mitad, para garantizar no-superposición
            // aunque el otro no se mueva.
            Vec2 separation = hardCoreSeparation(state, pos, neighbors);
            boolean overlapping = separation.norm() > 1e-9;

            // Snapping al slot SÓLO si está libre. Si hay otro agente encima, no
            // nos teletransportamos: nos separamos primero.
            if (distToSlot < QUEUE_SNAP_DISTANCE && !overlapping) {
                state.setVelocity(0.0, 0.0);
                state.setPosition(footTarget.x(), footTarget.y());
                return;
            }

            if (overlapping) {
                // Resolver el solapamiento tiene prioridad sobre avanzar al slot.
                Vec2 target = pos.add(separation);
                if (isStepClear(pos, target, floorWalls)) {
                    state.setVelocity(separation.x() / dt, separation.y() / dt);
                    state.setPosition(target.x(), target.y());
                } else {
                    state.setVelocity(0.0, 0.0);
                }
                return;
            }

            // Sin solapamiento: avanzar suave hacia el slot.
            double speed = Math.min(QUEUE_APPROACH_SPEED, distToSlot / dt);
            Vec2 v = distToSlot > 1e-9 ? toSlot.scale(speed / distToSlot) : Vec2.ZERO;
            state.setVelocity(v.x(), v.y());
            moveWithWallCheck(state, v, dt, floorWalls);
            return;
        }

        Vec2 pos = new Vec2(state.x(), state.y());
        Vec2 vel = new Vec2(state.vx(), state.vy());

        // Target direction e_t
        Vec2 e_t = Vec2.ZERO;
        if (footTarget != null) {
            Vec2 toTarget = footTarget.sub(pos);
            double norm = toTarget.norm();
            if (norm > 0.0) {
                e_t = toTarget.scale(1.0 / norm);
            }
        }

        // Compute contact/overlap directions for basic physical contact
        List<Vec2> contactDirections = collectContactDirections(state, pos, neighbors);
        boolean inContact = !contactDirections.isEmpty();

        // CPM radius update rule
        if (inContact) {
            state.setRadius(profile.rmin());
        } else {
            double newR = state.radius() + profile.rmax() / (profile.tau() / dt);
            if (newR > profile.rmax()) newR = profile.rmax();
            state.setRadius(newR);
        }

        Vec2 v;
        if (inContact) {
            // Under frontal/physical contact, use high physical escape velocity.
            // Con prioridad: escape asimétrico (uno prevalece, el otro cede).
            v = usePriority
                    ? escapeVelocityPriority(state, pos, e_t, neighbors, profile)
                    : escapeVelocity(contactDirections, profile.ve());
        } else {
            // Otherwise, apply the Angular Avoidance CPM (AACPM) layer

            // A. Wall avoidance vector n_w_c
            Vec2 n_w_c = Vec2.ZERO;
            Neighbor closestWallNeighbor = null;
            double minWallDist = Double.MAX_VALUE;
            for (Neighbor neighbor : neighbors) {
                if (neighbor.type() == NeighborType.WALL) {
                    if (neighbor.distance() < minWallDist) {
                        minWallDist = neighbor.distance();
                        closestWallNeighbor = neighbor;
                    }
                }
            }

            if (closestWallNeighbor != null) {
                double distance = closestWallNeighbor.distance();
                int wallId = closestWallNeighbor.id();
                if (wallId >= 0 && wallId < this.walls.size()) {
                    Vec2 w = this.walls.get(wallId).closestPointTo(pos);
                    Vec2 toAgent = pos.sub(w);
                    Vec2 e_iw = distance > 0.0 ? toAgent.scale(1.0 / distance) : Vec2.ZERO;

                    // Calibrated wall parameters: Aw = 1215.0, Bw = 0.025
                    double Aw = 1215.0;
                    double Bw = 0.025;
                    double magnitude = Aw * Math.exp(-distance / Bw);
                    n_w_c = e_iw.scale(magnitude);
                } else {
                    throw new IllegalStateException("Wall neighbor ID " + wallId + " is out of bounds for the loaded walls list.");
                }
            }

            // B. Pedestrian avoidance vector n_j_c
            // Use velocity for forward visibility, or target direction if stationary
            Vec2 refDir = vel.norm() > 0.0 ? vel.normalized() : e_t;

            List<Neighbor> frontAgents = new ArrayList<>();
            for (Neighbor neighbor : neighbors) {
                if (neighbor.type() == NeighborType.AGENT && neighbor.agent() != null) {
                    // Suppress avoidance maneuvers between agents who are both queueing
                    if (behavior == BehaviorState.QUEUEING && neighbor.agent().state() == BehaviorState.QUEUEING) {
                        continue;
                    }
                    Vec2 otherPos = new Vec2(neighbor.agent().x(), neighbor.agent().y());
                    Vec2 toOther = otherPos.sub(pos);
                    if (toOther.norm() > 0.0) {
                        double dot = refDir.dot(toOther.normalized());
                        // Visible region defined as front semi-circle (angle in [-pi/2, pi/2])
                        if (dot >= 0.0) {
                            frontAgents.add(neighbor);
                        }
                    }
                }
            }

            // Sort visible front neighbors by distance to find the two closest ones
            frontAgents.sort(Comparator.comparingDouble(Neighbor::distance));
            int limit = Math.min(2, frontAgents.size());
            Vec2 sum_n_c_j = Vec2.ZERO;

            // Calibrated pedestrian parameters: Ap = 1.1, Bp = 2.1
            double Ap = 1.1;
            double Bp = 2.1;

            for (int idx = 0; idx < limit; idx++) {
                Neighbor neighbor = frontAgents.get(idx);
                AgentState otherState = neighbor.agent();
                Vec2 otherPos = new Vec2(otherState.x(), otherState.y());
                Vec2 otherVel = new Vec2(otherState.vx(), otherState.vy());

                // Relative velocity
                Vec2 v_ij = otherVel.sub(vel);

                // Angle beta between relative velocity v_ij and target direction e_t
                double beta = 0.0;
                if (v_ij.norm() > 0.0 && e_t.norm() > 0.0) {
                    double cosBeta = v_ij.dot(e_t) / (v_ij.norm() * e_t.norm());
                    cosBeta = Math.max(-1.0, Math.min(1.0, cosBeta));
                    beta = Math.acos(cosBeta);
                }

                Vec2 e_ij_c = Vec2.ZERO;
                // Avoidance maneuver is active if beta angle is in [pi/2, pi] (approaching)
                if (Math.abs(beta) >= Math.PI / 2.0) {
                    Vec2 toAgent = pos.sub(otherPos);
                    double dist = toAgent.norm();
                    if (dist > 0.0) {
                        Vec2 e_ij = toAgent.scale(1.0 / dist);

                        // Signed angle alpha between e_ij and v_ij
                        double alpha = Math.atan2(v_ij.y(), v_ij.x()) - Math.atan2(e_ij.y(), e_ij.x());
                        while (alpha > Math.PI) alpha -= 2.0 * Math.PI;
                        while (alpha < -Math.PI) alpha += 2.0 * Math.PI;

                        double fAlpha = Math.abs(Math.abs(alpha) - Math.PI / 2.0);
                        double signAlpha = Math.signum(alpha);
                        if (signAlpha == 0.0) {
                            signAlpha = 1.0; // Default deflection to break symmetry in perfect head-on course
                        }
                        double rotationAngle = -signAlpha * fAlpha;

                        double cosRot = Math.cos(rotationAngle);
                        double sinRot = Math.sin(rotationAngle);
                        double rx = e_ij.x() * cosRot - e_ij.y() * sinRot;
                        double ry = e_ij.x() * sinRot + e_ij.y() * cosRot;
                        e_ij_c = new Vec2(rx, ry);
                    }
                }

                double d_ij = neighbor.distance();
                double magnitude = Ap * Math.exp(-d_ij / Bp);
                Vec2 n_j_c = e_ij_c.scale(magnitude);

                sum_n_c_j = sum_n_c_j.add(n_j_c);
            }

            // Combine target, walls, and pedestrians to compute avoidance direction e_a
            Vec2 sumVectors = sum_n_c_j.add(n_w_c).add(e_t);
            Vec2 e_a = sumVectors.norm() > 0.0 ? sumVectors.normalized() : e_t;

            // Bias de carril subida/bajada en escalera ancha (D19, contraflujo).
            // Gateado por stairLanes (OFF por defecto: no cambia el baseline ni
            // los tests). Sólo en tramos con espacio para dos carriles
            // (width >= STAIR_LANE_MIN_WIDTH); si no, un solo carril compartido.
            // Corrección PERPENDICULAR gentil, no un reemplazo del target: mezcla
            // en e_a un versor hacia el centro del carril asignado, con peso
            // proporcional al desvío lateral actual y saturado en LANE_BIAS_WEIGHT,
            // para no pelear con el escape de contacto (que ya separa cuerpos) ni
            // estrangular el flujo denso (la repulsión entre agentes sigue
            // dominando y el tramo se usa completo en evacuación).
            if (onStair != null && stairLanes && onStair.width() >= STAIR_LANE_MIN_WIDTH
                    && footTarget3 != null) {
                boolean ascending = footTarget3.z() > state.z();
                Vec2 laneCenter = onStair.laneTargetAt(pos.x(), pos.y(), ascending).xy();
                Vec2 toLane = laneCenter.sub(pos);
                double lateralDev = toLane.norm();
                if (lateralDev > 1e-9) {
                    Vec2 laneDir = toLane.scale(1.0 / lateralDev);
                    double weight = Math.min(LANE_BIAS_WEIGHT,
                            LANE_BIAS_WEIGHT * (lateralDev / onStair.laneOffset()));
                    Vec2 blended = e_a.scale(1.0 - weight).add(laneDir.scale(weight));
                    if (blended.norm() > 0.0) {
                        e_a = blended.normalized();
                    }
                }
            }

            // Compute desired velocity based on the updated avoidance direction
            v = desiredVelocity(state, behavior, e_a, profile, speedScale);
        }

        state.setVelocity(v.x(), v.y());
        moveWithWallCheck(state, v, dt, floorWalls);
    }

    /**
     * Aplica el desplazamiento {@code v*dt} garantizando que el agente nunca
     * cruce ni roce una pared (anti-tunneling estricto). El destino propuesto
     * es válido sólo si: (a) el segmento centro→destino no corta ninguna pared,
     * y (b) el destino queda a más de {@code radio} de toda pared (el cuerpo del
     * agente no penetra). Si no es válido, se intenta deslizar paralelo a la
     * pared infractora; si tampoco, el agente se detiene este paso. Así ninguna
     * combinación de dt grande, velocidad alta o esquinas puede atravesar.
     */
    private void moveWithWallCheck(AgentState state, Vec2 v, double dt, List<Wall> floorWalls) {
        Vec2 from = new Vec2(state.x(), state.y());
        Vec2 to = new Vec2(from.x() + v.x() * dt, from.y() + v.y() * dt);

        if (isStepClear(from, to, floorWalls)) {
            state.setPosition(to.x(), to.y());
            return;
        }

        // Deslizar paralelo a la pared más cercana al destino propuesto. Para no
        // quedar "raspando" lento la pared cuando el agente la encara casi de
        // frente (proyección chica), deslizamos a la VELOCIDAD PLENA del agente
        // en la dirección paralela: así avanza decidido a lo largo del muro y
        // dobla la esquina rápido en vez de verse trabado.
        Wall blocker = nearestWall(to, floorWalls);
        if (blocker != null) {
            double wx = blocker.p2().x() - blocker.p1().x();
            double wy = blocker.p2().y() - blocker.p1().y();
            double wlen2 = wx * wx + wy * wy;
            if (wlen2 > 0.0) {
                double wlen = Math.sqrt(wlen2);
                double speed = v.norm();
                double dotv = (v.x() * wx + v.y() * wy) / wlen2;   // signo del avance
                double dir = dotv >= 0 ? 1.0 : -1.0;
                // velocidad plena a lo largo de la pared (no la proyección reducida)
                Vec2 slide = new Vec2(dir * wx / wlen * speed, dir * wy / wlen * speed);
                Vec2 slideTo = new Vec2(from.x() + slide.x() * dt, from.y() + slide.y() * dt);
                if (isStepClear(from, slideTo, floorWalls)) {
                    state.setVelocity(slide.x(), slide.y());
                    state.setPosition(slideTo.x(), slideTo.y());
                    return;
                }

                // Dirección bloqueada (esquina): probar el sentido opuesto.
                Vec2 oppSlide = slide.scale(-1.0);
                Vec2 oppSlideTo = new Vec2(from.x() + oppSlide.x() * dt, from.y() + oppSlide.y() * dt);
                if (isStepClear(from, oppSlideTo, floorWalls)) {
                    state.setVelocity(oppSlide.x(), oppSlide.y());
                    state.setPosition(oppSlideTo.x(), oppSlideTo.y());
                    return;
                }
            }
        }

        // Último recurso (agente clavado en el filo/punta de un muro, típico al
        // ser empujado contra el borde de una puerta): despegarse alejándose
        // perpendicularmente de la pared más cercana. Así sale del filo y en el
        // próximo paso puede re-encarar el hueco en vez de quedar trabado con v=0.
        Wall nw = nearestWall(from, floorWalls);
        if (nw != null) {
            Vec2 cp = nw.closestPointTo(from);
            Vec2 away = from.sub(cp);
            double an = away.norm();
            if (an > 1e-9) {
                double speed = Math.min(v.norm(), 0.3);
                if (speed < 0.05) speed = 0.3;   // siempre un empujón mínimo
                Vec2 push = away.scale(speed / an);
                Vec2 pushTo = new Vec2(from.x() + push.x() * dt, from.y() + push.y() * dt);
                if (isStepClear(from, pushTo, floorWalls)) {
                    state.setVelocity(push.x(), push.y());
                    state.setPosition(pushTo.x(), pushTo.y());
                    return;
                }
            }
        }
        // Nada seguro: quedarse quieto este tick.
        state.setVelocity(0.0, 0.0);
    }

    /**
     * El paso {@code from→to} es seguro si no ATRAVIESA ninguna pared. A
     * propósito NO se exige distancia mínima (clearance) del centro a la pared:
     * la separación cuerpo-pared la maneja la elusión del modelo (el término
     * {@code A_w·exp(-d/B_w)} empuja al agente lejos antes de tocar). Exigir
     * clearance acá frena al agente en las puntas/aristas de los muros (el slide
     * paralelo no lo deja rodear el endpoint y queda clavado con v=0). Sólo
     * bloqueamos el atravesamiento real (cruce de segmento).
     */
    private boolean isStepClear(Vec2 from, Vec2 to, List<Wall> floorWalls) {
        for (Wall w : floorWalls) {
            if (segmentsIntersect(from, to, w.p1(), w.p2())) {
                return false;
            }
        }
        return true;
    }

    /** Pared más cercana al punto {@code p} (para elegir sobre cuál deslizar). */
    private Wall nearestWall(Vec2 p, List<Wall> floorWalls) {
        Wall best = null;
        double bestD = Double.MAX_VALUE;
        for (Wall w : floorWalls) {
            double d = w.distanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    private static boolean segmentsIntersect(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        double d1 = cross(c, d, a);
        double d2 = cross(c, d, b);
        double d3 = cross(a, b, c);
        double d4 = cross(a, b, d);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        // Casos colineales/borde: un endpoint cae sobre el otro segmento.
        if (d1 == 0 && onSegment(c, d, a)) return true;
        if (d2 == 0 && onSegment(c, d, b)) return true;
        if (d3 == 0 && onSegment(a, b, c)) return true;
        if (d4 == 0 && onSegment(a, b, d)) return true;
        return false;
    }

    /** Producto cruz (p2-p1) x (p3-p1). */
    private static double cross(Vec2 p1, Vec2 p2, Vec2 p3) {
        return (p2.x() - p1.x()) * (p3.y() - p1.y()) - (p2.y() - p1.y()) * (p3.x() - p1.x());
    }

    /** Asume colinealidad: ¿{@code p} cae dentro del bounding box de a-b? */
    private static boolean onSegment(Vec2 a, Vec2 b, Vec2 p) {
        return Math.min(a.x(), b.x()) <= p.x() && p.x() <= Math.max(a.x(), b.x())
                && Math.min(a.y(), b.y()) <= p.y() && p.y() <= Math.max(a.y(), b.y());
    }

    private List<Vec2> collectContactDirections(AgentState self, Vec2 selfPos, List<Neighbor> neighbors) {
        List<Vec2> dirs = new ArrayList<>();
        Vec2 selfVel = new Vec2(self.vx(), self.vy());
        double rmin = self.profile() != null ? self.profile().rmin() : self.radius();

        for (Neighbor neighbor : neighbors) {
            if (neighbor.type() == NeighborType.AGENT && neighbor.agent() != null) {
                // Detección de contacto ANISOTRÓPICA (A-CPM, Martin & Parisi 2024 §3.2):
                // el contacto isotrópico clásico sólo aplica cuando la partícula está
                // en su radio físico mínimo (núcleo duro, omnidireccional — nunca se
                // atraviesan). Con espacio personal expandido (r≠rmin), sólo cuenta
                // como contacto si el vecino está en el FRENTE y su sección transversal
                // intersecta la franja frontal de ancho rmin. Esto evita las colisiones
                // artificiales entre partículas que caminan paralelas, que es lo que
                // diferencia el A-CPM del CPM isotrópico (clave para flujo bidireccional).
                AgentState other = neighbor.agent();
                Vec2 otherPos = new Vec2(other.x(), other.y());
                double centerDist = neighbor.distance();
                if (centerDist <= 0.0) continue;

                if (isAnisotropicContact(self, selfPos, selfVel, rmin, other, otherPos, centerDist)) {
                    dirs.add(selfPos.sub(otherPos).scale(1.0 / centerDist));
                }
            } else if (neighbor.type() == NeighborType.WALL) {
                double d = neighbor.distance();
                // La pared cuenta como CONTACTO (dispara la velocidad de escape
                // perpendicular, que ignora el objetivo) sólo cuando el CUERPO
                // FÍSICO del agente (rmin, núcleo duro) la tocaría — no dentro de
                // todo su espacio personal expandido (hasta rmax). En la franja
                // rmin..rmax la repulsión SUAVE de pared (Aw·exp(-d/Bw), rama de
                // avoidance) ya lo aparta sin anular el pull al objetivo, así el
                // agente DOBLA esquinas / cruza puertas en vez de rebotar y
                // quedarse pegado en la jamba (bug D14). El anti-tunneling
                // (moveWithWallCheck) sigue impidiendo atravesar la pared.
                if (d <= rmin && d > 0.0) {
                    int wallId = neighbor.id();
                    if (wallId >= 0 && wallId < this.walls.size()) {
                        Vec2 w = this.walls.get(wallId).closestPointTo(selfPos);
                        Vec2 toAgent = selfPos.sub(w);
                        dirs.add(toAgent.scale(1.0 / d));
                    } else {
                        throw new IllegalStateException("Wall neighbor ID " + wallId + " is out of bounds for the loaded walls list.");
                    }
                }
            }
        }

        dirs.removeIf(d -> d.equals(Vec2.ZERO));
        return dirs;
    }

    /**
     * Criterio de contacto anisotrópico del A-CPM (Martin & Parisi 2024, §3.2).
     *
     * <p>La partícula {@code self} está en contacto con {@code other} si:</p>
     * <ul>
     *   <li>Está en su radio físico (r = rmin): contacto isotrópico clásico,
     *       {@code |ri-rj| < ri+rj} (núcleo duro, omnidireccional). Garantiza
     *       que dos agentes nunca se atraviesen.</li>
     *   <li>Tiene espacio personal expandido (r ≠ rmin): contacto sólo si
     *       <ol>
     *         <li>el otro está al frente — ángulo entre {@code v_i} y
     *             {@code r_j - r_i} en [-π/2, π/2]; y</li>
     *         <li>la sección transversal frontal lo intersecta — la distancia
     *             perpendicular del centro de j al eje de movimiento de i es
     *             menor que {@code rmin + r_j} (las líneas paralelas a v_i desde
     *             el borde de rmin cortan el círculo de j); y</li>
     *         <li>los radios actuales se solapan: {@code |ri-rj| < ri+rj}.</li>
     *       </ol>
     *   </li>
     * </ul>
     * Sin velocidad definida (agente detenido) se cae al criterio isotrópico,
     * para no perder el núcleo duro cuando no hay dirección de movimiento.
     */
    private boolean isAnisotropicContact(AgentState self, Vec2 selfPos, Vec2 selfVel,
                                         double rmin, AgentState other, Vec2 otherPos,
                                         double centerDist) {
        double sumRadii = self.radius() + other.radius();
        boolean overlap = centerDist < sumRadii;
        if (!overlap) {
            return false;
        }

        boolean atMinRadius = self.radius() <= rmin + 1e-9;
        double speed = selfVel.norm();
        // En radio mínimo o sin dirección de movimiento -> núcleo duro isotrópico.
        if (atMinRadius || speed < 1e-9) {
            return true;
        }

        Vec2 dirMove = selfVel.scale(1.0 / speed);
        Vec2 toOther = otherPos.sub(selfPos);

        // (1) El otro debe estar al frente: proyección sobre la dirección de
        // movimiento positiva (ángulo en [-π/2, π/2]).
        double along = toOther.dot(dirMove);
        if (along < 0.0) {
            return false;
        }

        // (2) Sección transversal frontal: distancia perpendicular del centro de
        // j al eje de movimiento de i < rmin + r_j.
        double perp = Math.abs(toOther.x() * dirMove.y() - toOther.y() * dirMove.x());
        return perp < rmin + other.radius();
    }

    /**
     * Empuje POSICIONAL para resolver solapamiento de núcleos duros (2·rmin).
     * Para cada vecino agente cuyos cuerpos físicos penetran, suma un vector que
     * separa exactamente esa penetración (repartida 50/50 — el otro agente hace
     * lo simétrico). Devuelve el desplazamiento total a aplicar este paso. Si no
     * hay penetración, devuelve cero. Garantiza no-superposición incluso a vel 0.
     */
    private Vec2 hardCoreSeparation(AgentState self, Vec2 selfPos, List<Neighbor> neighbors) {
        double sx = 0.0, sy = 0.0;
        double rmin = self.profile() != null ? self.profile().rmin() : self.radius();
        for (Neighbor nb : neighbors) {
            if (nb.type() != NeighborType.AGENT || nb.agent() == null) continue;
            AgentState o = nb.agent();
            double oRmin = o.profile() != null ? o.profile().rmin() : o.radius();
            double minSep = rmin + oRmin;          // suma de núcleos duros
            double d = nb.distance();
            if (d > 0.0 && d < minSep) {
                double penetration = minSep - d;
                // Separar la penetración COMPLETA (+ margen): el vecino puede estar
                // fijo en su slot y no ceder, así que no contamos con que se mueva.
                double push = penetration + 1e-3;
                sx += (selfPos.x() - o.x()) / d * push;
                sy += (selfPos.y() - o.y()) / d * push;
            }
        }
        return new Vec2(sx, sy);
    }

    private Vec2 escapeVelocity(List<Vec2> contactDirections, double ve) {
        double sx = 0.0, sy = 0.0;
        for (Vec2 e : contactDirections) {
            sx += e.x();
            sy += e.y();
        }
        Vec2 sum = new Vec2(sx, sy);
        double n = sum.norm();
        if (n == 0.0) return Vec2.ZERO;
        return sum.scale(ve / n);
    }

    /**
     * Escape de contacto ASIMÉTRICO por prioridad ("fuerza de decisión").
     *
     * <p>Para cada agente en contacto, la prioridad se mide por el impulso
     * actual (módulo de velocidad): el que más avanza prevalece. El factor de
     * cesión del agente i frente a j es {@code 2·p_j/(p_i+p_j)} (∈[0,2]): si i
     * es más rápido cede menos (<1), si es más lento cede más (>1). En empate
     * (Δimpulso &lt; TIE, p. ej. ambos frenados en pleno gridlock) decide el id
     * de forma determinista para romper la simetría. Las paredes ceden siempre
     * con factor 1 (no negocian).</p>
     *
     * <p>Dirección = suma de direcciones de separación ponderadas por cesión.
     * Magnitud = {@code ve · min(1, cesión_media)} (el que prevalece escapa
     * menos). Además, el que prevalece (cesión_media &lt; 1) recibe un empuje
     * hacia su objetivo {@code e_t} proporcional a {@code 1−cesión_media}, para
     * que efectivamente "pase" en vez de quedarse trabado.</p>
     */
    private Vec2 escapeVelocityPriority(AgentState self, Vec2 selfPos, Vec2 e_t,
                                        List<Neighbor> neighbors, AgentProfile profile) {
        double pSelf = Math.hypot(self.vx(), self.vy());
        double sx = 0.0, sy = 0.0, yieldSum = 0.0;
        int count = 0;

        for (Neighbor nb : neighbors) {
            if (nb.type() == NeighborType.AGENT && nb.agent() != null) {
                AgentState o = nb.agent();
                double d = nb.distance();
                double sumR = self.radius() + o.radius();
                if (!(d <= sumR && d > 0.0)) continue;

                Vec2 dir = selfPos.sub(new Vec2(o.x(), o.y())).scale(1.0 / d);
                double pOther = Math.hypot(o.vx(), o.vy());
                double yield;
                if (Math.abs(pSelf - pOther) < PRIORITY_TIE) {
                    // Empate (gridlock): desempate determinista por id.
                    yield = self.id() > o.id() ? 1.0 + PRIORITY_TIE_DELTA
                                               : 1.0 - PRIORITY_TIE_DELTA;
                } else {
                    yield = 2.0 * (pOther + PRIORITY_EPS) / (pSelf + pOther + 2.0 * PRIORITY_EPS);
                }
                sx += dir.x() * yield;
                sy += dir.y() * yield;
                yieldSum += yield;
                count++;
            } else if (nb.type() == NeighborType.WALL) {
                double d = nb.distance();
                if (d <= self.radius() && d > 0.0) {
                    int wallId = nb.id();
                    if (wallId >= 0 && wallId < this.walls.size()) {
                        Vec2 w = this.walls.get(wallId).closestPointTo(selfPos);
                        Vec2 dir = selfPos.sub(w).scale(1.0 / d);
                        sx += dir.x();
                        sy += dir.y();
                        yieldSum += 1.0;
                        count++;
                    }
                }
            }
        }

        if (count == 0) return Vec2.ZERO;

        Vec2 sum = new Vec2(sx, sy);
        double norm = sum.norm();
        Vec2 dir = norm > 0.0 ? sum.scale(1.0 / norm) : Vec2.ZERO;
        double meanYield = yieldSum / count;
        // Magnitud proporcional a la cesión: el que prevalece (cesión<1) escapa
        // POCO y mantiene su lugar; el que cede (cesión>1, hasta 2) se aparta con
        // fuerza y libera el paso. Sin empuje hacia el objetivo: agregarlo metía
        // más presión hacia el punto de convergencia y agravaba el gridlock.
        double mag = profile.ve() * Math.min(2.0, meanYield);
        return dir.scale(mag);
    }

    private Vec2 desiredVelocity(AgentState state, BehaviorState behavior, Vec2 e_a,
                                 AgentProfile profile, double speedScale) {
        double range = profile.rmax() - profile.rmin();
        double frac = range > 0.0 ? (state.radius() - profile.rmin()) / range : 1.0;
        if (frac < 0.0) frac = 0.0;
        else if (frac > 1.0) frac = 1.0;

        double vdMax = profile.vd();
        if (behavior == BehaviorState.APPROACHING) {
            vdMax *= 0.3;
        }
        // Paso 6: velocidad reducida sobre la escalera (speedScale = speedFactor).
        vdMax *= speedScale;

        double magnitude = vdMax * Math.pow(frac, profile.beta());
        return e_a.scale(magnitude);
    }
}
