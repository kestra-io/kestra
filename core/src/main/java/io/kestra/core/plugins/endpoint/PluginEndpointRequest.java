package io.kestra.core.plugins.endpoint;

import java.util.List;
import java.util.Map;

public record PluginEndpointRequest(Map<String, List<String>> parameters, byte[] body) {
    public PluginEndpointRequest {
        parameters = parameters == null ? Map.of() : parameters;
        body = body == null ? new byte[0] : body;
    }

    public String param(String name) {
        List<String> values = parameters.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
