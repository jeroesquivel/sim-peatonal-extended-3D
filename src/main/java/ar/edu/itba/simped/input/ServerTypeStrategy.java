package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.ServerType;

/**
 * Strategy para resolver el {@link ServerType} de un
 * {@link ar.edu.itba.simped.core.ServerZone}.
 *
 * <ul>
 *   <li><b>Formato A:</b> el type viene explícito en {@code SERVER_PARAMS.csv};
 *       no se invoca esta strategy.</li>
 *   <li><b>Formato B:</b> se invoca para inferir el type desde info
 *       estructural (presencia de queues).</li>
 * </ul>
 */
public interface ServerTypeStrategy {

    ServerType resolve(String baseName, boolean hasQueues);
}
