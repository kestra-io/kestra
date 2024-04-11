package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class SkipExecutionServiceTest {
    @Inject
    private SkipExecutionService skipExecutionService;

    @Test
    void skipExecutionByExecutionId() {
        var executionToSkip = "aaabbbccc";
        var executionNotToSkip = "bbbcccddd";

        skipExecutionService.setSkipExecutions(List.of(executionToSkip));

        assertThat(skipExecutionService.skipExecution(executionToSkip), is(true));
        assertThat(skipExecutionService.skipExecution(executionNotToSkip), is(false));
    }

    @Test
    void skipExecutionByExecution() {
        var executionToSkip = Execution.builder().id("skip").build();
        var executionToSkipByFlow = Execution.builder().id("id").namespace("namespace").flowId("skip").build();

        skipExecutionService.setSkipExecutions(List.of("skip"));
        skipExecutionService.setSkipFlows(List.of("namespace|skip"));

        assertThat(skipExecutionService.skipExecution(executionToSkip), is(true));
        assertThat(skipExecutionService.skipExecution(executionToSkipByFlow), is(true));
    }

    @Test
    void skipExecutionByTaskRun() {
        var taskRunToSkip = TaskRun.builder().executionId("skip").build();
        var taskRunToSkipByFlow = TaskRun.builder().id("id").namespace("namespace").flowId("skip").executionId("keep").build();

        skipExecutionService.setSkipExecutions(List.of("skip"));
        skipExecutionService.setSkipFlows(List.of("namespace|skip"));

        assertThat(skipExecutionService.skipExecution(taskRunToSkip), is(true));
        assertThat(skipExecutionService.skipExecution(taskRunToSkipByFlow), is(true));
    }

    @Test
    void skipExecutionByFlowId() {
        var flowToSkip = "namespace|skip";
        var flowToSkipWithTenant = "tenant|namespace|skip";

        skipExecutionService.setSkipFlows(List.of(flowToSkip, flowToSkipWithTenant));

        assertThat(skipExecutionService.skipExecution(null, "namespace", "skip", "random"), is(true));
        assertThat(skipExecutionService.skipExecution(null, "wrong", "skip", "random"), is(false));
        assertThat(skipExecutionService.skipExecution("tenant", "namespace", "skip", "random"), is(true));
        assertThat(skipExecutionService.skipExecution("wrong", "namespace", "skip", "random"), is(false));
        assertThat(skipExecutionService.skipExecution(null, "namespace", "not_skipped", "random"), is(false));
        assertThat(skipExecutionService.skipExecution("tenant", "namespace", "not_skipped", "random"), is(false));
    }
}