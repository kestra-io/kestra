package io.kestra.core.plugins.endpoint;

import java.util.List;
import java.util.Map;

public record DefaultPluginEndpointContext(
    Map<String, List<String>> parameters,
    Map<String, Object> body,
    String executionId,
    String taskRunId,
    TaskRunLogsFetcher logs,
    TaskRunOutputsFetcher outputs,
    ScopedStorage storage
) implements PluginEndpointContext {
    public DefaultPluginEndpointContext {
        parameters = parameters == null ? Map.of() : parameters;
        body = body == null ? Map.of() : body;
    }
}
