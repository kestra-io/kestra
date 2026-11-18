package io.kestra.worker.fetchers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import com.google.protobuf.ByteString;

import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.controller.grpc.WorkerJobPayload;
import io.kestra.controller.grpc.WorkerJobRequest;
import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.queues.InMemoryWorkerQueue;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerJobFetcherUndecodableJobTest {

    private static final String MISSING_TYPE = "io.kestra.plugin.missing.NotInstalled";

    private final List<WorkerJobRequest> sent = new CopyOnWriteArrayList<>();
    private final AtomicReference<ClientResponseObserver<WorkerJobRequest, WorkerJobResponse>> responseObserver = new AtomicReference<>();

    private WorkerJobFetcher fetcher;
    private WorkerQueue<WorkerJob> jobQueue;
    private WorkerQueue<WorkerTaskResult> resultQueue;
    private WorkerQueue<LogEntry> logQueue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("test");
        when(kestraContext.getPluginRegistry()).thenReturn(DefaultPluginRegistry.getOrCreate());
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
        jobQueue = new InMemoryWorkerQueue<>(10);
        resultQueue = new InMemoryWorkerQueue<>(10);
        logQueue = new InMemoryWorkerQueue<>(10);
        WorkerQueueRegistry registry = mock(WorkerQueueRegistry.class);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerJob.class))).thenReturn(jobQueue);
        when(registry.getOrCreate(any(WorkerContext.class), eq(WorkerTaskResult.class))).thenReturn(resultQueue);
        when(registry.getOrCreate(any(WorkerContext.class), eq(LogEntry.class))).thenReturn(logQueue);

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
    void shouldFailTaskWhenItsPluginIsNotInstalledOnTheWorker() throws Exception {
        fetcher.doOnLoop();

        responseObserver.get().onNext(taskWithMissingPlugin("taskrun-1"));

        WorkerTaskResult result = resultQueue.poll(Duration.ofSeconds(1));
        assertThat(result).isNotNull();
        assertThat(result.getTaskRun().getId()).isEqualTo("taskrun-1");
        assertThat(result.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        LogEntry logEntry = logQueue.poll(Duration.ofSeconds(1));
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getLevel()).isEqualTo(Level.ERROR);
        assertThat(logEntry.getTaskRunId()).isEqualTo("taskrun-1");
        assertThat(logEntry.getMessage()).contains(MISSING_TYPE).contains("worker-1");

        assertThat(jobQueue.poll(Duration.ZERO)).isNull();
        await().atMost(Duration.ofSeconds(5)).until(() -> sent.getLast().getCompletedJobIdsCount() > 0);
        assertThat(sent.getLast().getCompletedJobIdsList()).containsExactly("taskrun-1");
        assertThat(sent.getLast().getPermits()).isEqualTo(2);
    }

    @Test
    void shouldHoldPermitWhileFailingTaskWhenOutputQueueIsFull() throws Exception {
        for (int i = 0; i < 10; i++) {
            logQueue.put(LogEntry.builder().message("filler-" + i).build());
        }
        fetcher.doOnLoop();

        responseObserver.get().onNext(taskWithMissingPlugin("taskrun-1"));
        fetcher.doOnLoop();

        assertThat(sent.getLast().getPermits()).isEqualTo(1);
        assertThat(sent.getLast().getCompletedJobIdsList()).isEmpty();

        logQueue.poll(Duration.ZERO);
        await().atMost(Duration.ofSeconds(5)).until(() -> sent.getLast().getCompletedJobIdsCount() > 0);
        assertThat(sent.getLast().getCompletedJobIdsList()).containsExactly("taskrun-1");
        assertThat(sent.getLast().getPermits()).isEqualTo(2);
    }

    private static WorkerJobResponse taskWithMissingPlugin(String taskRunId) {
        String json = """
            {
              "type": "task",
              "taskRun": {
                "id": "%s",
                "tenantId": "main",
                "namespace": "company.team",
                "flowId": "flow",
                "taskId": "shell",
                "executionId": "execution-1",
                "state": {"current": "RUNNING", "histories": [{"state": "RUNNING", "date": "2026-11-09T10:00:00Z"}]}
              },
              "task": {"id": "shell", "type": "%s"}
            }""".formatted(taskRunId, MISSING_TYPE);
        return WorkerJobResponse.newBuilder()
            .addJobs(WorkerJobPayload.newBuilder().setJobId(taskRunId).setJobData(ByteString.copyFromUtf8(json)))
            .build();
    }
}
