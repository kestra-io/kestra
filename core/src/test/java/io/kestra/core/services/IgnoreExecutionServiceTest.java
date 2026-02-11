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
class IgnoreExecutionServiceTest {
    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    @BeforeEach
    void resetAll() {
        ignoreExecutionService.setIgnoredExecutions(null);
        ignoreExecutionService.setIgnoredFlows(null);
        ignoreExecutionService.setIgnoredNamespaces(null);
        ignoreExecutionService.setIgnoredTenants(null);
        ignoreExecutionService.setIgnoredIndexerRecords(null);
    }

    @Test
    void skipExecutionByExecutionId() {
        var executionToSkip = "aaabbbccc";
        var executionNotToSkip = "bbbcccddd";

        ignoreExecutionService.setIgnoredExecutions(List.of(executionToSkip));

        assertThat(ignoreExecutionService.ignoreExecution(executionToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(executionNotToSkip)).isFalse();
    }

    @Test
    void skipExecutionByExecution() {
        var executionToSkip = Execution.builder().id("skip").build();
        var executionToSkipByFlow = Execution.builder().tenantId("tenant").id("id").namespace("namespace").flowId("skip").build();

        ignoreExecutionService.setIgnoredExecutions(List.of("skip"));
        ignoreExecutionService.setIgnoredFlows(List.of("tenant|namespace|skip"));

        assertThat(ignoreExecutionService.ignoreExecution(executionToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(executionToSkipByFlow)).isTrue();
    }

    @Test
    void skipExecutionByTaskRun() {
        var taskRunToSkip = TaskRun.builder().executionId("skip").build();
        var taskRunToSkipByFlow = TaskRun.builder().id("id").tenantId("tenant").namespace("namespace").flowId("skip").executionId("keep").build();

        ignoreExecutionService.setIgnoredExecutions(List.of("skip"));
        ignoreExecutionService.setIgnoredFlows(List.of("tenant|namespace|skip"));

        assertThat(ignoreExecutionService.ignoreExecution(taskRunToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(taskRunToSkipByFlow)).isTrue();
    }

    @Test
    void skipExecutionByFlowId() {
        var flowToSkip = "tenant|namespace|skip";

        ignoreExecutionService.setIgnoredFlows(List.of(flowToSkip));

        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "skip", "random")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("wrong", "namespace", "skip", "random")).isFalse();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "not_skipped", "random")).isFalse();
    }

    @Test
    void skipExecutionByNamespace() {
        ignoreExecutionService.setIgnoredNamespaces(List.of("tenant|namespace"));

        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "someFlow", "someExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("anotherTenant", "namespace", "someFlow", "someExecution")).isFalse();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "anotherFlow", "anotherExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "other.namespace", "someFlow", "someExecution")).isFalse();
    }

    @Test
    void skipExecutionByTenantId() {
        ignoreExecutionService.setIgnoredTenants(List.of("tenant"));

        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "someFlow", "someExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("anotherTenant", "namespace", "someFlow", "someExecution")).isFalse();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "another.namespace", "someFlow", "someExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("anotherTenant", "another.namespace", "someFlow", "someExecution")).isFalse();
    }

    @Test
    void skipIndexedRecords() {
        ignoreExecutionService.setIgnoredIndexerRecords(List.of("indexed"));

        assertThat(ignoreExecutionService.ignoreIndexerRecord("indexed")).isTrue();
        assertThat(ignoreExecutionService.ignoreIndexerRecord("notindexed")).isFalse();
    }
}