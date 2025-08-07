package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class FlowConcurrencyCaseTest {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    public void flowConcurrencyCancel() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-cancel", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-cancel");

        assertThat(execution1.getState().isRunning()).isTrue();
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);

        CountDownLatch latch1 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            // FIXME we should fail if we receive the cancel execution again but on Kafka it happens
        });

        assertTrue(latch1.await(1, TimeUnit.MINUTES));
        receive.blockLast();
    }

    public void flowConcurrencyFail() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-fail", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-fail");

        assertThat(execution1.getState().isRunning()).isTrue();
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        CountDownLatch latch1 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            // FIXME we should fail if we receive the cancel execution again but on Kafka it happens
        });

        assertTrue(latch1.await(1, TimeUnit.MINUTES));
        receive.blockLast();
    }

    public void flowConcurrencyQueue() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-queue", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        executionQueue.emit(execution2);

        assertThat(execution1.getState().isRunning()).isTrue();
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        var executionResult1  = new AtomicReference<Execution>();
        var executionResult2  = new AtomicReference<Execution>();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                executionResult1.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            if (e.getLeft().getId().equals(execution2.getId())) {
                executionResult2.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.RUNNING) {
                    latch2.countDown();
                }
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch3.countDown();
                }
            }
        });

        assertTrue(latch1.await(1, TimeUnit.MINUTES));
        assertTrue(latch2.await(1, TimeUnit.MINUTES));
        assertTrue(latch3.await(1, TimeUnit.MINUTES));
        receive.blockLast();

        assertThat(executionResult1.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.get().getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(executionResult2.get().getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(executionResult2.get().getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyQueuePause() throws TimeoutException, QueueException, InterruptedException {
        AtomicReference<String> firstExecutionId = new AtomicReference<>();
        var firstExecutionResult  = new AtomicReference<Execution>();
        var secondExecutionResult  = new AtomicReference<Execution>();

        CountDownLatch firstExecutionLatch = new CountDownLatch(1);
        CountDownLatch secondExecutionLatch = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (!"flow-concurrency-queue-pause".equals(e.getLeft().getFlowId())){
                return;
            }
            String currentId = e.getLeft().getId();
            Type currentState = e.getLeft().getState().getCurrent();
            if (firstExecutionId.get() == null) {
                firstExecutionId.set(currentId);
            }

            if (currentId.equals(firstExecutionId.get())) {
                if (currentState == State.Type.SUCCESS) {
                    firstExecutionResult.set(e.getLeft());
                    firstExecutionLatch.countDown();
                }
            } else {
                if (currentState == State.Type.SUCCESS) {
                    secondExecutionResult.set(e.getLeft());
                    secondExecutionLatch.countDown();
                }
            }
        });


        Execution execution1 = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-queue-pause");
        Flow flow = flowRepository
            .findById(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-queue-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        executionQueue.emit(execution2);

        assertThat(execution1.getState().isPaused()).isTrue();
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        assertTrue(firstExecutionLatch.await(10, TimeUnit.SECONDS));
        assertTrue(secondExecutionLatch.await(10, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(firstExecutionResult.get().getId()).isEqualTo(execution1.getId());
        assertThat(firstExecutionResult.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.get().getId()).isEqualTo(execution2.getId());
        assertThat(secondExecutionResult.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.get().getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(secondExecutionResult.get().getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(secondExecutionResult.get().getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyCancelPause() throws TimeoutException, QueueException, InterruptedException {
        AtomicReference<String> firstExecutionId = new AtomicReference<>();
        var firstExecutionResult  = new AtomicReference<Execution>();
        var secondExecutionResult  = new AtomicReference<Execution>();
        CountDownLatch firstExecLatch = new CountDownLatch(1);
        CountDownLatch secondExecLatch = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (!"flow-concurrency-cancel-pause".equals(e.getLeft().getFlowId())){
                return;
            }
            String currentId = e.getLeft().getId();
            Type currentState = e.getLeft().getState().getCurrent();
            if (firstExecutionId.get() == null) {
                firstExecutionId.set(currentId);
            }
            if (currentId.equals(firstExecutionId.get())) {
                if (currentState == State.Type.SUCCESS) {
                    firstExecutionResult.set(e.getLeft());
                    firstExecLatch.countDown();
                }
            } else {
                if (currentState == State.Type.CANCELLED) {
                    secondExecutionResult.set(e.getLeft());
                    secondExecLatch.countDown();
                }
            }
        });

        Execution execution1 = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-cancel-pause");
        Flow flow = flowRepository
            .findById(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-cancel-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        executionQueue.emit(execution2);

        assertThat(execution1.getState().isPaused()).isTrue();
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        assertTrue(firstExecLatch.await(10, TimeUnit.SECONDS));
        assertTrue(secondExecLatch.await(10, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(firstExecutionResult.get().getId()).isEqualTo(execution1.getId());
        assertThat(firstExecutionResult.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.get().getId()).isEqualTo(execution2.getId());
        assertThat(secondExecutionResult.get().getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(secondExecutionResult.get().getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(secondExecutionResult.get().getState().getHistories().get(1).getState()).isEqualTo(State.Type.CANCELLED);
    }

    public void flowConcurrencyWithForEachItem() throws TimeoutException, QueueException, InterruptedException, URISyntaxException, IOException {
        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution forEachItem = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests", "flow-concurrency-for-each-item", null,
        (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs), Duration.ofSeconds(5));
        assertThat(forEachItem.getState().getCurrent()).isEqualTo(Type.RUNNING);

        Set<String> executionIds = new HashSet<>();
        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if ("flow-concurrency-queue".equals(e.getLeft().getFlowId()) && e.getLeft().getState().isRunning()) {
                executionIds.add(e.getLeft().getId());
            }
        });

        // wait a little to be sure there are not too many executions started
        Thread.sleep(500);

        assertThat(executionIds).hasSize(1);
        receive.blockLast();

        Execution terminated = runnerUtils.awaitExecution(e -> e.getId().equals(forEachItem.getId()) && e.getState().isTerminated(), () -> {}, Duration.ofSeconds(10));
        assertThat(terminated.getState().getCurrent()).isEqualTo(Type.SUCCESS);
    }

    private URI storageUpload() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), content());

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private List<String> content() {
        return IntStream
            .range(0, 7)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .toList();
    }

}
