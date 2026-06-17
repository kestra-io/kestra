package io.kestra.core.runners.pebble.functions;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// subflow() renders at execute-form time on the webserver and is gated on a WEBSERVER/STANDALONE
// server-type. The core test environment leaves kestra.server-type unset (which excludes the bean),
// so set WEBSERVER explicitly: it satisfies the gate without pulling in the STANDALONE-only executor
// liveness machinery that startRunner would otherwise wire up.
@Property(name = "kestra.server-type", value = "WEBSERVER")
@KestraTest(startRunner = true)
class SubflowFunctionTest {
    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    private VariableRenderer variableRenderer;

    @Test
    @LoadFlows("flows/valids/subflow-function-child.yaml")
    void shouldRunSubflowAndExposeFlowOutputs() throws IllegalVariableEvaluationException {
        // Given-When
        String rendered = variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child', inputs={'region': 'us'}).outputs.datacenters }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        );

        // Then: the parent reads the subflow's flow-level output through the returned wrapper
        assertThat(rendered).isEqualTo("dc-us");
    }

    @Test
    @LoadFlows("flows/valids/subflow-function-child.yaml")
    void shouldExposeStateAndLabelsOnTheResult() throws IllegalVariableEvaluationException {
        // When: the result wrapper exposes the terminal state
        String state = variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child').state }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        );
        assertThat(state).isEqualTo("SUCCESS");

        // And: the system.from label is set so subflow() executions can be told apart
        String from = variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child').labels['system.from'] }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        );
        assertThat(from).isEqualTo("subflow");
    }

    @Test
    @LoadFlows("flows/valids/subflow-function-child.yaml")
    void shouldMergeProvidedLabels() throws IllegalVariableEvaluationException {
        // When: caller-provided labels are attached to the execution and exposed on the result
        String team = variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child', labels={'team': 'data'}).labels.team }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        );

        // Then
        assertThat(team).isEqualTo("data");
    }

    @Test
    @LoadFlows("flows/valids/subflow-function-child.yaml")
    void shouldThrowWhenSettingReservedSystemLabel() {
        // The caller may not set reserved system.* labels via the 'labels' argument
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child', labels={'system.test': 'x'}) }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("system label");
    }

    @Test
    @LoadFlows("flows/valids/subflow-function-child.yaml")
    void shouldAllowCorrelationIdSystemLabel() throws IllegalVariableEvaluationException {
        // system.correlationId is the one system label a caller may propagate
        String state = variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child', labels={'system.correlationId': 'abc'}).state }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        );
        assertThat(state).isEqualTo("SUCCESS");
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
    void shouldThrowWhenFlowCallsItself() {
        // The flow's input default calls subflow() on its own id. Input resolution is synchronous and on
        // the same thread (Execution.newExecution applies the input resolver inline), so the per-thread
        // depth cap catches the self-recursion without a dedicated self-call check.
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-recursive') }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("depth");
    }

    @Test
    @LoadFlows({"flows/valids/subflow-function-mutual-a.yaml", "flows/valids/subflow-function-mutual-b.yaml"})
    void shouldThrowWhenMaxDepthExceeded() {
        // a -> b -> a -> ... mutual recursion across two flows, also bounded by the depth cap
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-mutual-a') }}",
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

    @Test
    void shouldThrowWhenUsedInTaskContext() {
        // A top-level 'taskrun' variable means we are rendering a task property, which may run on a worker
        Map<String, Object> variables = new HashMap<>(getVariables(MAIN_TENANT, NAMESPACE));
        variables.put("taskrun", Map.of("id", "abc"));

        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child') }}",
            variables
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("task or trigger");
    }

    @Test
    void shouldThrowWhenUsedInTriggerContext() {
        // A top-level 'trigger' variable means we are rendering a trigger property, which may run on a worker
        Map<String, Object> variables = new HashMap<>(getVariables(MAIN_TENANT, NAMESPACE));
        variables.put("trigger", Map.of("id", "schedule"));

        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child') }}",
            variables
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("task or trigger");
    }

    @Test
    void shouldThrowWhenTimeoutExceedsMax() {
        // The configured maxTimeout (PT5M) is a hard cap; a larger 'timeout' argument is rejected
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ subflow(namespace='" + NAMESPACE + "', id='subflow-function-child', timeout='PT10M') }}",
            getVariables(MAIN_TENANT, NAMESPACE)
        )).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("exceeds the maximum");
    }
}
