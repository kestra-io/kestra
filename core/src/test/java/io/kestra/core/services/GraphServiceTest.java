package io.kestra.core.services;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.hierarchies.AbstractGraphTask;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class GraphServiceTest {

    @Inject
    private GraphService graphService;

    // ---- renderedProperties: pre-exec (no execution) ----

    @Test
    void shouldResolveVarsPreExecInRenderedProperties() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        // Given: flow with vars.greeting = "hello" and a task using {{ vars.greeting }}-{{ vars.region }}
        var flow = parse("flows/valids/display-resolved-properties.yaml");

        // When: build graph without execution
        var flowGraph = graphService.flowGraph(flow, Collections.emptyList());

        // Then: task-with-vars should have renderedProperties with the vars resolved
        var node = taskNode(flowGraph, "task-with-vars");
        assertThat(node.getRenderedProperties()).isNotNull();
        assertThat(node.getRenderedProperties().get("format")).isEqualTo("hello-us-east-1");
    }

    @Test
    void shouldResolveFlowIdPreExecInRenderedProperties() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        // Given: flow with a task using {{ flow.id }}
        var flow = parse("flows/valids/display-resolved-properties.yaml");

        // When
        var flowGraph = graphService.flowGraph(flow, Collections.emptyList());

        // Then: flow.id should be resolved to the flow id
        var node = taskNode(flowGraph, "task-with-flow");
        assertThat(node.getRenderedProperties()).isNotNull();
        assertThat(node.getRenderedProperties().get("format")).isEqualTo("display-resolved-properties");
    }

    @Test
    void shouldKeepInputsRawPreExecInRenderedProperties() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        // Given: flow with a task using {{ inputs.myInput }} and no execution
        var flow = parse("flows/valids/display-resolved-properties.yaml");

        // When: build graph without execution (inputs not available)
        var flowGraph = graphService.flowGraph(flow, Collections.emptyList());

        // Then: inputs expression stays raw because inputs are not present in pre-exec context
        var node = taskNode(flowGraph, "task-with-inputs");
        assertThat(node.getRenderedProperties()).isNotNull();
        assertThat(node.getRenderedProperties().get("format")).isEqualTo("{{ inputs.myInput }}");
    }

    @Test
    void shouldKeepNonDeterministicFunctionsRawInRenderedProperties() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        // Given: flow with a task using {{ now() }}
        var flow = parse("flows/valids/display-resolved-properties.yaml");

        // When
        var flowGraph = graphService.flowGraph(flow, Collections.emptyList());

        // Then: now() stays raw — non-deterministic function must not be invoked for display
        var node = taskNode(flowGraph, "task-with-now");
        assertThat(node.getRenderedProperties()).isNotNull();
        assertThat(node.getRenderedProperties().get("format")).isEqualTo("{{ now() }}");
    }

    @Test
    void shouldMaskEnvAndKeepEnvsMapAccessRawInRenderedProperties() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        // Given: a task using both env('PATH') and direct envs.* map access, with an execution context
        var flow = parse("flows/valids/display-resolved-properties.yaml");
        var execution = TestsUtils.mockExecution(flow, java.util.Collections.emptyMap());

        // When: build the graph with the full execution context (where envs would be populated)
        var flowGraph = graphService.flowGraph(flow, Collections.emptyList(), execution);

        // Then: env() is masked, and direct envs.* access is never resolved (envs is stripped from the
        // display context) — so it cannot bypass the env() mask.
        var node = taskNode(flowGraph, "task-with-envs");
        assertThat(node.getRenderedProperties()).isNotNull();
        assertThat(node.getRenderedProperties().get("format")).isEqualTo("[env: PATH]|{{ envs.path }}");
    }

    @Test
    void shouldStripRenderedPropertiesForExecutionView() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        // Given: a flow graph with renderedProperties populated
        var flow = parse("flows/valids/display-resolved-properties.yaml");
        var flowGraph = graphService.flowGraph(flow, Collections.emptyList());
        assertThat(taskNode(flowGraph, "task-with-vars").getRenderedProperties()).isNotNull();

        // When: the graph is reduced for principals with only EXECUTION-READ
        var executionView = flowGraph.forExecution();

        // Then: renderedProperties is stripped on every task node — it holds the full task config
        // and must not bypass the forExecution() field reduction.
        var taskNodes = executionView.getNodes().stream()
            .filter(AbstractGraphTask.class::isInstance)
            .map(AbstractGraphTask.class::cast)
            .filter(n -> n.getTask() != null)
            .toList();
        assertThat(taskNodes).isNotEmpty();
        taskNodes.forEach(n -> assertThat(n.getRenderedProperties()).isNull());
    }

    @Test
    void shouldPopulateRenderedPropertiesOnAllTaskNodes() throws IllegalVariableEvaluationException, IOException, FlowProcessingException {
        // Given
        var flow = parse("flows/valids/return.yaml");

        // When
        var flowGraph = graphService.flowGraph(flow, Collections.emptyList());

        // Then: every task node has renderedProperties populated
        var taskNodes = flowGraph.getNodes().stream()
            .filter(AbstractGraphTask.class::isInstance)
            .map(AbstractGraphTask.class::cast)
            .filter(n -> n.getTask() != null)
            .toList();
        assertThat(taskNodes).isNotEmpty();
        taskNodes.forEach(n ->
            assertThat(n.getRenderedProperties())
                .as("renderedProperties missing for task %s", n.getTask().getId())
                .isNotNull()
        );
    }

    // ---- helpers ----

    private FlowWithSource parse(String path) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;
        var file = new File(resource.getFile());
        return YamlParser.parse(file, FlowWithSource.class).toBuilder()
            .tenantId(MAIN_TENANT)
            .source(Files.readString(file.toPath()))
            .build();
    }

    private static AbstractGraphTask taskNode(io.kestra.core.models.hierarchies.FlowGraph flowGraph, String taskId) {
        return (AbstractGraphTask) flowGraph.getNodes().stream()
            .filter(AbstractGraphTask.class::isInstance)
            .filter(n -> ((AbstractGraphTask) n).getTask() != null && ((AbstractGraphTask) n).getTask().getId().equals(taskId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Task node not found: " + taskId));
    }
}
