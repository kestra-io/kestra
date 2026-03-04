package io.kestra.core.killswitch;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class KillSwitchServiceTest {
    @Inject
    private KillSwitchService killSwitchService;

    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    @Test
    void evaluateExecutionId() {
        String executionId = IdUtils.create();
        assertThat(killSwitchService.evaluate(executionId), is(EvaluationType.PASS));

        ignoreExecutionService.setIgnoredExecutions(List.of(executionId));
        assertThat(killSwitchService.evaluate(executionId), is(EvaluationType.IGNORE));

        ignoreExecutionService.setIgnoredExecutions(null);
        assertThat(killSwitchService.evaluate(executionId), is(EvaluationType.PASS));
    }

    @Test
    void evaluateExecution() {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.test")
            .flowId("test")
            .build();

        assertThat(killSwitchService.evaluate(execution), is(EvaluationType.PASS));

        ignoreExecutionService.setIgnoredExecutions(List.of(execution.getId()));
        assertThat(killSwitchService.evaluate(execution), is(EvaluationType.IGNORE));

        ignoreExecutionService.setIgnoredExecutions(null);
        assertThat(killSwitchService.evaluate(execution), is(EvaluationType.PASS));
    }

    @Test
    void evaluateTaskRun() {
        TaskRun taskRun = TaskRun.builder()
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .namespace("io.kestra.test")
            .flowId("test")
            .build();

        assertThat(killSwitchService.evaluate(taskRun), is(EvaluationType.PASS));

        ignoreExecutionService.setIgnoredExecutions(List.of(taskRun.getExecutionId()));
        assertThat(killSwitchService.evaluate(taskRun), is(EvaluationType.IGNORE));

        ignoreExecutionService.setIgnoredExecutions(null);
        assertThat(killSwitchService.evaluate(taskRun), is(EvaluationType.PASS));
    }
}