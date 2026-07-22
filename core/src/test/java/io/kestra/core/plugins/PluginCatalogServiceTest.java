package io.kestra.core.plugins;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.KestraContext;

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

    @BeforeEach
    void setUp() {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("1.3.0");
        KestraContext.setContext(kestraContext);

        httpClient = mock(HttpClient.class);
        blockingClient = mock(BlockingHttpClient.class);
        when(httpClient.toBlocking()).thenReturn(blockingClient);
    }

    @AfterEach
    void tearDown() {
        KestraContext.setContext(null);
    }

    @Test
    void shouldReturnPluginManifests() {
        // Given
        stubPluginList(
            Map.of("name", "plugin-serdes", "title", "Serdes", "group", "io.kestra.plugin", "license", "OPENSOURCE")
        );

        PluginCatalogService service = new PluginCatalogService(httpClient, false, true);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Serdes");
        assertThat(result.getFirst().groupId()).isEqualTo("io.kestra.plugin");
        assertThat(result.getFirst().artifactId()).isEqualTo("plugin-serdes");
    }

    /**
     * Regression test: the icon for each plugin is fetched from inside the stream that builds the
     * catalog. If a single icon request fails at transport level (timeout, connection reset, TLS
     * error, or a gateway that path-filters /icons/*), it must not discard the whole catalog.
     * Before the fix, the uncaught exception aborted load() and get() returned an empty list.
     */
    @Test
    void shouldKeepCatalogWhenSingleIconFetchFails() {
        // Given: two plugins, and the icon request fails at transport level for one of them.
        stubPluginList(
            Map.of("name", "plugin-good", "title", "Good", "group", "io.kestra.plugin.good", "license", "OPENSOURCE"),
            Map.of("name", "plugin-bad", "title", "Bad", "group", "io.kestra.plugin.bad", "license", "OPENSOURCE")
        );
        when(blockingClient.exchange(any(HttpRequest.class), eq(String.class)))
            .thenAnswer(invocation -> {
                HttpRequest<?> request = invocation.getArgument(0);
                if (request.getPath().contains("io.kestra.plugin.bad")) {
                    throw new RuntimeException("icon request failed (simulated read timeout)");
                }
                return HttpResponse.ok("<svg>icon</svg>");
            });

        PluginCatalogService service = new PluginCatalogService(httpClient, true, false);

        // When
        List<PluginCatalogService.PluginManifest> result = service.get();

        // Then: both plugins are still present; the failing icon simply degrades to null.
        assertThat(result).hasSize(2);
        assertThat(result)
            .filteredOn(m -> m.artifactId().equals("plugin-good"))
            .singleElement()
            .satisfies(m -> assertThat(m.icon()).isNotNull());
        assertThat(result)
            .filteredOn(m -> m.artifactId().equals("plugin-bad"))
            .singleElement()
            .satisfies(m -> assertThat(m.icon()).isNull());
    }

    @SafeVarargs
    private void stubPluginList(Map<String, Object>... plugins) {
        when(blockingClient.exchange(any(), any(Argument.class)))
            .thenReturn(HttpResponse.ok(List.of(plugins)));
    }
}
