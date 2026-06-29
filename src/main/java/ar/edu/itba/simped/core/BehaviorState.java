package ar.edu.itba.simped.core;

/**
 * Etiqueta de estado de comportamiento del agente (state* en el contract v4).
 * Producido por StateMachine (4.4), leído por Output (3) y serializado al archivo
 * de salida como nombre del enum.
 */
public enum BehaviorState {
    IDLE,
    WALKING,
    APPROACHING,
    ARRIVED, //todo: review because maybe its not necessary
    OCCUPYING,
    LEAVING,
    QUEUEING,
    DEAD
}
