package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginAutoInstallDetectResult;

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
@Property(name = "kestra.plugins.auto-install.enabled", value = "true")
class PluginInstallEnabledTest {

    public static final String PATH = "/api/v1/plugins";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldRejectArtifactsNotInTheCatalogWhenAutoInstallEnabled() {
        // Given — a Maven coordinate the plugin catalog does not map
        List<PluginArtifact> artifacts = List.of(
            PluginArtifact.builder().groupId("com.evil").artifactId("backdoor").version("1.0.0").build()
        );

        // When
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST(PATH + "/install", artifacts), Void.class)
        );

        // Then — the endpoint must never resolve/download/class-load an arbitrary coordinate
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void shouldReturnNotFoundForUnknownInstallJob() {
        // When
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.GET(PATH + "/install/" + UUID.randomUUID()), Void.class)
        );

        // Then
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldReportMissingTypesInDetectionWhenAutoInstallEnabled() {
        // Given
        String flowYaml = "id: test\nnamespace: test\ntasks:\n  - id: t\n    type: io.kestra.plugin.unknown.Task\n";

        // When
        PluginAutoInstallDetectResult result = client.toBlocking().retrieve(
            HttpRequest.POST(PATH + "/auto-install/detect", flowYaml).contentType("text/plain"),
            PluginAutoInstallDetectResult.class
        );

        // Then — the type is missing from the registry; whether an artifact resolves depends on the catalog
        assertThat(result.enabled()).isTrue();
        assertThat(result.missingTypes()).containsExactly("io.kestra.plugin.unknown.Task");
    }
}
