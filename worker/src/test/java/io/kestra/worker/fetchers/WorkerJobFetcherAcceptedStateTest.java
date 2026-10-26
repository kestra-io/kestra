package io.kestra.worker.fetchers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.controller.grpc.WorkerJobPayload;
import io.kestra.controller.grpc.WorkerJobRequest;
import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.queues.InMemoryWorkerQueue;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerJobFetcherAcceptedStateTest {

    private final AtomicReference<ClientResponseObserver<WorkerJobRequest, WorkerJobResponse>> responseObserver = new AtomicReference<>();

    private WorkerQueue<WorkerJob> workerJobQueue;
    private WorkerQueue<WorkerTaskResult> workerTaskResultQueue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        KestraContext.setContext(kestraContext);

        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation ->
        {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> observer = invocation.getArgument(0);
            observer.beforeStart(requestStream);
            responseObserver.set(observer);
            return null;
        }).when(stub).streamWorkerJobs(any());

        WorkerContext context = new WorkerContext("worker-1", "group-1", 2, 0);
        workerJobQueue = new InMemoryWorkerQueue<>(context.workerThreads());
        workerTaskResultQueue = new InMemoryWorkerQueue<>(context.workerThreads());

        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerJob.class))).thenReturn(workerJobQueue);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerTaskResult.class))).thenReturn(workerTaskResultQueue);

        WorkerJobFetcher fetcher = new WorkerJobFetcher(
            stub,
            mock(GrpcChannelManager.class),
            registry,
            mock(ExecutionKilledManager.class),
            null,
            List.of(),
            new GrpcConfiguration(false, 10485760)
        );
        fetcher.init(context);
        fetcher.doOnLoop();
    }

    @Test
    void shouldMarkTaskAcceptedAndBufferItAccepted() throws Exception {
        // When
        responseObserver.get().onNext(response(taskJson("task-1", State.Type.SUBMITTED)));

        // Then
        WorkerTaskResult result = workerTaskResultQueue.poll(1, Duration.ZERO).getFirst();
        assertThat(result.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.ACCEPTED);

        WorkerTask queued = (WorkerTask) workerJobQueue.poll(1, Duration.ZERO).getFirst();
        assertThat(queued.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.ACCEPTED);
    }

    @Test
    void shouldNotMarkTaskAcceptedWhenNotSubmitted() throws Exception {
        // When
        responseObserver.get().onNext(response(taskJson("task-1", State.Type.RUNNING)));

        // Then
        assertThat(workerTaskResultQueue.poll(1, Duration.ZERO)).isEmpty();

        WorkerTask queued = (WorkerTask) workerJobQueue.poll(1, Duration.ZERO).getFirst();
        assertThat(queued.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
    }

    private static WorkerJobResponse response(String jobData) {
        return WorkerJobResponse.newBuilder()
            .addJobs(WorkerJobPayload.newBuilder().setJobId("job-1").setJobData(ByteString.copyFromUtf8(jobData)))
            .build();
    }

    private static String taskJson(String taskRunId, State.Type state) {
        return """
            {"type":"task","taskRun":{"id":"%s","state":{"current":"%s","histories":[{"state":"%s","date":"2024-01-01T00:00:00Z"}]}}}"""
            .formatted(taskRunId, state.name(), state.name());
    }
}
