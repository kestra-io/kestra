package io.kestra.worker.fetchers;

import java.time.Duration;
import java.util.ArrayList;
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
import io.kestra.core.runners.WorkerJob;
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

class WorkerJobFetcherPermitsTest {

    private final List<WorkerJobRequest> sent = new ArrayList<>();
    private final AtomicReference<ClientResponseObserver<WorkerJobRequest, WorkerJobResponse>> responseObserver = new AtomicReference<>();

    private WorkerJobFetcher fetcher;
    private WorkerQueue<WorkerJob> queue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        KestraContext.setContext(kestraContext);

        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        ClientCallStreamObserver<WorkerJobRequest> requestStream = mock(ClientCallStreamObserver.class);
        doAnswer(invocation ->
        {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(requestStream).onNext(any());
        doAnswer(invocation ->
        {
            ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> observer = invocation.getArgument(0);
            observer.beforeStart(requestStream);
            responseObserver.set(observer);
            return null;
        }).when(stub).streamWorkerJobs(any());

        WorkerContext context = new WorkerContext("worker-1", "group-1", 2, 0);
        queue = new InMemoryWorkerQueue<>(context.workerThreads());
        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerJob.class))).thenReturn(queue);

        fetcher = new WorkerJobFetcher(
            stub,
            mock(GrpcChannelManager.class),
            registry,
            mock(ExecutionKilledManager.class),
            null,
            List.of(),
            new GrpcConfiguration(false, 10485760)
        );
        fetcher.init(context);
    }

    @Test
    void shouldRequestOneJobPerIdleThreadWhenJobBufferIsZero() throws Exception {
        fetcher.doOnLoop();
        assertThat(sent.getLast().getPermits()).isEqualTo(2);
        assertThat(sent.getLast().getConnectionInfo().getMaxConcurrency()).isEqualTo(2);

        responseObserver.get().onNext(response("job-1", "job-2"));
        assertThat(sent.getLast().getPermits()).isZero();

        queue.poll(2, Duration.ZERO);

        fetcher.onJobCompleted("job-1");
        assertThat(sent.getLast().getPermits()).isEqualTo(1);
    }

    private static WorkerJobResponse response(String... jobIds) {
        WorkerJobResponse.Builder builder = WorkerJobResponse.newBuilder();
        for (String jobId : jobIds) {
            String json = """
                {"type":"task","taskRun":{"id":"%s"}}""".formatted(jobId);
            builder.addJobs(WorkerJobPayload.newBuilder().setJobId(jobId).setJobData(ByteString.copyFromUtf8(json)));
        }
        return builder.build();
    }
}
