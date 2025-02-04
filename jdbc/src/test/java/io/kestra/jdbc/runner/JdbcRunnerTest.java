package io.kestra.jdbc.runner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.AbstractRunnerTest;
import io.kestra.core.runners.InputsTest;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

public abstract class JdbcRunnerTest extends AbstractRunnerTest {

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @Test
    @LoadFlows({"flows/valids/waitfor-child-task-warning.yaml"})
    void waitForChildTaskWarning() throws Exception {
        waitForTestCaseTest.waitForChildTaskWarning();
    }

    @Test
    @LoadFlows({"flows/valids/inputs-large.yaml"})
    void flowTooLarge() throws Exception {
        char[] chars = new char[200000];
        Arrays.fill(chars, 'a');

        Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs-large",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(120)
        );

        assertThat(execution.getTaskRunList().size(),
            greaterThanOrEqualTo(6)); // the exact number is test-run-dependent.
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        // To avoid flooding the database with big messages, we re-init it
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    @LoadFlows({"flows/valids/inputs-large.yaml"})
    void queueMessageTooLarge() {
        char[] chars = new char[1100000];
        Arrays.fill(chars, 'a');

        Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        var exception = assertThrows(QueueException.class, () -> runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs-large",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(60)
        ));

        // the size is different on all runs, so we cannot assert on the exact message size
        assertThat(exception.getMessage(), containsString("Message of size"));
        assertThat(exception.getMessage(),
            containsString("has exceeded the configured limit of 1048576"));
        assertThat(exception, instanceOf(MessageTooBigException.class));
    }

    @Test
    @LoadFlows({"flows/valids/workertask-result-too-large.yaml"})
    void workerTaskResultTooLarge() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logsQueue,
            either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "workertask-result-too-large"
        );

        LogEntry matchingLog = TestsUtils.awaitLog(logs, log -> log.getMessage()
            .startsWith("Unable to emit the worker task result to the queue"));
        receive.blockLast();

        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.ERROR));
        // the size is different on all runs, so we cannot assert on the exact message size
        assertThat(matchingLog.getMessage(), containsString("Message of size"));
        assertThat(matchingLog.getMessage(),
            containsString("has exceeded the configured limit of 1048576"));

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().size(), is(1));

    }

    @Test
    @LoadFlows("flows/valids/errors.yaml")
    void errors() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logsQueue,
            either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "errors", null, null,
            Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(7));

        receive.blockLast();
        LogEntry logEntry = TestsUtils.awaitLog(logs,
            log -> log.getMessage().contains("- task: failed, message: Task failure"));
        assertThat(logEntry, notNullValue());
        assertThat(logEntry.getMessage(), is("- task: failed, message: Task failure"));
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/execution.yaml"})
    void executionDate() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests",
            "execution-start-date", null, null, Duration.ofSeconds(60));

        assertThat((String) execution.getTaskRunList().getFirst().getOutputs().get("value"),
            matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/for-each-item-subflow.yaml",
        "flows/valids/for-each-item-no-wait.yaml"})
    protected void forEachItemNoWait() throws Exception {
        forEachItemCaseTest.forEachItemNoWait();
    }
}
