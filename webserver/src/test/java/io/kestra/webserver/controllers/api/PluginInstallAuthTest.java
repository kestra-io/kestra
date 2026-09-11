package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginAutoInstallDetectResult;
import io.kestra.webserver.filter.TestAuthFilter;
import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Asserts that the plugin install endpoints deny unauthenticated access and accept authenticated
 * callers. {@link TestAuthFilter} silently authenticates every test request, so it is disabled
 * around the unauthenticated assertions.
 */
@KestraTest
@Property(name = "kestra.plugins.auto-install.enabled", value = "true")
class PluginInstallAuthTest {

    private static final String PATH = "/api/v1/plugins";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    BasicAuthService basicAuthService;

    @Inject
    BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @BeforeEach
    void disableTestAuth() {
        // Make sure the configured credentials exist so the positive test can authenticate.
        if (basicAuthService.credentials() == null) {
            basicAuthService.init();
        }
        TestAuthFilter.ENABLED = false;
    }

    @AfterEach
    void enableTestAuth() {
        TestAuthFilter.ENABLED = true;
    }

    @Test
    void shouldDenyInstallWhenUnauthenticated() {
        // Given
        List<PluginArtifact> artifacts = List.of(
            PluginArtifact.builder().groupId("io.kestra.plugin").artifactId("plugin-compress").version("LATEST").build()
        );

        // When
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST(PATH + "/install", artifacts), Void.class)
        );

        // Then
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldDenyDetectWhenUnauthenticated() {
        // Given
        String flowYaml = "id: test\nnamespace: test\ntasks:\n  - id: t\n    type: io.kestra.plugin.unknown.Task\n";

        // When
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST(PATH + "/auto-install/detect", flowYaml).contentType("text/plain"), Void.class)
        );

        // Then
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldDenyInstallJobLookupWhenUnauthenticated() {
        // When
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.GET(PATH + "/install/" + UUID.randomUUID()), Void.class)
        );

        // Then
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldAllowDetectWhenAuthenticated() {
        // Given
        String flowYaml = "id: test\nnamespace: test\ntasks:\n  - id: t\n    type: io.kestra.plugin.unknown.Task\n";

        // When
        PluginAutoInstallDetectResult result = client.toBlocking().retrieve(
            HttpRequest.POST(PATH + "/auto-install/detect", flowYaml)
                .contentType("text/plain")
                .basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()),
            PluginAutoInstallDetectResult.class
        );

        // Then
        assertThat(result.enabled()).isTrue();
        assertThat(result.missingTypes()).containsExactly("io.kestra.plugin.unknown.Task");
    }
}
