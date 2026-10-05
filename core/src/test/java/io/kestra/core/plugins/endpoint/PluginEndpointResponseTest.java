package io.kestra.core.plugins.endpoint;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PluginEndpointResponseTest {
    @Test
    void shouldSerializeDataToJson() {
        PluginEndpointResponse response = PluginEndpointResponse.of(Map.of("message", "hello: toto"));

        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
            .isEqualTo("{\"message\":\"hello: toto\"}");
    }
}
