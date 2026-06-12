package io.kestra.worker.fetchers;

import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkerJobFetcherBackoffTest {

    private WorkerJobFetcher newFetcher() {
        return new WorkerJobFetcher(
            mock(WorkerControllerServiceStub.class),
            mock(GrpcChannelManager.class),
            mock(WorkerQueueRegistry.class),
            mock(ExecutionKilledManager.class),
            null,
            List.of(),
            mock(GrpcConfiguration.class)
        );
    }

    @Test
    void shouldGrowBackoffExponentiallyAndCapAt30s() {
        WorkerJobFetcher fetcher = newFetcher();

        // Each failure without an intervening confirmed connection must double the delay,
        // capped at 30s — this is the behaviour the flat-500ms hot-loop regression broke.
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(500L);
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(1_000L);
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(2_000L);
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(4_000L);
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(8_000L);
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(16_000L);
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(30_000L);
        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(30_000L);
    }

    @Test
    void shouldResetBackoffToMinimumOnConfirmedConnection() {
        WorkerJobFetcher fetcher = newFetcher();

        fetcher.scheduleReconnectBackoff();
        fetcher.scheduleReconnectBackoff(); // delay now at 2s

        // A confirmed (re-)connection resets the backoff; the next failure starts from the minimum.
        fetcher.resetBackoff();

        assertThat(fetcher.scheduleReconnectBackoff()).isEqualTo(500L);
    }
}
