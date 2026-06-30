package ar.edu.itba.simped.core;

/**
 * Escalera: tramo que conecta dos plantas. Es el elemento nuevo de la
 * ampliación 3D (ver D4 en {@code .claude/DECISIONES.md}).
 *
 * <p>Se declara en {@code STAIRS.csv} como el <b>eje</b> del tramo: el extremo
 * {@link #foot} (pie) en una planta y {@link #top} (tope) en otra, con
 * {@code foot.z() != top.z()}. El agente recorre el eje proyectado al plano
 * ({@link #axisXy()}) e <b>interpola</b> su altura {@code z} según el progreso
 * horizontal ({@link #pointAt(double)}). En la escalera la velocidad del agente
 * se reduce por {@link #speedFactor} (lo aplica el CPM, paso 6).</p>
 *
 * @param blockName    nombre del block (ej. "MAIN").
 * @param foot         extremo al pie de la escalera (planta inferior u origen).
 * @param top          extremo en el tope (planta superior o destino).
 * @param width        ancho del tramo [m].
 * @param speedFactor  factor de velocidad reducida en el tramo, en (0, 1].
 */
public record Stairs(String blockName, Vec3 foot, Vec3 top, double width, double speedFactor) {

    /** Factor de velocidad reducida por defecto si el CSV no lo especifica. */
    public static final double DEFAULT_SPEED_FACTOR = 0.5;

    public Stairs {
        if (blockName == null || blockName.isBlank()) {
            throw new IllegalArgumentException("Stairs requires a non-blank blockName");
        }
        if (foot == null || top == null) {
            throw new IllegalArgumentException("Stairs requires non-null foot and top");
        }
        if (Math.abs(foot.z() - top.z()) < 1e-9) {
            throw new IllegalArgumentException(
                    "Stairs must connect different floors (foot.z != top.z), got z=" + foot.z());
        }
        if (width <= 0.0) {
            throw new IllegalArgumentException("Stairs width must be positive, got " + width);
        }
        if (speedFactor <= 0.0 || speedFactor > 1.0) {
            throw new IllegalArgumentException("Stairs speedFactor must be in (0, 1], got " + speedFactor);
        }
    }

    /** Escalera con el factor de velocidad por defecto. */
    public Stairs(String blockName, Vec3 foot, Vec3 top, double width) {
        this(blockName, foot, top, width, DEFAULT_SPEED_FACTOR);
    }

    /** Eje planar (proyección {@code xy}) del pie al tope. */
    public Segment axisXy() {
        return new Segment(foot.xy(), top.xy());
    }

    /** Largo planar del tramo (para interpolar {@code z} por avance horizontal). */
    public double horizontalLength() {
        return foot.xy().distanceTo(top.xy());
    }

    /**
     * Punto 3D a lo largo del tramo: {@code progress} en {@code [0,1]} va del
     * pie ({@code 0}) al tope ({@code 1}). La {@code z} resulta de interpolar
     * linealmente entre {@code foot.z()} y {@code top.z()}.
     */
    public Vec3 pointAt(double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        return foot.add(top.sub(foot).scale(t));
    }
}