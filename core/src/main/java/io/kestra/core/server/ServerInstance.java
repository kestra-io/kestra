package io.kestra.core.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.utils.IdUtils;

import java.util.Map;
import java.util.Set;

/**
 * Runtime information about a Kestra server (i.e. JVM).
 *
 * @param id      The server instance ID.
 * @param type    The server type.
 * @param version The server version.
 * @param props   The server properties - an opaque map of key/value properties.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ServerInstance(
    String id,
    Type type,
    String version,
    String hostname,
    Map<String, Object> props,
    Set<Metric> metrics
) {
    /// Static JVM Instance UUID
    public static final String INSTANCE_ID = IdUtils.create();

    /**
     * Creates a new {@link ServerInstance} using the static local instance ID.
     */
    public ServerInstance(final Type type,
                          final String version,
                          final String hostname,
                          final Map<String, Object> props,
                          final Set<Metric> metrics) {
        this(INSTANCE_ID, type, version, hostname, props, metrics);
    }

    public enum Type {
        SERVER, STANDALONE;
    }

}
