package ar.edu.itba.simped.scenario;

import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Gaussian;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.ServerSignal;
import ar.edu.itba.simped.core.ports.StandardServerSignal;
import ar.edu.itba.simped.agent.AgentImpl;
import ar.edu.itba.simped.environment.servers.engine.ServersModule;
import ar.edu.itba.simped.environment.servers.engine.ServersParameters;
import ar.edu.itba.simped.environment.servers.interfaces.EventSink;
import ar.edu.itba.simped.environment.servers.interfaces.TargetSink;
import ar.edu.itba.simped.environment.servers.model.Rectangle;
import ar.edu.itba.simped.environment.servers.model.Server;
import ar.edu.itba.simped.environment.servers.model.ServerConfig;
import ar.edu.itba.simped.environment.servers.model.ServerType;
import ar.edu.itba.simped.environment.servers.queue.QueueLine;
import ar.edu.itba.simped.environment.servers.service.ServiceTimeSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Glue de T6: arma el {@link ServersModule} real de G0 a partir de los
 * {@link ServerZone} que parsea G3, y conecta sus sinks (I13b/I13c) de vuelta
 * a los agentes vía su {@code Sensors}.
 *
 * <p>El SM delega a {@code module.ports()} (I13a); el módulo, cuando avanza con
 * {@link ServersModule#step}, empuja los targets de servicio (I13b →
 * {@code Sensors.setFootTarget}) y la señal de fin (I13c →
 * {@code Sensors.onServerSignal} → {@code StateMachine.onTaskComplete}).</p>
 *
 * <p>Construir los tipos de server / su lifecycle es de G0; acá solo se los
 * instancia desde el escenario y se los cablea al loop.</p>
 */
public final class ServersWiring {

    private static final long SEED = 0L;
    private static final int QUEUE_SLOTS = 64;
    private static final Vec2 QUEUE_DIRECTION = new Vec2(0.0, -1.0);
    /** Separación entre la posición de servicio y el slot 0 de la fila [m].
     *  Debe ser mayor que arrivalThreshold: si el slot 0 coincidiera con la
     *  posición de servicio, el primero de la fila quedaría superpuesto con el
     *  agente siendo atendido (los OMs anulan la repulsión entre QUEUEING). */
    private static final double QUEUE_FRONT_GAP = 1.0;

    private ServersWiring() {
    }

    /**
     * @param zones    server zones parseadas por G3
     * @param registry map agentId → Agent que el wiring de spawn va poblando;
     *                 los sinks lo usan para encontrar el agente destino
     */
    public static ServersModule build(List<ServerZone> zones, Map<Integer, Agent> registry) {
        ServersParameters params = ServersParameters.defaults();

        List<Server> servers = new ArrayList<>(zones.size());
        int id = 0;
        for (ServerZone zone : zones) {
            servers.add(toServer(id++, zone, params.slotSpacing()));
        }

        TargetSink targetSink = (agentId, target) -> {
            Agent a = registry.get(agentId);
            if (a instanceof AgentImpl impl) {
                impl.setServerTarget(target);   // I13b: el agente sigue el target del Server
            }
        };
        EventSink eventSink = new EventSink() {
            @Override
            public void serviceComplete(int agentId) {
                // Limpiar el target fino ANTES de relayar: la SM puede avanzar
                // el plan y re-delegar sincrónicamente (que setea uno nuevo).
                Agent a = registry.get(agentId);
                if (a instanceof AgentImpl impl) {
                    impl.setServerTarget(null);
                }
                relay(agentId, StandardServerSignal.SERVICE_COMPLETE);   // I13c → onTaskComplete
            }

            @Override
            public void arrivedAtPost(int agentId) {
                relay(agentId, StandardServerSignal.ARRIVED_AT_POST);    // I13c → QUEUEING en el puesto
            }

            private void relay(int agentId, ServerSignal signal) {
                Agent a = registry.get(agentId);
                if (a instanceof AgentImpl impl) {
                    impl.sensors().onServerSignal(signal);
                }
            }
        };

        return new ServersModule(servers, targetSink, eventSink,
                new ServiceTimeSampler(new Random(SEED)), params);
    }

