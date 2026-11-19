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
import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerJobStreamConfig;
import io.kestra.worker.queues.InMemoryWorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;

import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerJobFetcherReconciliationTest {

    private static final Duration SYNC_TIMEOUT = Duration.ofMillis(10);

    private final List<WorkerJobRequest> sent = new ArrayList<>();
    private final AtomicReference<ClientResponseObserver<WorkerJobRequest, WorkerJobResponse>> responseObserver = new AtomicReference<>();
    private ClientCallStreamObserver<WorkerJobRequest> requestStream;
    private WorkerJobFetcher fetcher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        KestraContext.setContext(kestraContext);
        meterRegistry = new SimpleMeterRegistry();

        WorkerControllerServiceStub stub = mock(WorkerControllerServiceStub.class);
        requestStream = mock(ClientCallStreamObserver.class);
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

        WorkerContext context = new WorkerContext("worker-1", "group-1", 4);
        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerJob.class))).thenReturn(new InMemoryWorkerQueue<>(8));

        fetcher = new WorkerJobFetcher(
            stub,
            mock(GrpcChannelManager.class),
            registry,
            mock(ExecutionKilledManager.class),
            null,
            List.of(),
            new GrpcConfiguration(false, 10485760),
            new WorkerJobStreamConfig(Duration.ZERO, SYNC_TIMEOUT),
            new MetricRegistry(meterRegistry, new MetricConfig())
        );
        fetcher.init(context);
    }

    @Test
    void shouldReportHeldJobsWithANewGenerationWhenReconnecting() throws Exception {
        // Given
        fetcher.doOnLoop();
        responseObserver.get().onNext(jobs("job-1"));

        // When
        responseObserver.get().onError(Status.UNAVAILABLE.asRuntimeException());
        fetcher.resetBackoff();
        fetcher.doOnLoop();

        // Then
        WorkerJobRequest initialRequest = sent.getLast();
        assertThat(initialRequest.getConnectionInfo().getStreamGeneration()).isEqualTo(2);
        assertThat(initialRequest.getSync().getHeldJobIdsList()).containsExactly("job-1");
    }

    @Test
    void shouldHoldJobUntilControllerAcceptsItsTerminalResult() throws Exception {
        // Given
        fetcher.doOnLoop();
        responseObserver.get().onNext(jobs("job-1"));

        // When
        fetcher.onJobCompleted("job-1");
        fetcher.doOnLoop();

        // Then
        assertThat(sent.getLast().getSync().getHeldJobIdsList()).containsExactly("job-1");

        // When
        fetcher.onTerminalResultAccepted("job-1");
        fetcher.doOnLoop();

        // Then
        assertThat(sent.getLast().hasSync()).isTrue();
        assertThat(sent.getLast().getSync().getHeldJobIdsList()).isEmpty();
    }

    @Test
    void shouldReconnectWhenControllerStopsAcknowledgingSyncs() throws Exception {
        // Given
        fetcher.doOnLoop();
        responseObserver.get().onNext(WorkerJobResponse.newBuilder().setSyncAck(1).build());

        // When
        Thread.sleep(SYNC_TIMEOUT.toMillis() * 5);
        fetcher.doOnLoop();

        // Then
        verify(requestStream).cancel(anyString(), any());

        // When
        responseObserver.get().onError(Status.CANCELLED.asRuntimeException());
        fetcher.resetBackoff();
        fetcher.doOnLoop();

        // Then
        assertThat(reconnections("sync_unacknowledged")).isEqualTo(1.0);
        assertThat(reconnections("stream_error")).isZero();
    }

    @Test
    void shouldNotReconnectWhenNoControllerEverAcknowledgedASync() throws Exception {
        // Given
        fetcher.doOnLoop();

        // When
        Thread.sleep(SYNC_TIMEOUT.toMillis() * 5);
        fetcher.doOnLoop();

        // Then
        verify(requestStream, never()).cancel(anyString(), any());
    }

    private double reconnections(String reason) {
        Counter counter = meterRegistry.find(MetricRegistry.METRIC_WORKER_STREAM_RECONNECT_COUNT)
            .tag(MetricRegistry.TAG_REASON, reason)
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void shouldNotReconnectWhenControllerOfTheNewStreamNeverAcknowledgedASync() throws Exception {
        // Given
        fetcher.doOnLoop();
        responseObserver.get().onNext(WorkerJobResponse.newBuilder().setSyncAck(1).build());
        responseObserver.get().onError(Status.UNAVAILABLE.asRuntimeException());
        fetcher.resetBackoff();
        fetcher.doOnLoop();

        // When
        Thread.sleep(SYNC_TIMEOUT.toMillis() * 5);
        fetcher.doOnLoop();

        // Then
        verify(requestStream, never()).cancel(anyString(), any());
    }

    private static WorkerJobResponse jobs(String... jobIds) {
        WorkerJobResponse.Builder builder = WorkerJobResponse.newBuilder();
        for (String jobId : jobIds) {
            String json = """
                {"type":"task","taskRun":{"id":"%s"}}""".formatted(jobId);
            builder.addJobs(WorkerJobPayload.newBuilder().setJobId(jobId).setJobData(ByteString.copyFromUtf8(json)));
        }
        return builder.build();
    }
}
