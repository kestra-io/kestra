package io.kestra.core.plugins;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.utils.ExecutorsUtils;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginCatalogServiceTest {

    private HttpClient httpClient;
    private BlockingHttpClient blockingClient;
    private ExecutorsUtils executorsUtils;

    @BeforeEach
    void setUp() {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("1.0.0");
        KestraContext.setContext(kestraContext);

        httpClient = mock(HttpClient.class);
        blockingClient = mock(BlockingHttpClient.class);
        executorsUtils = mock(ExecutorsUtils.class);
        when(httpClient.toBlocking()).thenReturn(blockingClient);
    }

    @AfterEach
    void tearDown() {
        KestraContext.setContext(null);
    }

    // -- get() contract --

    @Test
    void shouldReturnPluginManifests() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of(
                Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE")
            )));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Serdes");
        assertThat(result.getFirst().groupId()).isEqualTo("io.kestra.plugin");
        assertThat(result.getFirst().artifactId()).isEqualTo("plugin-serdes");
    }

    @Test
    void shouldFilterCoreAndEEPluginsWhenCommunityOnly() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of(
                Map.of("name", "core", "title", "Core", "group", "io.kestra.core", "license", "OPENSOURCE"),
                Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE"),
                Map.of("name", "plugin-ee-only", "title", "EE Only", "group", "io.kestra.plugin.ee", "license", "EE")
            )));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().artifactId()).isEqualTo("plugin-serdes");
    }

    @Test
    void shouldIncludeEEPluginsWhenNotCommunityOnly() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of(
                Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE"),
                Map.of("name", "plugin-ee-only", "title", "EE Only", "group", "io.kestra.plugin.ee", "license", "EE")
            )));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void shouldReturnEmptyListWhenApiCallFails() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenThrow(new RuntimeException("API unavailable"));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).isEmpty();
    }

    // -- resolveVersions() contract --

    @Test
    void shouldResolveLatestVersionForKnownArtifact() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of(
                new PluginCatalogService.ApiPluginArtifact("io.kestra.plugin", "plugin-serdes", "OPENSOURCE", List.of("0.21.0", "0.20.0"))
            )));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils);
        PluginArtifact artifact = new PluginArtifact("io.kestra.plugin", "plugin-serdes", "jar", null, "LATEST", null);

        // When
        List<PluginResolutionResult> results = service.resolveVersions(List.of(artifact));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().resolved()).isTrue();
        assertThat(results.getFirst().version()).isEqualTo("0.21.0");
        assertThat(results.getFirst().versions()).containsExactly("0.21.0", "0.20.0");
    }

    @Test
    void shouldResolveSpecificVersionWhenAvailable() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of(
                new PluginCatalogService.ApiPluginArtifact("io.kestra.plugin", "plugin-serdes", "OPENSOURCE", List.of("0.21.0", "0.20.0"))
            )));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils);
        PluginArtifact artifact = new PluginArtifact("io.kestra.plugin", "plugin-serdes", "jar", null, "0.20.0", null);

        // When
        List<PluginResolutionResult> results = service.resolveVersions(List.of(artifact));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().resolved()).isTrue();
        assertThat(results.getFirst().version()).isEqualTo("0.20.0");
    }

    @Test
    void shouldNotResolveUnknownArtifact() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of()));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils);
        PluginArtifact artifact = new PluginArtifact("io.kestra.plugin", "plugin-unknown", "jar", null, "1.0.0", null);

        // When
        List<PluginResolutionResult> results = service.resolveVersions(List.of(artifact));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().resolved()).isFalse();
        assertThat(results.getFirst().version()).isNull();
        assertThat(results.getFirst().versions()).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenResolvingEmptyArtifacts() {
        // Given
        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils);

        // When
        List<PluginResolutionResult> results = service.resolveVersions(List.of());

        // Then
        assertThat(results).isEmpty();
    }
}