    private static Server toServer(int id, ServerZone zone, double slotSpacing) {
        String name = zone.baseName() + "_" + zone.id() + "_SERVER";
        // group = baseName: todos los miembros con el mismo prefijo (CASHIER_1,
        // CASHIER_2, MOLINETE_1..4) forman UN grupo lógico. El SM delega al grupo
        // y el módulo reparte al miembro menos cargado (softmax carga+distancia).
        // Cada miembro mantiene su propia cola.
        String group = zone.baseName();
        Vec2 servicePos = zone.area().centroid();
        Rectangle region = toG0Rect(zone);
        double tMean = serviceParam(zone);

        return switch (zone.type()) {
            case QUEUE -> new Server(id, name, group, ServerType.QUEUE,
                    new ServerConfig.Queue(tMean), region,
                    queueLine(zone, servicePos, slotSpacing),
                    servicePos);
            case SEMAPHORE -> new Server(id, name, group, ServerType.SEMAPHORE,
                    semaphoreConfig(zone, tMean), region, null, servicePos);
            case CLASSROOM -> new Server(id, name, group, ServerType.CLASSROOM,
                    new ServerConfig.Classroom(sessions(zone), tMean), region, null, servicePos);
        };
    }

    /**
     * Línea de la fila de un QUEUE. Si el escenario trae segmentos
     * {@code *_QUEUE000} (I20), el primero define la fila completa: el frente
     * es el extremo más cercano a la posición de servicio y la capacidad sale
     * del largo del segmento — así el diseñador del escenario controla por
     * dónde corre la fila (molinetes apilados, paredes, etc.) y que no se
     * superponga ni atraviese geometría. Sin segmento, default: la fila crece
     * hacia el sur desde {@code QUEUE_FRONT_GAP} detrás del puesto.
     */
    private static QueueLine queueLine(ServerZone zone, Vec2 servicePos, double slotSpacing) {
        if (zone.queues() != null && !zone.queues().isEmpty()) {
            Segment seg = zone.queues().get(0);
            Vec2 front = servicePos.distanceTo(seg.a()) <= servicePos.distanceTo(seg.b())
                    ? seg.a() : seg.b();
            Vec2 back = front.equals(seg.a()) ? seg.b() : seg.a();
            if (front.distanceTo(back) > 1e-9) {
                return new QueueLine(front, back, slotSpacing);
            }
        }
        return QueueLine.directed(servicePos.add(QUEUE_DIRECTION.scale(QUEUE_FRONT_GAP)),
                QUEUE_DIRECTION, slotSpacing, QUEUE_SLOTS);
    }

    /**
     * Semaphore (G0): ciclo verde/rojo. period = server_time_param,
     * green = green_duration (default = period → siempre verde), offset = t_init.
     */
    private static ServerConfig.Semaphore semaphoreConfig(ServerZone zone, double period) {
        double green = zone.params().greenDuration().orElse(period);
        if (green <= 0.0 || green > period) {
            green = period;
        }
        double offset = zone.params().startTime().orElse(0.0);
        return new ServerConfig.Semaphore(period, green, offset);
    }


    private static double serviceParam(ServerZone zone) {
        return zone.params().serviceTime()
                .map(ServersWiring::representativeValue)
                .orElse(1.0);
    }

    /** Valor central de una distribución, para usarse como t_mean / período del
     *  ciclo (el muestreo aleatorio real lo hace el ServiceTimeSampler para
     *  QUEUE; semaphore/classroom usan este valor como tiempo fijo del ciclo). */
    private static double representativeValue(Distribution d) {
        return switch (d) {
            case Deterministic det -> det.value();
            case Gaussian g -> g.mean();
            case Uniform u -> (u.min() + u.max()) / 2.0;
        };
    }

    /** t_init de cada sesión, ordenados y sin duplicados (Classroom los exige
     *  estrictamente crecientes). Si no hay sesiones, una en t=0. */
    private static double[] sessions(ServerZone zone) {
        double[] sorted = zone.params().sessionStarts().stream()
                .mapToDouble(Double::doubleValue)
                .sorted()
                .distinct()
                .toArray();
        return sorted.length == 0 ? new double[] {0.0} : sorted;
    }

    private static Rectangle toG0Rect(ServerZone zone) {
        Vec2 a = zone.area().a();
        Vec2 c = zone.area().c();
        return new Rectangle(
                Math.min(a.x(), c.x()), Math.min(a.y(), c.y()),
                Math.max(a.x(), c.x()), Math.max(a.y(), c.y()));
    }
}
