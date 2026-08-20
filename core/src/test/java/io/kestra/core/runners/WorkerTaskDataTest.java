package io.kestra.core.runners;

import java.util.Map;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class WorkerTaskDataTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private FlowInputOutput flowInputOutput;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    @LoadFlows(value = { "flows/valids/input-log-secret.yaml" }, tenantId = "tenant6624")
    void shouldNotSerializePlaintextSecretsWhenBuildingWireData() throws Exception {
        // Given
        RunContext runContext = runContextForSecretInputs("tenant6624");

        // When
        String serialized = JacksonMapper.ofJson().writeValueAsString(WorkerTaskData.from(runContext));

        // Then
        assertThat(serialized).doesNotContain("s3cr3t");
        assertThat(serialized).doesNotContain("n3st3d");
        assertThat(serialized).contains("io.kestra.datatype:aes_encrypted");
    }

    @Test
    @LoadFlows(value = { "flows/valids/input-log-secret.yaml" }, tenantId = "tenant6624bis")
    void shouldRenderSecretInputsAsPlaintextWhenReadFromTheRunContext() throws Exception {
        // Given
        RunContext runContext = runContextForSecretInputs("tenant6624bis");

        // When
        String rendered = runContext.render("{{ inputs.secret }}-{{ inputs.nested.key }}");

        // Then
        assertThat(rendered).isEqualTo("s3cr3t-n3st3d");
    }

    /**
     * Builds a run context for the {@code input-log-secret} flow, which declares both a top-level and a dotted
     * SECRET input so one payload covers both shapes.
     */
    private RunContext runContextForSecretInputs(String tenantId) throws Exception {
        Flow flow = flowRepository.findById(tenantId, "io.kestra.tests", "input-log-secret").orElseThrow();
        Execution execution = Execution.builder()
            .id("exec6624")
            .namespace(flow.getNamespace())
            .tenantId(flow.getTenantId())
            .flowId(flow.getId())
            .flowRevision(1)
            .state(new State())
            .build();
        execution = execution.withInputs(
            flowInputOutput.readExecutionInputs(flow, execution, Map.of("secret", "s3cr3t", "nested.key", "n3st3d"))
        );

        var task = flow.getTasks().getFirst();
        return runContextFactory.of(flow, task, execution, TaskRun.of(execution, ResolvedTask.of(task)));
    }
}
