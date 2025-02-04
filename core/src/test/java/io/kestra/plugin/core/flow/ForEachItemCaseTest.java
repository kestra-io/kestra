package io.kestra.plugin.core.flow;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Singleton
public class ForEachItemCaseTest {
    static final String TEST_NAMESPACE = "io.kestra.tests";

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private ExecutionService executionService;

    @SuppressWarnings("unchecked")
    public void forEachItem() throws TimeoutException, InterruptedException, URISyntaxException, IOException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(26);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-subflow") && execution.getState().getCurrent().isTerminated()) {
                triggered.set(execution);
                countDownLatch.countDown();
            }
        });

        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 26 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));
        receive.blockLast();

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(26));
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("SUCCESS"), is(26));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
        Optional<Label> correlationId = triggered.get().getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent(), is(true));
        assertThat(correlationId.get().value(), is(execution.getId()));
    }

    public void forEachItemEmptyItems() throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = emptyItems();
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs, nullValue());
    }

    @SuppressWarnings("unchecked")
    public void forEachItemNoWait() throws TimeoutException, InterruptedException, URISyntaxException, IOException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(26);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-subflow")) {
                log.info("Received sub-execution " + execution.getId() + " with status " + execution.getState().getCurrent());
                if (execution.getState().getCurrent().isTerminated()) {
                    triggered.set(execution);
                    countDownLatch.countDown();
                }
            }
        });

        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item-no-wait", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // assert that not all subflows ran (depending on the speed of execution, there can be some)
        // be careful that it's racy.
        assertThat(countDownLatch.getCount(), greaterThan(0L));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(26));
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), nullValue()); // if we didn't wait we will only observe RUNNING and SUCCESS
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("SUCCESS"), is(26));

        // wait for the 26 flows to ends
        assertThat("Remaining count was " + countDownLatch.getCount(), countDownLatch.await(1, TimeUnit.MINUTES), is(true));
        receive.blockLast();

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-no-wait/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
    }

    @SuppressWarnings("unchecked")
    public void forEachItemFailed() throws TimeoutException, InterruptedException, URISyntaxException, IOException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(26);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-subflow-failed") && execution.getState().getCurrent().isTerminated()) {
                triggered.set(execution);
                countDownLatch.countDown();
            }
        });

        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item-failed", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(60));

        // we should have triggered 26 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));
        receive.blockLast();

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(26));
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("FAILED"), is(26));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow-failed"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-failed/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
    }

    @SuppressWarnings("unchecked")
    public void forEachItemWithSubflowOutputs() throws TimeoutException, InterruptedException, URISyntaxException, IOException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(26);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-outputs-subflow") && execution.getState().getCurrent().isTerminated()) {
                triggered.set(execution);
                countDownLatch.countDown();
            }
        });

        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item-outputs", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 26 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));
        receive.blockLast();

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(26));
        assertThat(outputs.get("iterations"), notNullValue());

        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("SUCCESS"), is(26));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-outputs-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-outputs/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));

        // asserts for subflow merged outputs
        Map<String, Object> mergeTaskOutputs = execution.getTaskRunList().get(3).getOutputs();
        assertThat(mergeTaskOutputs.get("subflowOutputs"), notNullValue());
        InputStream stream = storageInterface.get(null, execution.getNamespace(), URI.create((String) mergeTaskOutputs.get("subflowOutputs")));

        try (var br = new BufferedReader(new InputStreamReader(stream))) {
            // one line per sub-flows
            assertThat(br.lines().count(), is(26L));
        }
    }

    public void restartForEachItem() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(6);
        Flux<Execution> receiveSubflows = TestsUtils.receive(executionQueue, either -> {
            Execution subflowExecution = either.getLeft();
            if (subflowExecution.getFlowId().equals("restart-child") && subflowExecution.getState().getCurrent().isFailed()) {
                countDownLatch.countDown();
            }
        });

        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 20);
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "restart-for-each-item", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        // here we must have 1 failed subflows
        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        receiveSubflows.blockLast();

        CountDownLatch successLatch = new CountDownLatch(6);
        receiveSubflows = TestsUtils.receive(executionQueue, either -> {
            Execution subflowExecution = either.getLeft();
            if (subflowExecution.getFlowId().equals("restart-child") && subflowExecution.getState().getCurrent().isSuccess()) {
                successLatch.countDown();
            }
        });
        Execution restarted = executionService.restart(execution, null);
        execution = runnerUtils.awaitExecution(
            e -> e.getState().getCurrent() == State.Type.SUCCESS && e.getFlowId().equals("restart-for-each-item"),
            throwRunnable(() -> executionQueue.emit(restarted)),
            Duration.ofSeconds(10)
        );
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertTrue(successLatch.await(1, TimeUnit.MINUTES));
        receiveSubflows.blockLast();
    }

    private URI storageUpload() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), content());

        return storageInterface.put(
            null,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private URI emptyItems() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        return storageInterface.put(
            null,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private List<String> content() {
        return IntStream
            .range(0, 102)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .toList();
    }
}
