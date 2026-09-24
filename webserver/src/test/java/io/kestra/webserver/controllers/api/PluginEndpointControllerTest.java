package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.plugins.endpoint.PluginEndpoint;
import io.kestra.core.plugins.endpoint.PluginEndpointRequest;
import io.kestra.core.plugins.endpoint.PluginEndpointResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginEndpointControllerTest {
    private static PluginEndpoint helloEndpoint() {
        return new PluginEndpoint() {
            @Override public String name() { return "hello"; }
            @Override public PluginEndpointResponse handle(PluginEndpointRequest request) {
                return PluginEndpointResponse.of(Map.of("message", "hello: " + request.param("name")));
            }
        };
    }

    private static PluginEndpoint throwingEndpoint() {
        return new PluginEndpoint() {
            @Override public String name() { return "boom"; }
            @Override public PluginEndpointResponse handle(PluginEndpointRequest request) {
                throw new RuntimeException("secret-detail-should-not-leak");
            }
        };
    }

    private static PluginEndpoint echoBodyEndpoint() {
        return new PluginEndpoint() {
            @Override public String name() { return "echo"; }
            @Override public PluginEndpointResponse handle(PluginEndpointRequest request) {
                return new PluginEndpointResponse(request.body(), "application/octet-stream");
            }
        };
    }

    private static PluginRegistry registryWith(RegisteredPlugin... plugins) {
        PluginRegistry registry = mock(PluginRegistry.class);
        when(registry.plugins(any())).thenReturn(List.of(plugins));
        return registry;
    }

    @Test
    void shouldInvokeEndpointAndReturnJson() {
        RegisteredPlugin plugin = mock(RegisteredPlugin.class);
        when(plugin.group()).thenReturn("io.kestra.plugin.ai");
        when(plugin.getEndpoints()).thenReturn(List.of(helloEndpoint()));

        PluginEndpointController controller = new PluginEndpointController(registryWith(plugin));
        HttpResponse<byte[]> response = controller.get(
            HttpRequest.GET("/api/v1/main/plugins/io.kestra.plugin.ai/endpoints/hello?name=toto"),
            "io.kestra.plugin.ai", "hello");

        assertThat(response.status().getCode()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("hello: toto");
        assertThat(response.getHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().contains(HttpHeaders.CONTENT_DISPOSITION)).isFalse();
    }

    @Test
    void shouldThrowNotFoundWhenGroupUnknown() {
        PluginEndpointController controller = new PluginEndpointController(registryWith());
        assertThatThrownBy(() -> controller.get(HttpRequest.GET("/x"), "unknown", "hello"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundWhenNameUnknown() {
        RegisteredPlugin plugin = mock(RegisteredPlugin.class);
        when(plugin.group()).thenReturn("io.kestra.plugin.ai");
        when(plugin.getEndpoints()).thenReturn(List.of(helloEndpoint()));

        PluginEndpointController controller = new PluginEndpointController(registryWith(plugin));
        assertThatThrownBy(() -> controller.get(HttpRequest.GET("/x"), "io.kestra.plugin.ai", "nope"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldReturnServerErrorWhenHandlerThrows() {
        RegisteredPlugin plugin = mock(RegisteredPlugin.class);
        when(plugin.group()).thenReturn("io.kestra.plugin.ai");
        when(plugin.getEndpoints()).thenReturn(List.of(throwingEndpoint()));

        PluginEndpointController controller = new PluginEndpointController(registryWith(plugin));
        HttpResponse<byte[]> response = controller.get(
            HttpRequest.GET("/api/v1/main/plugins/io.kestra.plugin.ai/endpoints/boom"),
            "io.kestra.plugin.ai", "boom");

        assertThat(response.status().getCode()).isGreaterThanOrEqualTo(500);
        String body = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("secret-detail-should-not-leak");
        assertThat(body).contains("failed to process the request");
    }

    @Test
    void shouldPassBodyToHandlerOnPost() {
        RegisteredPlugin plugin = mock(RegisteredPlugin.class);
        when(plugin.group()).thenReturn("io.kestra.plugin.ai");
        when(plugin.getEndpoints()).thenReturn(List.of(echoBodyEndpoint()));

        PluginEndpointController controller = new PluginEndpointController(registryWith(plugin));
        byte[] body = "hello-body".getBytes(StandardCharsets.UTF_8);
        HttpResponse<byte[]> response = controller.post(
            HttpRequest.POST("/api/v1/main/plugins/io.kestra.plugin.ai/endpoints/echo", body),
            "io.kestra.plugin.ai", "echo", body);

        assertThat(response.status().getCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(body);
        assertThat(response.getHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment");
    }
}
