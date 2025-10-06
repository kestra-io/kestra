package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkipExecutionServiceTest {
    @Inject
    private SkipExecutionService skipExecutionService;

    @BeforeEach
    void resetAll() {
        skipExecutionService.setSkipExecutions(null);
        skipExecutionService.setSkipFlows(null);
        skipExecutionService.setSkipNamespaces(null);
        skipExecutionService.setSkipTenants(null);
        skipExecutionService.setSkipIndexerRecords(null);
    }

    @Test
    void skipExecutionByExecutionId() {
        var executionToSkip = "aaabbbccc";
        var executionNotToSkip = "bbbcccddd";

        skipExecutionService.setSkipExecutions(List.of(executionToSkip));

        assertThat(skipExecutionService.skipExecution(executionToSkip)).isTrue();
        assertThat(skipExecutionService.skipExecution(executionNotToSkip)).isFalse();
    }

    @Test
    void skipExecutionByExecution() {
        var executionToSkip = Execution.builder().id("skip").build();
        var executionToSkipByFlow = Execution.builder().id("id").namespace("namespace").flowId("skip").build();

        skipExecutionService.setSkipExecutions(List.of("skip"));
        skipExecutionService.setSkipFlows(List.of("namespace|skip"));

        assertThat(skipExecutionService.skipExecution(executionToSkip)).isTrue();
        assertThat(skipExecutionService.skipExecution(executionToSkipByFlow)).isTrue();
    }

    @Test
    void skipExecutionByTaskRun() {
        var taskRunToSkip = TaskRun.builder().executionId("skip").build();
        var taskRunToSkipByFlow = TaskRun.builder().id("id").namespace("namespace").flowId("skip").executionId("keep").build();

        skipExecutionService.setSkipExecutions(List.of("skip"));
        skipExecutionService.setSkipFlows(List.of("namespace|skip"));

        assertThat(skipExecutionService.skipExecution(taskRunToSkip)).isTrue();
        assertThat(skipExecutionService.skipExecution(taskRunToSkipByFlow)).isTrue();
    }

    @Test
    void skipExecutionByFlowId() {
        var flowToSkip = "namespace|skip";
        var flowToSkipWithTenant = "tenant|namespace|skip";

        skipExecutionService.setSkipFlows(List.of(flowToSkip, flowToSkipWithTenant));

        assertThat(skipExecutionService.skipExecution(null, "namespace", "skip", "random")).isTrue();
        assertThat(skipExecutionService.skipExecution(null, "wrong", "skip", "random")).isFalse();
        assertThat(skipExecutionService.skipExecution("tenant", "namespace", "skip", "random")).isTrue();
        assertThat(skipExecutionService.skipExecution("wrong", "namespace", "skip", "random")).isFalse();
        assertThat(skipExecutionService.skipExecution(null, "namespace", "not_skipped", "random")).isFalse();
        assertThat(skipExecutionService.skipExecution("tenant", "namespace", "not_skipped", "random")).isFalse();
    }

    @Test
    void skipExecutionByNamespace() {
        skipExecutionService.setSkipNamespaces(List.of("tenant|namespace"));

        assertThat(skipExecutionService.skipExecution("tenant", "namespace", "someFlow", "someExecution")).isTrue();
        assertThat(skipExecutionService.skipExecution(null, "namespace", "someFlow", "someExecution")).isFalse();
        assertThat(skipExecutionService.skipExecution("anotherTenant", "namespace", "someFlow", "someExecution")).isFalse();
        assertThat(skipExecutionService.skipExecution("tenant", "namespace", "anotherFlow", "anotherExecution")).isTrue();
        assertThat(skipExecutionService.skipExecution("tenant", "other.namespace", "someFlow", "someExecution")).isFalse();
    }

    @Test
    void skipExecutionByTenantId() {
        skipExecutionService.setSkipTenants(List.of("tenant"));

        assertThat(skipExecutionService.skipExecution("tenant", "namespace", "someFlow", "someExecution")).isTrue();
        assertThat(skipExecutionService.skipExecution("anotherTenant", "namespace", "someFlow", "someExecution")).isFalse();
        assertThat(skipExecutionService.skipExecution("tenant", "another.namespace", "someFlow", "someExecution")).isTrue();
        assertThat(skipExecutionService.skipExecution("anotherTenant", "another.namespace", "someFlow", "someExecution")).isFalse();
    }

    @Test
    void skipIndexedRecords() {
        skipExecutionService.setSkipIndexerRecords(List.of("indexed"));

        assertThat(skipExecutionService.skipIndexerRecord("indexed")).isTrue();
        assertThat(skipExecutionService.skipIndexerRecord("notindexed")).isFalse();
    }
}