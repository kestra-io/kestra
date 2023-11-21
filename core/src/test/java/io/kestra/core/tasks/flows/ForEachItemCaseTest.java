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
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import static org.hamcrest.Matchers.*;

@Singleton
public class ForEachItemCaseTest {
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
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "for-each-item", null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 3 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts().get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(0).getOutputs();
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("max"), is(3));
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("SUCCESS"), is(3));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item/executions/.*/tasks/each/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
    }

    public void forEachItemNoWait() throws TimeoutException, InterruptedException, URISyntaxException, IOException {
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
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "for-each-item-no-wait", null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts().get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Map<String, Object> outputs = execution.getTaskRunList().get(0).getOutputs();
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("max"), is(3));
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), nullValue()); // if we didn't wait we will only observe CREATED and SUCCESS
        assertThat(iterations.get("SUCCESS"), is(3));

        // assert that not all subflows ran (depending on the speed of execution there can be some)
        // be careful that it's racy.
        assertThat(countDownLatch.getCount(), greaterThan(0L));

        // wait for the 3 flows to ends
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-no-wait/executions/.*/tasks/each/.*\\.txt"));
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
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "for-each-item-failed", null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 3 subflows
        assertThat(countDownLatch.await(1, TimeUnit.MINUTES), is(true));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts().get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        Map<String, Object> outputs = execution.getTaskRunList().get(0).getOutputs();
        assertThat(outputs.get("iterations"), notNullValue());
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("max"), is(3));
        assertThat(iterations.get("CREATED"), is(0));
        assertThat(iterations.get("RUNNING"), is(0));
        assertThat(iterations.get("FAILED"), is(3));

        // assert on the last subflow execution
        assertThat(triggered.get().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(triggered.get().getFlowId(), is("for-each-item-subflow-failed"));
        assertThat((String) triggered.get().getInputs().get("items"), matchesRegex("kestra:///io/kestra/tests/for-each-item-failed/executions/.*/tasks/each/.*\\.txt"));
        assertThat(triggered.get().getTaskRunList(), hasSize(1));
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
