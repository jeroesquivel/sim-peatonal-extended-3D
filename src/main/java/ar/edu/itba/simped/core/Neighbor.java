package ar.edu.itba.simped.core;

/**
 * Obstáculo visible dentro del radio de interacción de un agente (I16q).
 *
 * @param id         id del vecino (agente) o de la pared (índice 0-based en Walls)
 * @param type       AGENT o WALL
 * @param distance   distancia centro-a-centro (agente) o punto-segmento (pared)
 * @param agent      referencia al AgentState vecino, o null si type == WALL
 */
public record Neighbor(int id, NeighborType type, double distance, AgentState agent) {
}
