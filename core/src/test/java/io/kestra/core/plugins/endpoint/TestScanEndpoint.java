package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.annotations.Plugin;

import java.util.Map;

@Plugin
public class TestScanEndpoint implements PluginEndpoint {
    @Override
    public String name() {
        return "test-scan";
    }

    @Override
    public PluginEndpointResponse handle(PluginEndpointRequest request) {
        return PluginEndpointResponse.of(Map.of("ok", true));
    }
}
