package io.kestra.webserver.models.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLightExecutionTest {

    @Test
    void shouldExposeLastTaskRunWithItsAttemptCount() {
        Execution execution = Execution.builder()
            .taskRunList(List.of(
                TaskRun.builder().taskId("first").build(),
                TaskRun.builder()
                    .taskId("last")
                    .attempts(List.of(TaskRunAttempt.builder().build(), TaskRunAttempt.builder().build()))
                    .build()
            ))
            .build();

        ApiLightExecution dto = ApiLightExecution.of(execution);

        assertThat(dto.lastTaskRun().taskId()).isEqualTo("last");
        assertThat(dto.lastTaskRun().attempts()).isEqualTo(2);
    }

    @Test
    void shouldReturnNoLastTaskRunWhenExecutionHasNoTaskRun() {
        assertThat(ApiLightExecution.of(Execution.builder().build()).lastTaskRun()).isNull();
    }
}
