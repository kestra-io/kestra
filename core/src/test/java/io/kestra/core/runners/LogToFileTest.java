package io.kestra.core.runners;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class LogToFileTest {
    @Inject
    private StorageInterface storage;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @ExecuteFlow("flows/valids/log-to-file.yaml")
    void task(Execution execution) throws Exception {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getAttempts()).hasSize(1);
        TaskRunAttempt attempt = taskRun.getAttempts().getFirst();
        assertThat(attempt.getLogFile()).isNotNull();

        InputStream inputStream = storage.get(MAIN_TENANT, "io.kestra.tests", attempt.getLogFile());
        List<String> strings = IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        assertThat(strings).isNotNull();
        assertThat(strings.size()).isEqualTo(1);
        assertThat(strings.getFirst()).contains("INFO");
        assertThat(strings.getFirst()).contains("Hello World!");
    }

    @Test
    @LoadFlows("flows/valids/log-to-file-runif-error.yaml")
    void shouldNotLoseTheRunIfErrorWhenTheTaskLogsToFile() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "log-to-file-runif-error");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        LogEntry runIfError = TestsUtils.awaitLog(
            logs,
            log -> execution.getId().equals(log.getExecutionId()) && log.getMessage().startsWith("Failed to evaluate the runIf condition for task hello")
        );
        assertThat(runIfError).isNotNull();
    }

    @Test
    @LoadFlows("flows/valids/log-to-file-cache.yaml")
    void shouldUploadTheLogFileWhenTheTaskOutputIsCached() throws Exception {
        runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "log-to-file-cache");

        Execution cached = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "log-to-file-cache");

        assertThat(cached.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        TaskRunAttempt attempt = cached.getTaskRunList().getFirst().getAttempts().getFirst();
        assertThat(attempt.getLogFile()).isNotNull();
        try (InputStream inputStream = storage.get(MAIN_TENANT, "io.kestra.tests", attempt.getLogFile())) {
            assertThat(IOUtils.toString(inputStream, StandardCharsets.UTF_8))
                .contains("Skipping task execution for task 'hello' as there is an existing cache entry for it");
        }
    }

    @Test
    @LoadFlows("flows/valids/log-to-file-working-directory.yaml")
    void shouldNotLoseWorkingDirectoryLogsWhenItLogsToFile() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "log-to-file-working-directory");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        LogEntry subtaskRunIfError = TestsUtils.awaitLog(
            logs,
            log -> execution.getId().equals(log.getExecutionId()) && log.getMessage().startsWith("Failed evaluating runIf")
        );
        assertThat(subtaskRunIfError).isNotNull();
        LogEntry workingDirectoryLog = TestsUtils.awaitLog(
            logs,
            log -> execution.getId().equals(log.getExecutionId()) && log.getMessage().equals("Cache files didn't change, skip updating it")
        );
        assertThat(workingDirectoryLog).isNotNull();
    }
}
