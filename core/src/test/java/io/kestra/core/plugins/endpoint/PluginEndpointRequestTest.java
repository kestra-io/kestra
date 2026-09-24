package io.kestra.core.plugins.endpoint;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PluginEndpointRequestTest {
    @Test
    void shouldReturnFirstParameterValueOrNull() {
        PluginEndpointRequest request = new PluginEndpointRequest(
            Map.of("name", List.of("toto", "titi")), null);

        assertThat(request.param("name")).isEqualTo("toto");
        assertThat(request.param("missing")).isNull();
    }
}
