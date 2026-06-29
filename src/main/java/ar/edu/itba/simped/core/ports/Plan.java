package ar.edu.itba.simped.core.ports;

import java.util.List;

/**
 * Sub-módulo 4.2 del contract v4.
 *
 * <p>Task list ordenada. Una instancia por agente, asignada en spawn por PG.
 * Actúa además como registro de templates desde el cual PG extrae plan
 * definitions.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I2:  recibe templates desde UserInput.</li>
 *   <li>I8:  provee la full task list a {@link StateMachine}.</li>
 *   <li>I18: lee Locations y Exits válidos desde {@link Geometry}.</li>
 *   <li>I21/I22: asignación y provisión de templates con
 *       {@link PedestrianGenerator}.</li>
 * </ul>
 * </p>
 */
public interface Plan {

    /** I8: lista ordenada de tasks. Cada entrada es un {@link TaskTarget}. */
    List<TaskTarget> taskList();

    /** Índice de la task activa. */
    int currentTaskIndex();

    /** Avanza al próximo task; equivalente a I10a habiendo completado uno. */
    void advance();
}
