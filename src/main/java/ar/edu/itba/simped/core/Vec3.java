package ar.edu.itba.simped.core;

/**
 * Vector 3D inmutable. Tipo de posición/velocidad del agente en el simulador
 * multiplanta: {@code x, y} es la coordenada planar (plano de cada planta) y
 * {@code z} la altura/planta (cambia principalmente al recorrer una escalera).
 *
 * <p>La <b>física peatonal es planar</b> (el CPM opera en el plano de la planta
 * del agente). Por eso este tipo expone {@link #xy()} para proyectar al plano y
 * delegar la dinámica horizontal en {@link Vec2}; la {@code z} se actualiza por
 * separado (interpolación a lo largo del tramo de escalera). La heurística y los
 * costos del A* 3D, en cambio, sí usan las tres coordenadas vía
 * {@link #distanceTo(Vec3)}.</p>
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    /** Construye un Vec3 a partir de una posición planar y una altura. */
    public static Vec3 of(Vec2 xy, double z) {
        return new Vec3(xy.x(), xy.y(), z);
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 sub(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double norm() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Distancia euclídea 3D (heurística admisible del A* multiplanta). */
    public double distanceTo(Vec3 other) {
        return sub(other).norm();
    }

    /** Distancia planar (ignora {@code z}) — útil dentro de una misma planta. */
    public double horizontalDistanceTo(Vec3 other) {
        return xy().distanceTo(other.xy());
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 normalized() {
        double n = norm();
        if (n < 1e-12) return ZERO;
        return scale(1.0 / n);
    }

    public Vec3 clampNorm(double maxNorm) {
        double n = norm();
        if (n <= maxNorm || n < 1e-12) return this;
        return scale(maxNorm / n);
    }

    /** Proyección al plano horizontal (descarta {@code z}). */
    public Vec2 xy() {
        return new Vec2(x, y);
    }

    /** Copia con otra altura {@code z}. */
    public Vec3 withZ(double newZ) {
        return new Vec3(x, y, newZ);
    }
}
