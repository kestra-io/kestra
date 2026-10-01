package io.kestra.core.plugins.endpoint;

import java.util.List;
import java.util.Map;

/**
 * The context a {@link PluginEndpoint} is invoked with. It carries the request inputs and, scoped by
 * construction to the single execution / taskRun the endpoint was called for, read access to that
 * taskRun's logs and outputs and read/write access to the execution's internal storage. A plugin
 * receives only this context; it is handed no repository, storage root or bean, and each capability
 * can reach nothing beyond that one execution / taskRun.
 */
public interface PluginEndpointContext {
    Map<String, List<String>> parameters();

    Map<String, Object> body();

    String executionId();

    String taskRunId();

    TaskRunLogs logs();

    TaskRunOutputs outputs();

    ScopedStorage storage();

    default String param(String name) {
        List<String> values = parameters().get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
