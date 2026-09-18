package io.kestra.core.plugins;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.KestraContext;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginCatalogServiceTest {

    private HttpClient httpClient;
    private BlockingHttpClient blockingClient;

    @BeforeEach
    void setUp() {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("1.0.0");
        KestraContext.setContext(kestraContext);

        httpClient = mock(HttpClient.class);
        blockingClient = mock(BlockingHttpClient.class);
        when(httpClient.toBlocking()).thenReturn(blockingClient);
    }

    @AfterEach
    void tearDown() {
        KestraContext.setContext(null);
        PluginCatalogService.retryDelay = Duration.ofSeconds(30);
    }

    // -- get() contract --

    @Test
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

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().artifactId()).isEqualTo("plugin-serdes");
        assertThat(result.getFirst().groupId()).isEqualTo("io.kestra.plugin");
    }

    @Test
    void shouldRetryAfterFailedLoadInsteadOfCachingTheFailure() {
        // Given: first call to the API fails, second succeeds
        PluginCatalogService.retryDelay = Duration.ZERO;
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenThrow(new RuntimeException("api.kestra.io is restarting"))
            .thenReturn(
                HttpResponse.ok(
                    List.of(
                        Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE")
                    )
                )
            );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true);

        // When
        List<PluginCatalogService.PluginManifest> firstResult = service.get();
        List<PluginCatalogService.PluginManifest> secondResult = service.get();

        // Then
        assertThat(firstResult).isEmpty();
        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.getFirst().artifactId()).isEqualTo("plugin-serdes");
    }

    @Test
    void shouldNotRetryBeforeRetryDelayElapsed() {
        // Given: a failing API and a long retry delay
        PluginCatalogService.retryDelay = Duration.ofHours(1);
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenThrow(new RuntimeException("api.kestra.io is restarting"));

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true);

        // When
        service.get();
        service.get();

        // Then: only one HTTP call was made, the second get() served the (empty) cache without hammering the API
        verify(blockingClient, times(1)).exchange(any(), any(Argument.class));
    }
}
