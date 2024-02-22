package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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

    public void forEachItem() throws TimeoutException, InterruptedException, URISyntaxException, IOException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-subflow") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                triggered.set(execution);
            }
        });

        URI file = storageUpload(10);
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item", null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 3 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(3));
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("SUCCESS"), is(3));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
    }

    public void forEachItemNoWait() throws TimeoutException, InterruptedException, URISyntaxException, IOException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-subflow")) {
                log.info("Received sub-execution " + execution.getId() + " with status " + execution.getState().getCurrent());
                if (execution.getState().getCurrent().isTerminated()) {
                    countDownLatch.countDown();
                    triggered.set(execution);
                }
            }
        });

        URI file = storageUpload(10);
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item-no-wait", null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // assert that not all subflows ran (depending on the speed of execution, there can be some)
        // be careful that it's racy.
        assertThat(countDownLatch.getCount(), greaterThan(0L));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(3));
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), nullValue()); // if we didn't wait we will only observe RUNNING and SUCCESS
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("SUCCESS"), is(3));

        // wait for the 3 flows to ends
        assertThat("Remaining count was " + countDownLatch.getCount(), countDownLatch.await(1, TimeUnit.MINUTES), is(true));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-no-wait/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
    }

    public void forEachItemFailed() throws TimeoutException, InterruptedException, URISyntaxException, IOException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-subflow-failed") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                triggered.set(execution);
            }
        });

        URI file = storageUpload(10);
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item-failed", null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 3 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(3));
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("FAILED"), is(3));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow-failed"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-failed/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
    }

    public void forEachItemWithSubflowOutputs() throws TimeoutException, InterruptedException, URISyntaxException, IOException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("for-each-item-outputs-subflow") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                triggered.set(execution);
            }
        });

        URI file = storageUpload(10);
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, TEST_NAMESPACE, "for-each-item-outputs", null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 3 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getTaskRunList().get(2).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches"), is(3));
        assertThat(outputs.get("iterations"), notNullValue());

        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("SUCCESS"), is(3));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-outputs-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-outputs/executions/.*/tasks/each-split/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));

        // asserts for subflow merged outputs
        Map<String, Object> mergeTaskOutputs = execution.getTaskRunList().get(3).getOutputs();
        assertThat(mergeTaskOutputs.get("subflowOutputs"), notNullValue());
        InputStream stream = storageInterface.get(null, URI.create((String) mergeTaskOutputs.get("subflowOutputs")));

        try (var br = new BufferedReader(new InputStreamReader(stream))) {
            // one line per sub-flows
            assertThat(br.lines().count(), is(3L));
        }
    }

    private URI storageUpload(int count) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), content(count));

        return storageInterface.put(
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private List<String> content(int count) {
        return IntStream
            .range(0, count)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .collect(Collectors.toList());
    }
}
