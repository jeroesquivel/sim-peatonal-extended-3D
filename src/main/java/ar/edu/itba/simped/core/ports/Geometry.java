package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Wall;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-módulo 5.1 del contract v4. Definición espacial completa del escenario.
 * Read-only post-init.
 *
 * <p>Sub-componentes (5.1.1 – 5.1.5): Walls, Locations, Exits,
 * GeneratorPositions, ServerPositions.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I1:  recibe definición desde UserInput.</li>
 *   <li>I17: provee nodes+edges a {@link Graph} para init de la mesh.</li>
 *   <li>I18: provee Locations y Exits válidos como targets de {@link Plan}.</li>
 *   <li>I19: provee geometría de Walls a {@link NeighborsIndex}.</li>
 *   <li>I20: provee ServerPositions a {@link Server}.</li>
 * </ul>
 * </p>
 */
public interface Geometry {

    List<Wall> walls();

    List<Location> locations();

    List<Exit> exits();

    List<GeneratorZone> generatorZones();

    List<ServerZone> serverZones();

    /**
     * Escaleras que conectan plantas (vacío en escenarios de una sola planta).
     * Las usa el {@link Graph} para unir los grafos por planta y el
     * {@link OperationalModel} para la velocidad reducida e interpolación de z.
     */
    List<Stairs> stairs();

    // ── Consultas por planta (D5) ────────────────────────────────────────────
    // Una planta se identifica por su valor z (double). El grafo (paso 4) y el
    // CIM (paso 5) operan por planta y consumen estas vistas, derivadas de las
    // listas de arriba. Son default → las heredan todas las implementaciones.

    /** Tolerancia para comparar/deduplicar niveles de planta z. */
    double FLOOR_EPS = 1e-6;

    /**
     * Niveles de planta distintos presentes en la geometría (de walls, exits,
     * locations, generators, servers y los extremos de las escaleras),
     * ordenados ascendentemente y deduplicados por {@link #FLOOR_EPS}.
     */
    default List<Double> floors() {
        List<Double> all = new ArrayList<>();
        for (Wall w : walls()) all.add(w.z());
        for (Exit e : exits()) all.add(e.z());
        for (Location l : locations()) all.add(l.z());
        for (GeneratorZone g : generatorZones()) all.add(g.z());
        for (ServerZone s : serverZones()) all.add(s.z());
        for (Stairs s : stairs()) {
            all.add(s.foot().z());
            all.add(s.top().z());
        }
        all.sort(Double::compare);
        List<Double> out = new ArrayList<>();
        for (double z : all) {
            if (out.isEmpty() || Math.abs(out.get(out.size() - 1) - z) > FLOOR_EPS) {
                out.add(z);
            }
        }
        return out;
    }

    /** Paredes que pertenecen a la planta {@code z} (±{@link #FLOOR_EPS}). */
    default List<Wall> wallsOn(double z) {
        List<Wall> out = new ArrayList<>();
        for (Wall w : walls()) {
            if (Math.abs(w.z() - z) < FLOOR_EPS) out.add(w);
        }
        return out;
    }

    /** Salidas en la planta {@code z}. */
    default List<Exit> exitsOn(double z) {
        List<Exit> out = new ArrayList<>();
        for (Exit e : exits()) {
            if (Math.abs(e.z() - z) < FLOOR_EPS) out.add(e);
        }
        return out;
    }

    /** Targets en la planta {@code z}. */
    default List<Location> locationsOn(double z) {
        List<Location> out = new ArrayList<>();
        for (Location l : locations()) {
            if (Math.abs(l.z() - z) < FLOOR_EPS) out.add(l);
        }
        return out;
    }

    /** Zonas de spawn en la planta {@code z}. */
    default List<GeneratorZone> generatorZonesOn(double z) {
        List<GeneratorZone> out = new ArrayList<>();
        for (GeneratorZone g : generatorZones()) {
            if (Math.abs(g.z() - z) < FLOOR_EPS) out.add(g);
        }
        return out;
    }

    /** Servers en la planta {@code z}. */
    default List<ServerZone> serverZonesOn(double z) {
        List<ServerZone> out = new ArrayList<>();
        for (ServerZone s : serverZones()) {
            if (Math.abs(s.z() - z) < FLOOR_EPS) out.add(s);
        }
        return out;
    }

    /**
     * Escaleras que tocan la planta {@code z} en su pie <b>o</b> en su tope.
     * El grafo las usa para unir el grafo de esta planta con el de la planta
     * del otro extremo.
     */
    default List<Stairs> stairsAt(double z) {
        List<Stairs> out = new ArrayList<>();
        for (Stairs s : stairs()) {
            if (Math.abs(s.foot().z() - z) < FLOOR_EPS
                    || Math.abs(s.top().z() - z) < FLOOR_EPS) {
                out.add(s);
            }
        }
        return out;
    }
}
