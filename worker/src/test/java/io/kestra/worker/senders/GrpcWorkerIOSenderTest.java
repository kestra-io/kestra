package io.kestra.worker.senders;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.services.GrpcWorkerControllerService;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.worker.Controller;
import io.kestra.core.worker.models.WorkerContext;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    private Controller controller;

    @MockBean(GrpcWorkerControllerService.class)
    GrpcWorkerControllerService grpcWorkerControllerService() {
        GrpcWorkerControllerService mock = mock(GrpcWorkerControllerService.class);
        // bindService() must work so the gRPC server can route incoming calls to mock methods
        when(mock.bindService()).thenCallRealMethod();
        return mock;
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
        taskResultSender.init(new WorkerContext("test-worker", null, 1));
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
        WorkerTaskResult large = buildTaskResult(Map.of("output", new String(largePayload)));

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
    }

    @Test
    void shouldRetryOnceOnUnauthenticatedError() {
        // Given - first call fails with UNAUTHENTICATED, second succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv -> {
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
            .untilAsserted(() -> {
                verify(grpcWorkerControllerService, org.mockito.Mockito.atLeast(2))
                    .sendWorkerTaskResults(captor.capture(), any());
            });

        // The retried request should contain the same task result
        WorkerTaskResult received = deserialize(captor.getAllValues().getLast()).records().getFirst();
        assertThat(received.getTaskRun().getId()).isEqualTo(result.getTaskRun().getId());
        assertThat(received.getOutputs()).isEqualTo(Map.of("key", "value"));
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
