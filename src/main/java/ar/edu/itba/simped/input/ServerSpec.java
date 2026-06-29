package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.ServerParams;
import ar.edu.itba.simped.core.ServerType;

/**
 * Pair interno: {@link ServerType} + {@link ServerParams} (parámetros de
 * comportamiento). Producido por ambos format loaders y consumido por el
 * GeometryAssembler (Bloque H) al armar {@link ar.edu.itba.simped.core.ServerZone}.
 *
 * <p>{@code explicitType} indica si el {@code type} fue declarado por el
 * escenario (Formato A {@code SERVER_PARAMS.csv}, o Formato B con campo
 * {@code type}) o es solo un placeholder a inferir. Cuando es {@code true} el
 * GeometryAssembler respeta el type y NO corre la inferencia por queues.</p>
 */
public record ServerSpec(ServerType type, ServerParams params, boolean explicitType) {

    /** Type explícito (declarado por el escenario). */
    public ServerSpec(ServerType type, ServerParams params) {
        this(type, params, true);
    }
}
