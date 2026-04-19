package io.kestra.webserver.services.posthog;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.EditionProvider;
import io.kestra.core.utils.VersionProvider;

import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosthogServiceTest {

    @Test
    void shouldNotThrowWhenApiIsUnreachable() {
        // Given
        HttpClient httpClient = mock(HttpClient.class);
        BlockingHttpClient blockingClient = mock(BlockingHttpClient.class);
        when(httpClient.toBlocking()).thenReturn(blockingClient);
        when(blockingClient.retrieve(anyString(), any()))
            .thenThrow(new RuntimeException("Connection timed out"));

        PosthogService service = new PosthogService(
            mock(InstanceService.class),
            mock(VersionProvider.class),
            mock(EditionProvider.class),
            httpClient
        );

        // When/Then: capture() must not throw even when API is unreachable
        assertThatCode(() -> service.capture("user-1", "event", Map.of()))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAttemptInitOnlyOnce() {
        // Given
        HttpClient httpClient = mock(HttpClient.class);
        BlockingHttpClient blockingClient = mock(BlockingHttpClient.class);
        when(httpClient.toBlocking()).thenReturn(blockingClient);
        when(blockingClient.retrieve(anyString(), any()))
            .thenThrow(new RuntimeException("Connection timed out"));

        PosthogService service = new PosthogService(
            mock(InstanceService.class),
            mock(VersionProvider.class),
            mock(EditionProvider.class),
            httpClient
        );

        // When: multiple capture calls
        service.capture("user-1", "event1", Map.of());
        service.capture("user-2", "event2", Map.of());
        service.capture("user-3", "event3", Map.of());

        // Then: only one init attempt was made despite multiple captures
        verify(blockingClient, times(1)).retrieve(anyString(), any());
    }

    @Test
    void shouldNotThrowOnShutdownWhenNotInitialized() {
        // Given: service created but capture() never called — postHog never initialized
        PosthogService service = new PosthogService(
            mock(InstanceService.class),
            mock(VersionProvider.class),
            mock(EditionProvider.class),
            mock(HttpClient.class)
        );

        // When/Then: shutdown() must not throw even with null postHog
        assertThatCode(service::shutdown).doesNotThrowAnyException();
    }
}
