package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class ReadExecutionToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private ReadExecutionTool tool;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldSummarizeExecutionWithTaskRunsWhenFound() {
        // Given — a FAILED execution with a successful task run and a failed one carrying attempt history
        State failedState = State.of(
            State.Type.FAILED, List.of(
                new State.History(State.Type.CREATED, Instant.parse("2026-01-01T00:00:00Z")),
                new State.History(State.Type.RUNNING, Instant.parse("2026-01-01T00:00:01Z")),
                new State.History(State.Type.FAILED, Instant.parse("2026-01-01T00:00:05Z"))
            )
        );
        TaskRun okRun = TaskRun.builder()
            .taskId("extract")
            .state(
                State.of(
                    State.Type.SUCCESS, List.of(
                        new State.History(State.Type.CREATED, Instant.parse("2026-01-01T00:00:00Z")),
                        new State.History(State.Type.SUCCESS, Instant.parse("2026-01-01T00:00:01Z"))
                    )
                )
            )
            .attempts(List.of(TaskRunAttempt.builder().state(new State(State.Type.SUCCESS)).build()))
            .build();
        TaskRun failedRun = TaskRun.builder()
            .taskId("load")
            .state(failedState)
            .attempts(List.of(TaskRunAttempt.builder().state(failedState).build()))
            .build();
        String executionId = IdUtils.create();
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .tenantId(MAIN_TENANT)
                .namespace(NAMESPACE)
                .flowId("flow-1")
                .state(failedState)
                .taskRunList(List.of(okRun, failedRun))
                .build()
        );

        // When
        ReadExecutionTool.Result result = tool.readExecution(executionId, CONTEXT);

        // Then — header, per-taskrun details, and the failed run's attempt state history
        assertThat(result.id()).isEqualTo(executionId);
        assertThat(result.namespace()).isEqualTo(NAMESPACE);
        assertThat(result.flowId()).isEqualTo("flow-1");
        assertThat(result.state()).isEqualTo("FAILED");
        assertThat(result.duration()).isEqualTo("PT5S");

        assertThat(result.taskRuns()).hasSize(2);
        ReadExecutionTool.TaskRunDetail extract = result.taskRuns().get(0);
        assertThat(extract.taskId()).isEqualTo("extract");
        assertThat(extract.state()).isEqualTo("SUCCESS");
        assertThat(extract.attempts()).isEqualTo(1);
        assertThat(extract.failedAttempts()).isEmpty();

        ReadExecutionTool.TaskRunDetail load = result.taskRuns().get(1);
        assertThat(load.taskId()).isEqualTo("load");
        assertThat(load.state()).isEqualTo("FAILED");
        assertThat(load.attempts()).isEqualTo(1);
        assertThat(load.failedAttempts()).containsExactly(
            new ReadExecutionTool.FailedAttempt(
                1, List.of(
                    "CREATED@2026-01-01T00:00:00Z",
                    "RUNNING@2026-01-01T00:00:01Z",
                    "FAILED@2026-01-01T00:00:05Z"
                )
            )
        );
    }

    @Test
    void shouldThrowWhenExecutionNotFound() {
        assertThatThrownBy(() -> tool.readExecution("does-not-exist", CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Execution not found");
    }
}
