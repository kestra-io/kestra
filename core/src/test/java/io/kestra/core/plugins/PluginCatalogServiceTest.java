package io.kestra.core.plugins;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.utils.ExecutorsUtils;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @SuppressWarnings("unchecked")
    void shouldReturnPluginManifests() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE")
                    )
                )
            );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils, null);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Serdes");
        assertThat(result.getFirst().groupId()).isEqualTo("io.kestra.plugin");
        assertThat(result.getFirst().artifactId()).isEqualTo("plugin-serdes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFilterCoreAndEEPluginsWhenCommunityOnly() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "core", "title", "Core", "group", "io.kestra.core", "license", "OPENSOURCE"),
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE"),
                        Map.of("name", "plugin-ee-only", "title", "EE Only", "group", "io.kestra.plugin.ee", "license", "EE")
                    )
                )
            );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils, null);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().artifactId()).isEqualTo("plugin-serdes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeEEPluginsWhenNotCommunityOnly() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE"),
                        Map.of("name", "plugin-ee-only", "title", "EE Only", "group", "io.kestra.plugin.ee", "license", "EE")
                    )
                )
            );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils, null);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyListWhenApiCallFails() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenThrow(new RuntimeException("API unavailable"));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils, null);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).isEmpty();
    }

    // -- resolveVersions() contract --

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveLatestVersionForKnownArtifact() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        new PluginCatalogService.ApiPluginArtifact("io.kestra.plugin", "plugin-serdes", "OPENSOURCE", List.of("0.21.0", "0.20.0"))
                    )
                )
            );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils, null);
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
    @SuppressWarnings("unchecked")
    void shouldResolveSpecificVersionWhenAvailable() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        new PluginCatalogService.ApiPluginArtifact("io.kestra.plugin", "plugin-serdes", "OPENSOURCE", List.of("0.21.0", "0.20.0"))
                    )
                )
            );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils, null);
        PluginArtifact artifact = new PluginArtifact("io.kestra.plugin", "plugin-serdes", "jar", null, "0.20.0", null);

        // When
        List<PluginResolutionResult> results = service.resolveVersions(List.of(artifact));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().resolved()).isTrue();
        assertThat(results.getFirst().version()).isEqualTo("0.20.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotResolveUnknownArtifact() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of()));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils, null);
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
        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils, null);

        // When
        List<PluginResolutionResult> results = service.resolveVersions(List.of());

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveIconLazilyForKnownArtifact() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin.serdes", "license", "OPENSOURCE")
                    )
                )
            );
        when(blockingClient.exchange(any(HttpRequest.class), eq(String.class)))
            .thenReturn(HttpResponse.ok("<svg>currentColor</svg>"));

        PluginCatalogService service = new PluginCatalogService(httpClient, true, false, executorsUtils, null);

        // When
        Optional<byte[]> icon = service.icon("io.kestra.plugin", "plugin-serdes");

        // Then
        assertThat(icon).isPresent();
        assertThat(new String(icon.get(), StandardCharsets.UTF_8)).isEqualTo("<svg>currentColor</svg>");
    }

    @Test
    void shouldReturnEmptyIconWhenIconResolutionDisabled() {
        // Given
        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils, null);

        // When
        Optional<byte[]> icon = service.icon("io.kestra.plugin", "plugin-serdes");

        // Then
        assertThat(icon).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyIconForUnknownArtifact() {
        // Given
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin.serdes", "license", "OPENSOURCE")
                    )
                )
            );

        PluginCatalogService service = new PluginCatalogService(httpClient, true, false, executorsUtils, null);

        // When
        Optional<byte[]> icon = service.icon("io.kestra.plugin", "plugin-unknown");

        // Then
        assertThat(icon).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveIconLazilyByGroup() {
        // Given
        when(blockingClient.exchange(any(HttpRequest.class), eq(String.class)))
            .thenReturn(HttpResponse.ok("<svg>currentColor</svg>"));

        PluginCatalogService service = new PluginCatalogService(httpClient, true, false, executorsUtils, null);

        // When
        Optional<byte[]> icon = service.icon("io.kestra.plugin.serdes");

        // Then
        assertThat(icon).isPresent();
        assertThat(new String(icon.get(), StandardCharsets.UTF_8)).isEqualTo("<svg>currentColor</svg>");
    }

    @Test
    void shouldReturnEmptyIconByGroupWhenIconResolutionDisabled() {
        // Given
        PluginCatalogService service = new PluginCatalogService(httpClient, false, false, executorsUtils, null);

        // When
        Optional<byte[]> icon = service.icon("io.kestra.plugin.serdes");

        // Then
        assertThat(icon).isEmpty();
    }

    @Test
    void shouldReturnEmptyIconForNullGroup() {
        // Given
        PluginCatalogService service = new PluginCatalogService(httpClient, true, false, executorsUtils, null);

        // When
        Optional<byte[]> icon = service.icon((String) null);

        // Then
        assertThat(icon).isEmpty();
    }

    // -- local catalog entries carried by the schema bundle --

    @Test
    void shouldExtendHostedCatalogWithBundleEntries() {
        // Given — the hosted catalog knows plugin-serdes, the bundle also carries an in-house plugin
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin.serdes", "license", "OPENSOURCE")
                    )
                )
            );
        PluginSchemaBundleService schemaBundleService = mock(PluginSchemaBundleService.class);
        when(schemaBundleService.catalogEntries()).thenReturn(
            List.of(new PluginCatalogService.PluginManifest("In-house", "com.acme.plugin", "plugin-acme", "com.acme.plugin.acme"))
        );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils, schemaBundleService);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result)
            .extracting(PluginCatalogService.PluginManifest::artifactId)
            .containsExactlyInAnyOrder("plugin-serdes", "plugin-acme");
    }

    @Test
    void shouldPreferHostedEntryOverBundleEntryForSameArtifact() {
        // Given — the same artifact on both sides: the hosted one carries the authoritative metadata
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin.serdes", "license", "OPENSOURCE")
                    )
                )
            );
        PluginSchemaBundleService schemaBundleService = mock(PluginSchemaBundleService.class);
        when(schemaBundleService.catalogEntries()).thenReturn(
            List.of(new PluginCatalogService.PluginManifest("Stale", "io.kestra.plugin", "plugin-serdes", "io.kestra.plugin.stale"))
        );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils, schemaBundleService);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).singleElement().satisfies(manifest ->
        {
            assertThat(manifest.artifactId()).isEqualTo("plugin-serdes");
            assertThat(manifest.group()).isEqualTo("io.kestra.plugin.serdes");
        });
    }

    @Test
    void shouldIgnoreIncompleteBundleEntries() {
        // Given — an entry without a Java package group cannot back a type lookup
        when(blockingClient.exchange(any(), any(Argument.class))).thenReturn(HttpResponse.ok(List.of()));
        PluginSchemaBundleService schemaBundleService = mock(PluginSchemaBundleService.class);
        when(schemaBundleService.catalogEntries()).thenReturn(
            List.of(new PluginCatalogService.PluginManifest("Broken", "com.acme.plugin", "plugin-acme", null))
        );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true, executorsUtils, schemaBundleService);

        // When / Then
        assertThat(service.get()).isEmpty();
    }
}
