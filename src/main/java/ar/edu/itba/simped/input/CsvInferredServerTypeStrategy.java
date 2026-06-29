package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.ServerType;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inferencia de {@link ServerType} desde la presencia/ausencia de
 * QUEUE rows: con queues → {@link ServerType#QUEUE}, sin queues →
 * {@link ServerType#CLASSROOM}. {@link ServerType#BROADCAST} no se
 * infiere — requiere declaración explícita (Formato A
 * {@code SERVER_PARAMS.csv}).
 *
 * <p><b>Frágil:</b> la presencia de queues no es semánticamente
 * equivalente al type. Se loguea un warning estructurado en cada
 * resolución.</p>
 */
public final class CsvInferredServerTypeStrategy implements ServerTypeStrategy {

    private static final Logger LOG = Logger.getLogger(CsvInferredServerTypeStrategy.class.getName());

    @Override
    public ServerType resolve(String baseName, boolean hasQueues) {
        ServerType inferred = hasQueues ? ServerType.QUEUE : ServerType.CLASSROOM;
        LOG.log(Level.WARNING,
                "ServerType inferred from CSV for block_name={0} → {1}. FRAGILE: "
                        + "BROADCAST can never be inferred; queue/classroom relies only "
                        + "on presence of QUEUE rows.",
                new Object[]{baseName, inferred});
        return inferred;
    }
}
