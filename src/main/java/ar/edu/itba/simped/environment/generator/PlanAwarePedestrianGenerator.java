package ar.edu.itba.simped.environment.generator;

import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;

/**
 * Extensión de {@link PedestrianGenerator} que permite consultar qué
 * {@link PlanTemplate} fue asignado a cada agente spawneado.
 *
 * <p>El wiring externo (e.g. WiredPedestrianGenerator) puede hacer
 * instanceof-check y usar esta interfaz para construir el Plan correcto
 * por agente en lugar de uno global.</p>
 */
public interface PlanAwarePedestrianGenerator extends PedestrianGenerator {

    /**
     * Devuelve el {@link PlanTemplate} asignado al agente con el id dado,
     * o {@code null} si no fue spawneado por este generador.
     */
    PlanTemplate planTemplateFor(int agentId);
}
