package ar.edu.itba.simped.input.csv;

/**
 * Tipo de fila en SERVERS.csv: {@code _SERVER} (rectángulo de
 * atención) o {@code _QUEUE<nnn>} (segmento de cola).
 */
public enum ServerKind {
    SERVER,
    QUEUE
}
