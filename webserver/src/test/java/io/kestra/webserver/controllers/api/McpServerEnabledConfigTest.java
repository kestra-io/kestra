package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.mcp.platform-server.enabled", value = "true")
class McpServerEnabledConfigTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldReportMcpServerEnabledWhenPlatformServerIsEnabled() {
        var response = client.toBlocking().retrieve(GET("/api/v1/configs"), MiscController.Configuration.class);

        assertThat(response.getIsMcpServerEnabled()).isTrue();
    }
}
