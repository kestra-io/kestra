package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.AbstractRunnerTest;
import io.kestra.core.runners.InputsTest;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class JdbcRunnerTest extends AbstractRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    public static final String NAMESPACE = "io.kestra.tests";

    @Test
    void avoidInfiniteExecutionLoop() throws QueueException, InterruptedException {
        Flux<Execution> executionFlux = TestsUtils.receive(executionQueue);
        Execution execution = Execution.newExecution(TestsUtils.mockFlow(), Collections.emptyList());
        executionQueue.emit(execution);

        // Wait some time to ensure no infinite loop occurs
        Thread.sleep(500);

        // We expect the initial execution message + the failed due to missing flow
        assertThat(
            Objects.requireNonNull(executionFlux.collectList().block()).stream()
                .filter(e -> e.getId().equals(execution.getId()))
                .toList()
        ).hasSize(2);
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-child-task-warning.yaml"})
    void waitForChildTaskWarning() throws Exception {
        loopUntilTestCaseTest.waitForChildTaskWarning();
    }

    @Test
    @LoadFlows({"flows/valids/inputs-large.yaml"})
    void flowTooLarge() throws Exception {
        char[] chars = new char[200000];
        Arrays.fill(chars, 'a');

        Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE,
            "inputs-large",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(120)
        );

        assertThat(execution.getTaskRunList().size()).isGreaterThanOrEqualTo(6); // the exact number is test-run-dependent.
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs-large.yaml"}, tenantId = TENANT_1)
    void queueMessageTooLarge() {
        char[] chars = new char[1100000];
        Arrays.fill(chars, 'a');

        Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", new String(chars));

        var exception = assertThrows(QueueException.class, () -> runnerUtils.runOne(
            TENANT_1,
            NAMESPACE,
            "inputs-large",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(60)
        ));

        // the size is different on all runs, so we cannot assert on the exact message size
        assertThat(exception.getMessage()).contains("Message of size");
        assertThat(exception.getMessage()).contains("has exceeded the configured limit of 1048576");
        assertThat(exception).isInstanceOf(MessageTooBigException.class);
    }

    @Test
    @LoadFlows({"flows/valids/workertask-result-too-large.yaml"})
    void workerTaskResultTooLarge() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logsQueue,
            either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE,
            "workertask-result-too-large"
        );

        LogEntry matchingLog = TestsUtils.awaitLog(logs, log -> log.getMessage()
            .startsWith("Unable to emit the worker task result to the queue"));
        receive.blockLast();

        assertThat(matchingLog).isNotNull();
        assertThat(matchingLog.getLevel()).isEqualTo(Level.ERROR);
        // the size is different on all runs, so we cannot assert on the exact message size
        assertThat(matchingLog.getMessage()).contains("Message of size");
        assertThat(matchingLog.getMessage()).contains("has exceeded the configured limit of 1048576");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);

    }

    @Test
    @LoadFlows("flows/valids/errors.yaml")
    void errors() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logsQueue,
            either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "errors", null, null,
            Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList()).hasSize(7);

        receive.blockLast();
        LogEntry logEntry = TestsUtils.awaitLog(logs,
            log -> log.getMessage().contains("- task: failed, message: Task failure"));
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getMessage()).isEqualTo("- task: failed, message: Task failure");
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/execution.yaml"})
    void executionDate() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE,
            "execution-start-date", null, null, Duration.ofSeconds(60));

        assertThat((String) execution.getTaskRunList().getFirst().getOutputs().get("value")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z");
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/for-each-item-subflow-sleep.yaml",
        "flows/valids/for-each-item-no-wait.yaml"})
    protected void forEachItemNoWait() throws Exception {
        forEachItemCaseTest.forEachItemNoWait();
    }
}
