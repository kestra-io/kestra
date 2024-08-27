package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class LogToFileTest extends AbstractMemoryRunnerTest {
    @Inject
    private StorageInterface storage;

    @Test
    void task() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "log-to-file");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getAttempts(), hasSize(1));
        TaskRunAttempt attempt = taskRun.getAttempts().getFirst();
        assertThat(attempt.getLogFile(), notNullValue());

        InputStream inputStream = storage.get(null, attempt.getLogFile());
        List<String> strings = IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        assertThat(strings, notNullValue());
        assertThat(strings.size(), is(1));
        assertThat(strings.getFirst(), containsString("INFO"));
        assertThat(strings.getFirst(), containsString("Hello World!"));
    }
}
