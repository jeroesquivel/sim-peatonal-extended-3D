package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Wall;

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
}
