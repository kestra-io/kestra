package io.kestra.core.plugins.endpoint;

import java.util.Map;

/**
 * Read-only access to the outputs of the single taskRun a plugin endpoint was invoked for. Values may
 * be opaque {@code kestra://} storage URIs rather than inline data; read those through {@link ScopedStorage}.
 */
public interface TaskRunOutputsFetcher {
    Map<String, Object> get();
}
