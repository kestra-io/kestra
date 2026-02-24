package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.*;
import io.kestra.core.runners.AbstractRunnerTest;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.InputsTest;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class JdbcRunnerTest extends AbstractRunnerTest {
    @Inject
    protected DispatchQueueInterface<Execution> executionQueue;

    @Inject
    protected DispatchQueueInterface<ExecutionEvent> executionEventQueue;

    @Inject
    private TaskOutputService taskOutputService;

    public static final String NAMESPACE = "io.kestra.tests";

    @Test
    void avoidInfiniteExecutionLoop() throws QueueException, InterruptedException {
        CopyOnWriteArrayList<ExecutionEvent > executions = new CopyOnWriteArrayList<>();
        executionEventQueue.addListener(e -> executions.add(e));

        Execution execution = Execution.newExecution(TestsUtils.mockFlow(), Collections.emptyList());
        executionQueue.emit(execution);

        // We expect the initial execution message + the failed due to missing flow
        await()
            .during(Duration.ofMillis(500)) // Wait some time to ensure no infinite loop occurs
            .atMost(Duration.ofSeconds(10))
            .until(() -> executions.size() == 2);
    }

    @Test
    @LoadFlows(value = {"flows/valids/waitfor-child-task-warning.yaml"}, tenantId = "waitforchildtaskwarning")
    void waitForChildTaskWarning() throws Exception {
        loopUntilTestCaseTest.waitForChildTaskWarning("waitforchildtaskwarning");
    }


    @Test
    @LoadFlows("flows/valids/errors.yaml")
    void errors() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logsQueue.addListener(l -> logs.add(l));

        Execution execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "errors", null, null,
            Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList()).hasSize(7);

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

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.getTaskRunList().getFirst());
        assertThat((String) outputs.get("value")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z");
    }

    @RetryingTest(5)
    @LoadFlows(value = {"flows/valids/for-each-item-subflow-sleep.yaml",
        "flows/valids/for-each-item-no-wait.yaml"}, tenantId = "foreachitemnowait")
    protected void forEachItemNoWait() throws Exception {
        forEachItemCaseTest.forEachItemNoWait("foreachitemnowait");
    }
}
