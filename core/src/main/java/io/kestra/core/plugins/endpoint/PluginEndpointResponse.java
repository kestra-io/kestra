package io.kestra.core.plugins.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.serializers.JacksonMapper;

public record PluginEndpointResponse(byte[] body, String contentType) {
    public static PluginEndpointResponse of(Object data) {
        try {
            return new PluginEndpointResponse(
                JacksonMapper.ofJson().writeValueAsBytes(data),
                "application/json"
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize the plugin endpoint response to JSON.", e);
        }
    }

    public static PluginEndpointResponse ofBytes(byte[] body, String contentType) {
        return new PluginEndpointResponse(body, contentType);
    }
}
