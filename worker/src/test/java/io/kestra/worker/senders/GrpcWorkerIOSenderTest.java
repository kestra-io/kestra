package io.kestra.worker.senders;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.services.GrpcWorkerControllerService;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.LogEntryEmitter;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.worker.Controller;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.queues.WorkerQueueRegistry;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@KestraTest
@Property(name = "kestra.grpc.maxInboundMessageSize", value = "1048576")
class GrpcWorkerIOSenderTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    GrpcWorkerIOSender<WorkerTaskResult> taskResultSender;

    @Inject
    GrpcWorkerControllerService grpcWorkerControllerService;

    @Inject
    LogEntryEmitter logEntryEmitter;

    private Controller controller;

    @MockBean(GrpcWorkerControllerService.class)
    GrpcWorkerControllerService grpcWorkerControllerService() {
        GrpcWorkerControllerService mock = mock(GrpcWorkerControllerService.class);
        // bindService() must work so the gRPC server can route incoming calls to mock methods
        when(mock.bindService()).thenCallRealMethod();
        return mock;
    }

    @MockBean(LogEntryEmitter.class)
    LogEntryEmitter logEntryEmitter() {
        return mock(LogEntryEmitter.class);
    }

    @BeforeEach
    void setUp() {
        doAnswer(inv ->
        {
            OpaqueData req = inv.getArgument(0);
            StreamObserver<OpaqueData> obs = inv.getArgument(1);
            obs.onNext(OpaqueData.newBuilder().setHeader(req.getHeader()).build());
            obs.onCompleted();
            return null;
        }).when(grpcWorkerControllerService).sendWorkerTaskResults(any(), any());

        controller = applicationContext.createBean(Controller.class);
        controller.start();
        taskResultSender.init(new WorkerContext("test-worker", "", 1));
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    @Test
    void shouldSendTaskResultToController() {
        WorkerTaskResult result = buildTaskResult(Map.of("key", "value"));

        taskResultSender.send(List.of(result));

        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> verify(grpcWorkerControllerService).sendWorkerTaskResults(captor.capture(), any()));

        WorkerTaskResult received = deserialize(captor.getValue()).records().getFirst();
        assertThat(received.getTaskRun().getId()).isEqualTo(result.getTaskRun().getId());
        assertThat(received.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.CREATED);
        assertThat(received.getOutputs()).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void shouldSendFailedResultWithoutOutputsWhenTaskResultIsTooLarge() {
        // 2 MB payload — exceeds the 1 MB server-side limit set via @Property
        char[] largePayload = new char[2 * 1024 * 1024];
        Arrays.fill(largePayload, 'a');
        TaskRun taskRun = buildTaskResult(Map.of()).getTaskRun()
            .withAttempts(List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .withState(State.Type.SUCCESS);
        WorkerTaskResult large = new WorkerTaskResult(taskRun, Map.of("output", new String(largePayload)));

        taskResultSender.send(List.of(large));

        // The initial send triggers a real RESOURCE_EXHAUSTED from the gRPC transport.
        // The fallback mapper fires and re-sends a stripped result to the controller.
        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> verify(grpcWorkerControllerService).sendWorkerTaskResults(captor.capture(), any()));

        WorkerTaskResult received = deserialize(captor.getValue()).records().getFirst();
        assertThat(received.getTaskRun().getId()).isEqualTo(large.getTaskRun().getId());
        assertThat(received.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(received.getOutputs()).isNull();
        assertThat(received.getTaskRun().getAttempts()).hasSize(1);
        assertThat(received.getTaskRun().lastAttempt().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        ArgumentCaptor<LogEntry> logCaptor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntryEmitter).emits(logCaptor.capture());

        LogEntry logEntry = logCaptor.getValue();
        assertThat(logEntry.getLevel()).isEqualTo(Level.ERROR);
        assertThat(logEntry.getTaskRunId()).isEqualTo(taskRun.getId());
        assertThat(logEntry.getAttemptNumber()).isZero();
        assertThat(logEntry.getMessage()).contains("kestra.grpc.max-inbound-message-size");
    }

    @Test
    void shouldResendResultsOneByOneWhenTheBatchIsTooLarge() {
        // Given a batch of two results of which one is too large on its own; the mocked controller does the rejecting,
        // since a real oversized message leaves its stream half-open on the server and holds its shutdown for the whole grace period
        WorkerTaskResult small = buildTaskResult(Map.of("key", "value"));
        TaskRun largeTaskRun = buildTaskResult(Map.of()).getTaskRun()
            .withAttempts(List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .withState(State.Type.SUCCESS);
        WorkerTaskResult large = new WorkerTaskResult(largeTaskRun, Map.of("output", "too large for the controller"));
        doAnswer(inv ->
        {
            OpaqueData req = inv.getArgument(0);
            StreamObserver<OpaqueData> obs = inv.getArgument(1);
            List<WorkerTaskResult> records = deserialize(req).records();
            boolean tooLarge = records.size() > 1
                || (records.getFirst().getTaskRun().getId().equals(large.getTaskRun().getId()) && records.getFirst().getOutputs() != null);
            if (tooLarge) {
                obs.onError(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED.withDescription("gRPC message exceeds maximum size")));
            } else {
                obs.onNext(OpaqueData.newBuilder().setHeader(req.getHeader()).build());
                obs.onCompleted();
            }
            return null;
        }).when(grpcWorkerControllerService).sendWorkerTaskResults(any(), any());

        // When
        taskResultSender.send(List.of(small, large));

        // Then the batch is rejected, each result is resent alone, and only the large one is failed:
        // 4 calls in all (the batch, the small result, the large result, the large result failed without outputs)
        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> verify(grpcWorkerControllerService, times(4)).sendWorkerTaskResults(captor.capture(), any()));

        List<BatchMessage<WorkerTaskResult>> calls = captor.getAllValues().stream().map(GrpcWorkerIOSenderTest::deserialize).toList();
        assertThat(calls.stream().map(call -> call.records().size())).contains(2);
        Map<String, WorkerTaskResult> delivered = calls.stream()
            .filter(call -> call.records().size() == 1)
            .map(call -> call.records().getFirst())
            .collect(Collectors.toMap(result -> result.getTaskRun().getId(), Function.identity(), (first, second) -> second));
        assertThat(delivered).containsOnlyKeys(small.getTaskRun().getId(), large.getTaskRun().getId());
        assertThat(delivered.get(small.getTaskRun().getId()).getOutputs()).isEqualTo(Map.of("key", "value"));
        assertThat(delivered.get(small.getTaskRun().getId()).getTaskRun().getState().getCurrent()).isEqualTo(State.Type.CREATED);
        assertThat(delivered.get(large.getTaskRun().getId()).getTaskRun().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(delivered.get(large.getTaskRun().getId()).getOutputs()).isNull();
    }

    @Test
    void shouldSplitTheBatchBeforeSendingWhenItExceedsTheInboundMessageSize() {
        // Given two results of 600 KB each: together above the 1 MB limit set via @Property, each one below it
        char[] payload = new char[600 * 1024];
        Arrays.fill(payload, 'a');
        WorkerTaskResult first = buildTaskResult(Map.of("output", new String(payload)));
        WorkerTaskResult second = buildTaskResult(Map.of("output", new String(payload)));

        // When
        taskResultSender.send(List.of(first, second));

        // Then both arrive intact in a call of their own, without any rejection from the transport
        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> verify(grpcWorkerControllerService, times(2)).sendWorkerTaskResults(captor.capture(), any()));

        List<BatchMessage<WorkerTaskResult>> calls = captor.getAllValues().stream().map(GrpcWorkerIOSenderTest::deserialize).toList();
        assertThat(calls).allSatisfy(call -> assertThat(call.records()).hasSize(1));
        assertThat(calls.stream().map(call -> call.records().getFirst().getTaskRun().getId()))
            .containsExactlyInAnyOrder(first.getTaskRun().getId(), second.getTaskRun().getId());
        assertThat(calls).allSatisfy(call -> assertThat(call.records().getFirst().getOutputs()).containsKey("output"));
        verifyNoInteractions(logEntryEmitter);
    }

    @Test
    void shouldRetryOnceOnUnauthenticatedError() {
        // Given - first call fails with UNAUTHENTICATED, second succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv ->
        {
            OpaqueData req = inv.getArgument(0);
            StreamObserver<OpaqueData> obs = inv.getArgument(1);
            if (callCount.getAndIncrement() == 0) {
                obs.onError(new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Invalid or expired access token")));
            } else {
                obs.onNext(OpaqueData.newBuilder().setHeader(req.getHeader()).build());
                obs.onCompleted();
            }
            return null;
        }).when(grpcWorkerControllerService).sendWorkerTaskResults(any(), any());

        WorkerTaskResult result = buildTaskResult(Map.of("key", "value"));

        // When
        taskResultSender.send(List.of(result));

        // Then - should have been called twice (initial + retry)
        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(() ->
            {
                verify(grpcWorkerControllerService, org.mockito.Mockito.atLeast(2))
                    .sendWorkerTaskResults(captor.capture(), any());
            });

        // The retried request should contain the same task result
        WorkerTaskResult received = deserialize(captor.getAllValues().getLast()).records().getFirst();
        assertThat(received.getTaskRun().getId()).isEqualTo(result.getTaskRun().getId());
        assertThat(received.getOutputs()).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void shouldRequeueAndRedeliverWhenSendFailsWithRetryableError() throws Exception {
        // Given - first send fails with UNAVAILABLE (controller unreachable), subsequent sends succeed
        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv ->
        {
            OpaqueData req = inv.getArgument(0);
            StreamObserver<OpaqueData> obs = inv.getArgument(1);
            if (callCount.getAndIncrement() == 0) {
                obs.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("controller unreachable")));
            } else {
                obs.onNext(OpaqueData.newBuilder().setHeader(req.getHeader()).build());
                obs.onCompleted();
            }
            return null;
        }).when(grpcWorkerControllerService).sendWorkerTaskResults(any(), any());

        WorkerTaskResult result = buildTaskResult(Map.of("key", "value"));

        // When - the first send fails and the result is re-queued instead of dropped.
        taskResultSender.send(List.of(result));

        // Then - the loop redrives until the controller receives the result (initial attempt +
        // redelivery). doOnLoop() runs inside the await because the first send fails asynchronously
        // on a gRPC callback thread, so a single redrive could poll before the item is re-queued.
        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() ->
            {
                taskResultSender.doOnLoop();
                verify(grpcWorkerControllerService, org.mockito.Mockito.atLeast(2))
                    .sendWorkerTaskResults(captor.capture(), any());
            });

        WorkerTaskResult redelivered = deserialize(captor.getAllValues().getLast()).records().getFirst();
        assertThat(redelivered.getTaskRun().getId()).isEqualTo(result.getTaskRun().getId());
        assertThat(redelivered.getOutputs()).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void shouldRequeueAndRedeliverWhenUnauthenticatedPersistsAfterRetry() throws Exception {
        // Given - the initial send and its immediate retry both find the access token rejected, then the
        // worker recovers its credentials and the next send succeeds.
        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv ->
        {
            OpaqueData req = inv.getArgument(0);
            StreamObserver<OpaqueData> obs = inv.getArgument(1);
            if (callCount.getAndIncrement() < 2) {
                obs.onError(new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Invalid or expired access token")));
            } else {
                obs.onNext(OpaqueData.newBuilder().setHeader(req.getHeader()).build());
                obs.onCompleted();
            }
            return null;
        }).when(grpcWorkerControllerService).sendWorkerTaskResults(any(), any());

        WorkerTaskResult result = buildTaskResult(Map.of("key", "value"));

        // When - the retry exhausts the single in-call attempt, so the result is re-queued instead of dropped.
        taskResultSender.send(List.of(result));

        // Then - the loop redrives until the controller receives the result (initial attempt, in-call retry,
        // then redelivery). doOnLoop() runs inside the await because the failures arrive asynchronously on
        // gRPC callback threads, so a single redrive could poll before the item is re-queued.
        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() ->
            {
                taskResultSender.doOnLoop();
                verify(grpcWorkerControllerService, org.mockito.Mockito.atLeast(3))
                    .sendWorkerTaskResults(captor.capture(), any());
            });

        WorkerTaskResult redelivered = deserialize(captor.getAllValues().getLast()).records().getFirst();
        assertThat(redelivered.getTaskRun().getId()).isEqualTo(result.getTaskRun().getId());
        assertThat(redelivered.getOutputs()).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void shouldRedeliverFallbackResultWhenFallbackResendFailsWithRetryableError() throws Exception {
        // Given - the initial send is rejected with RESOURCE_EXHAUSTED (outputs too large), so the fallback
        // mapper fires and resends a stripped failed-state result; that fallback resend then fails once with
        // UNAVAILABLE (transient partition) before finally succeeding on redelivery.
        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv ->
        {
            OpaqueData req = inv.getArgument(0);
            StreamObserver<OpaqueData> obs = inv.getArgument(1);
            int call = callCount.getAndIncrement();
            if (call == 0) {
                obs.onError(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED.withDescription("outputs exceeds maximum size")));
            } else if (call == 1) {
                obs.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("controller unreachable")));
            } else {
                obs.onNext(OpaqueData.newBuilder().setHeader(req.getHeader()).build());
                obs.onCompleted();
            }
            return null;
        }).when(grpcWorkerControllerService).sendWorkerTaskResults(any(), any());

        WorkerTaskResult result = buildTaskResult(Map.of("key", "value"));

        // When - RESOURCE_EXHAUSTED triggers the fallback resend, which fails transiently and is re-queued.
        taskResultSender.send(List.of(result));

        // Then - the loop redrives until the failed-state result (no outputs) is delivered rather than
        // dropped. doOnLoop() runs inside the await because the failures arrive asynchronously on gRPC
        // callback threads, so a single redrive could poll before the fallback result is re-queued.
        ArgumentCaptor<OpaqueData> captor = ArgumentCaptor.forClass(OpaqueData.class);
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() ->
            {
                taskResultSender.doOnLoop();
                verify(grpcWorkerControllerService, org.mockito.Mockito.atLeast(3))
                    .sendWorkerTaskResults(captor.capture(), any());
            });

        WorkerTaskResult redelivered = deserialize(captor.getAllValues().getLast()).records().getFirst();
        assertThat(redelivered.getTaskRun().getId()).isEqualTo(result.getTaskRun().getId());
        assertThat(redelivered.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(redelivered.getOutputs()).isNull();
    }

    @Test
    void shouldWaitForTheControllerReplyBeforeStopping() throws Exception {
        // Given
        List<StreamObserver<OpaqueData>> pendingReplies = new CopyOnWriteArrayList<>();
        GrpcWorkerIOSender<WorkerTaskResult> sender = stoppedSenderHoldingOneResult((request, observer) -> pendingReplies.add(observer), Duration.ofSeconds(30));

        // When
        Thread drain = Thread.ofVirtual().start(sender);
        await().atMost(Duration.ofSeconds(5)).until(() -> pendingReplies.size() == 1);

        // Then
        assertThat(drain.join(Duration.ofMillis(500))).as("the drain waits for the reply").isFalse();
        pendingReplies.getFirst().onCompleted();
        assertThat(drain.join(Duration.ofSeconds(5))).isTrue();
        assertThat(sender.hasUndeliveredResults()).isFalse();
    }

    @Test
    void shouldReportUndeliveredResultsWhenSendFailsDuringShutdown() {
        // Given
        GrpcWorkerIOSender<WorkerTaskResult> sender = stoppedSenderHoldingOneResult(
            (request, observer) -> observer.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("controller unreachable"))),
            Duration.ofSeconds(30)
        );

        // When
        sender.run();

        // Then
        assertThat(sender.hasUndeliveredResults()).isTrue();
    }

    @Test
    void shouldReportUndeliveredResultsWhenControllerNeverReplies() {
        // Given
        GrpcWorkerIOSender<WorkerTaskResult> sender = stoppedSenderHoldingOneResult((request, observer) -> {}, Duration.ofMillis(200));

        // When
        sender.run();

        // Then
        assertThat(sender.hasUndeliveredResults()).isTrue();
    }

    private GrpcWorkerIOSender<WorkerTaskResult> stoppedSenderHoldingOneResult(BiConsumer<OpaqueData, StreamObserver<OpaqueData>> grpcSendMethod, Duration replyTimeout) {
        WorkerQueueRegistry workerQueueRegistry = applicationContext.getBean(WorkerQueueRegistry.class);
        WorkerContext workerContext = new WorkerContext("worker-" + IdUtils.create(), "", 1);
        GrpcWorkerIOSender<WorkerTaskResult> sender = new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "test",
            WorkerTaskResult.class,
            GrpcWorkerIOSender.SendStrategy.PER_ITEM,
            1024 * 1024,
            grpcSendMethod,
            null,
            true,
            replyTimeout
        );
        sender.init(workerContext);
        workerQueueRegistry.getOrCreate(workerContext, WorkerTaskResult.class).put(buildTaskResult(Map.of()));
        sender.stop();
        return sender;
    }

    private static WorkerTaskResult buildTaskResult(Map<String, Object> outputs) {
        TaskRun taskRun = TaskRun.builder()
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();
        return new WorkerTaskResult(taskRun, outputs);
    }

    private static BatchMessage<WorkerTaskResult> deserialize(OpaqueData data) {
        return MessageFormats.JSON.fromByteString(data.getMessage(), new TypeReference<>() {
        });
    }
}
