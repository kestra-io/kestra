package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class SubflowFunctionTest {
    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    private VariableRenderer variableRenderer;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @LoadFlows("flows/valids/subflow-function-child.yaml")
    void shouldRunSubflowAndExposeFlowOutputs() throws IllegalVariableEvaluationException {
        // Given-When
        String rendered = variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child', inputs={'region': 'us'}).outputs.datacenters }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        );

        // Then: the parent reads the subflow's flow-level output
        assertThat(rendered).isEqualTo("dc-us");

        // And: the spawned execution is tagged SUBFLOW_FUNCTION so it stays out of the main list
        List<Execution> executions = executionRepository.findByFlowId(MAIN_TENANT, NAMESPACE, "subflow-function-child", Pageable.UNPAGED);
        assertThat(executions).isNotEmpty();
        assertThat(executions).allMatch(execution -> execution.getKind() == ExecutionKind.SUBFLOW_FUNCTION);
    }

    @Test
    @LoadFlows("flows/valids/subflow-function-child.yaml")
    void shouldUseSubflowInputDefaultsWhenNotProvided() throws IllegalVariableEvaluationException {
        // When: no inputs passed, the subflow's own default ('eu') is used
        String rendered = variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child').outputs.datacenters }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        );

        // Then
        assertThat(rendered).isEqualTo("dc-eu");
    }

    @Test
    void shouldThrowWhenFlowNotFound() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='does-not-exist') }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("does-not-exist");
    }

    @Test
    @LoadFlows("flows/valids/subflow-function-failing.yaml")
    void shouldThrowWhenSubflowFails() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-failing') }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("FAILED");
    }

    @Test
    @LoadFlows("flows/valids/subflow-function-recursive.yaml")
    void shouldThrowWhenMaxDepthExceeded() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-recursive') }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("depth");
    }

    @Test
    void shouldThrowWhenNamespaceOrIdMissing() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(id='subflow-function-child') }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("'namespace' and 'id'");
    }

    @Test
    void shouldThrowWhenNoFlowContext() {
        // Rendering outside a flow context (no 'flow' variable) must fail clearly
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child') }}",
            Map.of()
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("flow context");
    }
}
