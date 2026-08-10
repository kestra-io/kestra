package io.kestra.core.models.tasks;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ResumableTaskServiceTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldReturnEmptyWhenNothingRemembered() throws Exception {
        // Given
        RunContext runContext = runContext();

        // When / Then
        assertThat(ResumableTaskService.recall(runContext)).isEmpty();
    }

    @Test
    void shouldRecallHandleWhenRemembered() throws Exception {
        // Given
        RunContext runContext = runContext();

        // When
        ResumableTaskService.remember(runContext, "789", Duration.ofMinutes(60));

        // Then
        assertThat(ResumableTaskService.recall(runContext)).contains("789");
    }

    @Test
    void shouldReturnEmptyWhenForgotten() throws Exception {
        // Given
        RunContext runContext = runContext();
        ResumableTaskService.remember(runContext, "789", Duration.ofMinutes(60));

        // When
        ResumableTaskService.forget(runContext);

        // Then
        assertThat(ResumableTaskService.recall(runContext)).isEmpty();
    }

    @Test
    void shouldKeyByTaskRunId() {
        // Given
        RunContext runContext = runContext();

        // Then
        assertThat(ResumableTaskService.key(runContext))
            .isEqualTo("resume_" + runContext.taskRunInfo().taskRunId());
    }

    private RunContext runContext() {
        Log task = Log.builder().id(IdUtils.create()).type(Log.class.getName()).message("noop").build();
        return TestsUtils.mockRunContext(runContextFactory, task, Map.of());
    }
}
