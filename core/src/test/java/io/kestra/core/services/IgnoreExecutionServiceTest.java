package io.kestra.core.services;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.events.EventId;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;

import jakarta.inject.Inject;

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
        ignoreExecutionService.setIgnoredQueueRecords(null);
    }

    @Test
    void ignoreExecutionByExecutionId() {
        var executionToSkip = "aaabbbccc";
        var executionNotToSkip = "bbbcccddd";

        ignoreExecutionService.setIgnoredExecutions(List.of(executionToSkip));

        assertThat(ignoreExecutionService.ignoreExecution(executionToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(executionNotToSkip)).isFalse();
    }

    @Test
    void ignoreExecutionByExecution() {
        var executionToSkip = Execution.builder().id("skip").build();
        var executionToSkipByFlow = Execution.builder().tenantId("tenant").id("id").namespace("namespace").flowId("skip").build();

        ignoreExecutionService.setIgnoredExecutions(List.of("skip"));
        ignoreExecutionService.setIgnoredFlows(List.of("tenant|namespace|skip"));

        assertThat(ignoreExecutionService.ignoreExecution(executionToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(executionToSkipByFlow)).isTrue();
    }

    @Test
    void ignoreExecutionByExecutionEvent() {
        var executionToSkip = new ExecutionEvent("not", "not", "not", "skip", Instant.now(), ExecutionEventType.CREATED);
        var executionToSkipByFlow = new ExecutionEvent("tenant", "namespace", "skip", "not", Instant.now(), ExecutionEventType.CREATED);

        ignoreExecutionService.setIgnoredExecutions(List.of("skip"));
        ignoreExecutionService.setIgnoredFlows(List.of("tenant|namespace|skip"));

        assertThat(ignoreExecutionService.ignoreExecution(executionToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(executionToSkipByFlow)).isTrue();
    }

    @Test
    void ignoreExecutionByExecutionCommand() {
        var executionToSkip = new Restart("not", "not", "not", "skip", Instant.now(), EventId.create(), null, null);
        var executionToSkipByFlow = new Restart("tenant", "namespace", "skip", "not", Instant.now(), EventId.create(), null, null);

        ignoreExecutionService.setIgnoredExecutions(List.of("skip"));
        ignoreExecutionService.setIgnoredFlows(List.of("tenant|namespace|skip"));

        assertThat(ignoreExecutionService.ignoreExecution(executionToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(executionToSkipByFlow)).isTrue();
    }

    @Test
    void ignoreExecutionByTaskRun() {
        var taskRunToSkip = TaskRun.builder().executionId("skip").build();
        var taskRunToSkipByFlow = TaskRun.builder().id("id").tenantId("tenant").namespace("namespace").flowId("skip").executionId("keep").build();

        ignoreExecutionService.setIgnoredExecutions(List.of("skip"));
        ignoreExecutionService.setIgnoredFlows(List.of("tenant|namespace|skip"));

        assertThat(ignoreExecutionService.ignoreExecution(taskRunToSkip)).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution(taskRunToSkipByFlow)).isTrue();
    }

    @Test
    void ignoreExecutionByFlowId() {
        var flowToSkip = "tenant|namespace|skip";

        ignoreExecutionService.setIgnoredFlows(List.of(flowToSkip));

        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "skip", "random")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("wrong", "namespace", "skip", "random")).isFalse();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "not_skipped", "random")).isFalse();
    }

    @Test
    void ignoreExecutionByNamespace() {
        ignoreExecutionService.setIgnoredNamespaces(List.of("tenant|namespace"));

        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "someFlow", "someExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("anotherTenant", "namespace", "someFlow", "someExecution")).isFalse();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "anotherFlow", "anotherExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "other.namespace", "someFlow", "someExecution")).isFalse();
    }

    @Test
    void ignoreExecutionByTenantId() {
        ignoreExecutionService.setIgnoredTenants(List.of("tenant"));

        assertThat(ignoreExecutionService.ignoreExecution("tenant", "namespace", "someFlow", "someExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("anotherTenant", "namespace", "someFlow", "someExecution")).isFalse();
        assertThat(ignoreExecutionService.ignoreExecution("tenant", "another.namespace", "someFlow", "someExecution")).isTrue();
        assertThat(ignoreExecutionService.ignoreExecution("anotherTenant", "another.namespace", "someFlow", "someExecution")).isFalse();
    }

    @Test
    void ignoreIndexedRecords() {
        ignoreExecutionService.setIgnoredIndexerRecords(List.of("indexed"));

        assertThat(ignoreExecutionService.ignoreIndexerRecord("indexed")).isTrue();
        assertThat(ignoreExecutionService.ignoreIndexerRecord("notindexed")).isFalse();
    }

    @Test
    void ignoreQueueRecord() {
        ignoreExecutionService.setIgnoredQueueRecords(List.of("queue"));

        assertThat(ignoreExecutionService.ignoreQueueRecord("queue")).isTrue();
        assertThat(ignoreExecutionService.ignoreQueueRecord("notqueue")).isFalse();
    }
}