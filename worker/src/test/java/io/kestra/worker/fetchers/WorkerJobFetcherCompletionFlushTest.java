package io.kestra.worker.fetchers;

import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.controller.grpc.WorkerJobRequest;
import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkerJobFetcherCompletionFlushTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldFlushCompletionSignalImmediatelyWhenConnected() throws Exception {
        // Given — a fetcher with a connected request stream
        // The header factory reads the version off the global KestraContext.
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        KestraContext.setContext(kestraContext);

        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
        WorkerQueue<WorkerJob> queue = (WorkerQueue<WorkerJob>) mock(WorkerQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerJob.class))).thenReturn(queue);

        WorkerJobFetcher fetcher = new WorkerJobFetcher(
            stub,
            mock(GrpcChannelManager.class),
            registry,
            mock(ExecutionKilledManager.class),
            null,
            List.of(),
            new GrpcConfiguration(false, 10485760)
        );
        fetcher.init(new WorkerContext("worker-1", "group-1", 4));

        // Capture the request stream the moment the bidi stream opens.
        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation -> {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> responseObserver = invocation.getArgument(0);
            responseObserver.beforeStart(requestStream);
            return null;
        }).when(stub).streamWorkerJobs(any());

        // Open the stream (this sends the initial request), then forget that interaction.
        fetcher.doOnLoop();
        clearInvocations(requestStream);

        // When — a job reaches a terminal state on the worker
        fetcher.onJobCompleted("job-1");

        // Then — the completion is pushed to the controller right away, not on the next 100ms tick
        ArgumentCaptor<WorkerJobRequest> captor = ArgumentCaptor.forClass(WorkerJobRequest.class);
        verify(requestStream).onNext(captor.capture());
        assertThat(captor.getValue().getCompletedJobIdsList()).containsExactly("job-1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotFlushBeforeInitialRequestOnNewStream() throws Exception {
        // Given — a fetcher about to open a new stream
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        KestraContext.setContext(kestraContext);

        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
        WorkerQueue<WorkerJob> queue = (WorkerQueue<WorkerJob>) mock(WorkerQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        when(queue.capacity()).thenReturn(8);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerJob.class))).thenReturn(queue);

        WorkerJobFetcher fetcher = new WorkerJobFetcher(
            stub,
            mock(GrpcChannelManager.class),
            registry,
            mock(ExecutionKilledManager.class),
            null,
            List.of(),
            new GrpcConfiguration(false, 10485760)
        );
        fetcher.init(new WorkerContext("worker-1", "group-1", 4));

        List<WorkerJobRequest> sent = new ArrayList<>();
        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(requestStream).onNext(any());

        doAnswer(invocation -> {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> responseObserver = invocation.getArgument(0);
            responseObserver.beforeStart(requestStream);
            // Simulate a consumer finishing a job in the window where the request
            // observer is already published but the initial request is not yet sent.
            fetcher.onJobCompleted("job-race");
            return null;
        }).when(stub).streamWorkerJobs(any());

        // When — the stream opens
        fetcher.doOnLoop();

        // Then — the first message on the wire must be the connection-info request
        // (the controller rejects any other first message), carrying the completion.
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().hasConnectionInfo()).isTrue();
        assertThat(sent.getFirst().getCompletedJobIdsList()).containsExactly("job-race");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldQueueCompletionSignalWhenDisconnectedAndFlushOnNextRequest() throws Exception {
        // Given — a fetcher with no live stream yet
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        KestraContext.setContext(kestraContext);

        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
        WorkerQueue<WorkerJob> queue = (WorkerQueue<WorkerJob>) mock(WorkerQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        when(queue.capacity()).thenReturn(8);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerJob.class))).thenReturn(queue);

        WorkerJobFetcher fetcher = new WorkerJobFetcher(
            stub,
            mock(GrpcChannelManager.class),
            registry,
            mock(ExecutionKilledManager.class),
            null,
            List.of(),
            new GrpcConfiguration(false, 10485760)
        );
        fetcher.init(new WorkerContext("worker-1", "group-1", 4));

        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation -> {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> responseObserver = invocation.getArgument(0);
            responseObserver.beforeStart(requestStream);
            return null;
        }).when(stub).streamWorkerJobs(any());

        // When — a job completes before any stream exists
        fetcher.onJobCompleted("job-2");

        // Then — nothing is sent (no stream), but the signal is retained...
        verifyNoInteractions(requestStream);

        // ...and is flushed on the next outgoing request when the stream opens.
        fetcher.doOnLoop();

        ArgumentCaptor<WorkerJobRequest> captor = ArgumentCaptor.forClass(WorkerJobRequest.class);
        verify(requestStream).onNext(captor.capture());
        assertThat(captor.getValue().getCompletedJobIdsList()).containsExactly("job-2");
    }
}
