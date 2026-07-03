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

    /**
     * Avance del agente a lo largo del tramo: proyecta {@code (px,py)} sobre el
     * eje planar y devuelve el parámetro recortado a {@code [0,1]} (pie {@code 0}
     * → tope {@code 1}). Reutilizado por el CIM (clasificación por planta) y el
     * CPM (interpolación de {@code z} en la escalera).
     */
    public double progressAt(double px, double py) {
        double ax = foot.x(), ay = foot.y();
        double dx = top.x() - ax, dy = top.y() - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0.0) return 0.0;
        double t = ((px - ax) * dx + (py - ay) * dy) / len2;
        return Math.max(0.0, Math.min(1.0, t));
    }

    /**
     * Altura {@code z} interpolada según el avance horizontal de {@code (px,py)}
     * a lo largo del tramo ({@code z = lerp(foot.z, top.z, progressAt)}). Es la
     * regla de D2: la {@code z} no es grado de libertad dinámico, surge del
     * progreso planar en la escalera.
     */
    public double zAt(double px, double py) {
        return foot.z() + (top.z() - foot.z()) * progressAt(px, py);
    }

    /**
     * ¿El punto planar {@code (px,py)} cae dentro de la huella del tramo? Es
     * decir, su proyección sobre el eje cae en {@code [0,1]} y su distancia
     * perpendicular al eje es {@code ≤ width/2}.
     */
    public boolean containsXy(double px, double py) {
        double ax = foot.x(), ay = foot.y();
        double dx = top.x() - ax, dy = top.y() - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0.0) {
            return Math.hypot(px - ax, py - ay) <= width / 2.0;
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / len2;
        if (t < 0.0 || t > 1.0) return false;
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy) <= width / 2.0;
    }

    // --- Carriles subida/bajada (D19, contraflujo): métodos derivados de
    // foot/top/width, sin agregar campos al record (no rompe los call-sites
    // existentes que instancian Stairs con 4 o 5 args). ---

    /** Versor unitario del eje planar, en el sentido pie → tope. */
    public Vec2 axisDirXy() {
        return top.xy().sub(foot.xy()).normalized();
    }

    /** Versor perpendicular al eje en el plano (rota {@link #axisDirXy()} 90°). */
    public Vec2 perpXy() {
        Vec2 dir = axisDirXy();
        return new Vec2(-dir.y(), dir.x());
    }

    /** Offset lateral del centro de cada carril respecto al eje: medio de media-huella. */
    public double laneOffset() {
        return width / 4.0;
    }

    /**
     * Centro del carril de subida/bajada a la altura del avance planar de
     * {@code (px,py)}: proyecta sobre el eje ({@link #progressAt}), toma el punto
     * del eje a ese progreso (con {@code z} interpolada, {@link #pointAt}) y lo
     * desplaza perpendicularmente {@code +laneOffset()} si {@code ascending},
     * {@code -laneOffset()} si no. Es el punto hacia el que el CPM aplica el
     * bias lateral gentil en tramos anchos (paso 6 / D19).
     */
    public Vec3 laneTargetAt(double px, double py, boolean ascending) {
        double t = progressAt(px, py);
        Vec3 axisPoint = pointAt(t);
        double sign = ascending ? 1.0 : -1.0;
        Vec2 lanePos = axisPoint.xy().add(perpXy().scale(sign * laneOffset()));
        return lanePos.withZ(axisPoint.z());
    }
}