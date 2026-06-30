package ar.edu.itba.simped.core;

/**
 * Única fuente de verdad del estado kinemático y de comportamiento de un agente
 * (módulo 4.1 del contract v4).
 *
 * <p>Mutable: lo popula PG en init (I7), lo escribe OM cada dt (I15), lo leen
 * Sensors (I6) y CIM (I16).</p>
 *
 * <p>Sin lógica de negocio. Cualquier cálculo (integración, distancia a target,
 * etc.) vive en los módulos consumidores.</p>
 */
public final class AgentState {

    private final int id;
    private final String agentType;

    private double x;
    private double y;
    private double z;
    private double vx;
    private double vy;
    private double radius;
    private BehaviorState state;
    private AgentProfile profile;

    public AgentState(int id, String agentType) {
        this.id = id;
        this.agentType = agentType;
        this.state = BehaviorState.IDLE;
    }

    public AgentProfile profile() {
        return profile;
    }

    public void setProfile(AgentProfile profile) {
        this.profile = profile;
    }

    public int id() {
        return id;
    }

    public String agentType() {
        return agentType;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /**
     * Altura/planta del agente. {@code z} cambia principalmente al recorrer una
     * escalera (la dinámica horizontal del CPM no la toca). Default 0 = planta baja.
     */
    public double z() {
        return z;
    }

    public double vx() {
        return vx;
    }

    public double vy() {
        return vy;
    }

    public double radius() {
        return radius;
    }

    public BehaviorState state() {
        return state;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setVx(double vx) {
        this.vx = vx;
    }

    public void setVy(double vy) {
        this.vy = vy;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    /**
     * Mueve al agente en el plano horizontal <b>conservando su {@code z}</b>.
     * Es la que usa la física planar del CPM cada {@code dt}: la altura solo la
     * cambia el avance por escalera vía {@link #setZ(double)} o
     * {@link #setPosition(double, double, double)}.
     */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Ubica al agente en el espacio 3D (planta incluida). */
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Posición 3D del agente (proyectable al plano con {@link Vec3#xy()}). */
    public Vec3 position() {
        return new Vec3(x, y, z);
    }

    public void setVelocity(double vx, double vy) {
        this.vx = vx;
        this.vy = vy;
    }

    public void setState(BehaviorState state) {
        this.state = state;
    }
}
