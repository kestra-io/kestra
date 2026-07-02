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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that pausing job intake (maintenance / cordon) advertises zero permits to the controller
 * instead of parking the fetch loop, so the controller stops dispatching while the stream stays
 * alive. Resuming re-advertises the worker's remaining capacity.
 */
class WorkerJobFetcherPauseTest {

    @SuppressWarnings("unchecked")
    private WorkerJobFetcher newFetcher(WorkerControllerServiceStub stub, WorkerQueue<WorkerJob> queue) {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        KestraContext.setContext(kestraContext);

        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
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
        return fetcher;
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldAdvertiseZeroPermitsWhenPausedWhileConnected() throws Exception {
        // Given — a connected fetcher that has advertised capacity
        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        WorkerQueue<WorkerJob> queue = (WorkerQueue<WorkerJob>) mock(WorkerQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        WorkerJobFetcher fetcher = newFetcher(stub, queue);

        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation -> {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> responseObserver = invocation.getArgument(0);
            responseObserver.beforeStart(requestStream);
            return null;
        }).when(stub).streamWorkerJobs(any());

        fetcher.doOnLoop(); // opens the stream and sends the initial request (permits=5)
        clearInvocations(requestStream);

        // When — intake is paused
        fetcher.pause();

        // Then — a zero-permit update is pushed immediately so the controller stops dispatching
        ArgumentCaptor<WorkerJobRequest> captor = ArgumentCaptor.forClass(WorkerJobRequest.class);
        verify(requestStream).onNext(captor.capture());
        assertThat(captor.getValue().getPermits()).isZero();
        assertThat(fetcher.isPaused()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReAdvertiseRemainingCapacityWhenResumed() throws Exception {
        // Given — a connected, paused fetcher
        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        WorkerQueue<WorkerJob> queue = (WorkerQueue<WorkerJob>) mock(WorkerQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        WorkerJobFetcher fetcher = newFetcher(stub, queue);

        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation -> {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> responseObserver = invocation.getArgument(0);
            responseObserver.beforeStart(requestStream);
            return null;
        }).when(stub).streamWorkerJobs(any());

        fetcher.doOnLoop();
        fetcher.pause();
        clearInvocations(requestStream);

        // When — intake resumes
        fetcher.resume();

        // Then — the worker re-advertises its remaining capacity
        ArgumentCaptor<WorkerJobRequest> captor = ArgumentCaptor.forClass(WorkerJobRequest.class);
        verify(requestStream).onNext(captor.capture());
        assertThat(captor.getValue().getPermits()).isEqualTo(5);
        assertThat(fetcher.isPaused()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldSendZeroPermitsOnInitialRequestWhenPausedBeforeConnect() throws Exception {
        // Given — a fetcher paused before its stream is opened (worker starting cordoned / in maintenance)
        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        WorkerQueue<WorkerJob> queue = (WorkerQueue<WorkerJob>) mock(WorkerQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        when(queue.capacity()).thenReturn(8);
        WorkerJobFetcher fetcher = newFetcher(stub, queue);

        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation -> {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> responseObserver = invocation.getArgument(0);
            responseObserver.beforeStart(requestStream);
            return null;
        }).when(stub).streamWorkerJobs(any());

        fetcher.pause();

        // When — the stream opens
        fetcher.doOnLoop();

        // Then — the initial connection request advertises zero capacity, so the worker registers
        // (and can still receive kills / cluster events / the uncordon) but is dispatched no jobs.
        ArgumentCaptor<WorkerJobRequest> captor = ArgumentCaptor.forClass(WorkerJobRequest.class);
        verify(requestStream).onNext(captor.capture());
        assertThat(captor.getValue().hasConnectionInfo()).isTrue();
        assertThat(captor.getValue().getPermits()).isZero();
    }
}
