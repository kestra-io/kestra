package io.kestra.webserver.controllers.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.plugins.PluginArtifact;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@Property(name = "kestra.plugins.auto-install.enabled", value = "false")
class PluginInstallDisabledTest {

    public static final String PATH = "/api/v1/plugins";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldRejectInstallWhenAutoInstallDisabled() {
        // Given - kestra.plugins.auto-install.enabled=false is set via @Property
        List<PluginArtifact> artifacts = List.of(
            PluginArtifact.builder().groupId("io.kestra.plugin").artifactId("plugin-notifications").version("LATEST").build()
        );

        // When
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST(PATH + "/install", artifacts), Void.class)
        );

        // Then - a disabled instance must never resolve/download/load arbitrary artifacts, even for an authenticated caller
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    @Test
    void shouldReturnEmptyDetectionWhenAutoInstallDisabled() {
        // Given - kestra.plugins.auto-install.enabled=false is set via @Property
        String flowYaml = "id: test\nnamespace: test\ntasks:\n  - id: t\n    type: io.kestra.plugin.unknown.Task\n";

        // When
        var result = client.toBlocking().retrieve(
            HttpRequest.POST(PATH + "/auto-install/detect", flowYaml).contentType("text/plain"),
            io.kestra.core.plugins.PluginAutoInstallDetectResult.class
        );

        // Then
        assertThat(result.enabled()).isFalse();
        assertThat(result.missingTypes()).isEmpty();
    }
}
