package org.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.annotation.MicronautTest;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueException;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.InputsTest;
import org.kestra.core.runners.ListenersTest;
import org.kestra.core.runners.RunnerUtils;
import org.kestra.core.runners.StandAloneRunner;
import org.kestra.core.utils.TestsUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class KafkaRunnerTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        runner.setThreads(1);
        runner.run();
        TestsUtils.loads(repositoryLoader);
    }

    @Test
    void full() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "full", null, null, Duration.ofSeconds(15));

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "errors", null, null, Duration.ofSeconds(15));

        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    void sequential() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "sequential");

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void listeners() throws TimeoutException, QueueException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests")));

        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners",
            null,
            (f, e) -> ImmutableMap.of("string", "OK")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("ok"));
        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("execution-success-listener"));
    }

    @Test
    void recordTooLarge() {
        char[] chars = new char[2000000];
        Arrays.fill(chars, 'a');

        HashMap<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            runnerUtils.runOne(
                "org.kestra.tests",
                "inputs",
                null,
                (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
            );
        });

        assertThat(e.getCause().getClass(), is(ExecutionException.class));
        assertThat(e.getCause().getCause().getClass(), is(RecordTooLargeException.class));
    }

    @Test
    void workerRecordTooLarge() throws TimeoutException {
        char[] chars = new char[600000];
        Arrays.fill(chars, 'a');

        HashMap<String, String> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void invalidVars() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "variables-invalid");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(1).getAttempts().get(0).getLogs().get(0).getMessage(), containsString("Missing variable: inputs.invalid"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
