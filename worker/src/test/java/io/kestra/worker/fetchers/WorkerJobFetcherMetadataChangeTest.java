package io.kestra.worker.fetchers;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.worker.MetadataChangePayload;
import io.kestra.core.worker.WorkerBroadcastEvent;
import io.kestra.core.worker.WorkerMetadataChangeHandler;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;
import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WorkerJobFetcherMetadataChangeTest {

    @SuppressWarnings("unchecked")
    private WorkerJobFetcher newFetcher(List<WorkerMetadataChangeHandler> handlers) {
        return new WorkerJobFetcher(
            mock(WorkerControllerServiceStub.class),
            mock(GrpcChannelManager.class),
            mock(WorkerQueueRegistry.class),
            mock(ExecutionKilledManager.class),
            (BroadcastQueueInterface<ClusterEvent>) mock(BroadcastQueueInterface.class),
            handlers,
            new GrpcConfiguration(false, 10485760)
        );
    }

    @Test
    void shouldFanOutMetadataChangeEventToEveryHandler() {
        // Given
        WorkerMetadataChangeHandler h1 = mock(WorkerMetadataChangeHandler.class);
        WorkerMetadataChangeHandler h2 = mock(WorkerMetadataChangeHandler.class);
        WorkerJobFetcher fetcher = newFetcher(List.of(h1, h2));

        MetadataChangePayload payload = new MetadataChangePayload(
            MetadataChangePayload.Type.NAMESPACE, "tenant-a", "prod.team");

        // When
        fetcher.onBroadcastEvent(new WorkerBroadcastEvent.MetadataChangeEvent(payload));

        // Then
        verify(h1).onMetadataChange(payload);
        verify(h2).onMetadataChange(payload);
    }

    @Test
    void shouldBeNoOpWhenNoHandlers() {
        // Given
        WorkerJobFetcher fetcher = newFetcher(List.of());

        MetadataChangePayload payload = new MetadataChangePayload(
            MetadataChangePayload.Type.TENANT, "tenant-a", null);

        // When / Then — must not throw
        assertThatNoException().isThrownBy(() ->
            fetcher.onBroadcastEvent(new WorkerBroadcastEvent.MetadataChangeEvent(payload)));
    }

    @Test
    void shouldIsolateHandlerFailuresFromEachOther() {
        // Given
        WorkerMetadataChangeHandler failing = mock(WorkerMetadataChangeHandler.class);
        WorkerMetadataChangeHandler healthy = mock(WorkerMetadataChangeHandler.class);
        doThrow(new RuntimeException("boom")).when(failing).onMetadataChange(org.mockito.ArgumentMatchers.any());
        WorkerJobFetcher fetcher = newFetcher(List.of(failing, healthy));

        MetadataChangePayload payload = new MetadataChangePayload(
            MetadataChangePayload.Type.FLOW, "tenant-a", "prod.team");

        // When
        fetcher.onBroadcastEvent(new WorkerBroadcastEvent.MetadataChangeEvent(payload));

        // Then — healthy still invoked despite failing handler
        verify(healthy).onMetadataChange(payload);
    }

    @Test
    void shouldStillRouteKillEvents() {
        // Given
        WorkerMetadataChangeHandler handler = mock(WorkerMetadataChangeHandler.class);
        ExecutionKilledManager killed = mock(ExecutionKilledManager.class);
        WorkerJobFetcher fetcher = new WorkerJobFetcher(
            mock(WorkerControllerServiceStub.class),
            mock(GrpcChannelManager.class),
            mock(WorkerQueueRegistry.class),
            killed,
            null,
            List.of(handler),
            new GrpcConfiguration(false, 10485760)
        );

        io.kestra.core.models.executions.ExecutionKilled k = io.kestra.core.models.executions.ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .state(io.kestra.core.models.executions.ExecutionKilled.State.EXECUTED)
            .build();

        // When
        fetcher.onBroadcastEvent(new WorkerBroadcastEvent.KillEvent(k));

        // Then
        verify(killed).onKillReceived(k);
        verifyNoInteractions(handler);
    }
}
