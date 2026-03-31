package io.kestra.webserver.services;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.kestra.core.Helpers;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.pebble.PebbleExpressionService;
import io.kestra.core.runners.pebble.PebbleFunction;
import io.kestra.core.services.PluginDefaultService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExpressionContextServiceTest {

    @Inject
    private ExpressionContextService expressionContextService;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private PebbleExpressionService pebbleExpressionService;

    @BeforeAll
    public static void beforeAll() {
        Helpers.loadExternalPluginsFromClasspath();
    }

    @SuppressWarnings("SneakyThrows")
    @lombok.SneakyThrows
    private Flow parseFlow(String yaml) {
        return pluginDefaultService.parseFlowWithAllDefaults(null, yaml, false);
    }

    @Test
    void shouldReturnInputExpressions() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            inputs:
              - id: myString
                type: STRING
              - id: myInt
                type: INT
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        assertThat(result).containsKey("Inputs");
        List<String> inputs = result.get("Inputs");
        assertThat(inputs).contains("inputs.myString", "inputs.myInt");
    }

    @Test
    void shouldReturnVariableExpressions() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            variables:
              myVar: someValue
              otherVar: otherValue
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        assertThat(result).containsKey("Variables");
        List<String> variables = result.get("Variables");
        assertThat(variables).contains("vars.myVar", "vars.otherVar");
    }

    @Test
    void shouldReturnTaskOutputExpressions() {
        // Given — Return task produces an output with a "value" field
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.debug.Return
                format: hello
              - id: t2
                type: io.kestra.plugin.core.log.Log
                message: "{{ outputs.t1.value }}"
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        assertThat(result).containsKey("Task Outputs");
        List<String> outputs = result.get("Task Outputs");
        assertThat(outputs).anyMatch(e -> e.startsWith("outputs.t1."));
    }

    @Test
    void shouldFilterOutputsByTaskId() {
        // Given — t2 should not see t2's own outputs, only t1's
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.debug.Return
                format: hello
              - id: t2
                type: io.kestra.plugin.core.debug.Return
                format: world
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, "t2");

        // Then
        List<String> outputs = result.get("Task Outputs");
        assertThat(outputs).anyMatch(e -> e.startsWith("outputs.t1."));
        assertThat(outputs).noneMatch(e -> e.startsWith("outputs.t2."));
    }

    @Test
    void shouldReturnTriggerOutputsWithoutTriggerIdInPath() {
        // Given — Schedule trigger produces outputs like trigger.date, trigger.next
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: mySchedule
                type: io.kestra.plugin.core.trigger.Schedule
                cron: "0 0 * * *"
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then — trigger outputs should use "trigger.*" prefix, NOT "trigger.mySchedule.*"
        List<String> outputs = result.get("Task Outputs");
        assertThat(outputs).noneMatch(e -> e.contains("trigger.mySchedule."));
        assertThat(outputs).anyMatch(e -> e.startsWith("trigger."));
    }

    @Test
    void shouldReturnExecutionContextPaths() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        assertThat(result).containsKey("Execution Context");
        List<String> ctx = result.get("Execution Context");
        assertThat(ctx).contains(
            "flow.id",
            "flow.namespace",
            "execution.id",
            "execution.startDate",
            "execution.state",
            "task.id",
            "task.type",
            "taskrun.id",
            "taskrun.startDate",
            "taskrun.value",
            "taskrun.iteration"
        );
    }

    @Test
    void shouldReturnLabelExpressions() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            labels:
              env: prod
              team: platform
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        List<String> ctx = result.get("Execution Context");
        assertThat(ctx).contains("labels.env", "labels.team");
    }

    @Test
    void shouldReturnAllPebbleFilters() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        assertThat(result).containsKey("Filters");
        List<String> filters = result.get("Filters");
        assertThat(filters).isNotEmpty();
        // All filters should start with "| "
        assertThat(filters).allMatch(f -> f.startsWith("| "));
        // Every filter from PebbleExpressionService should be present
        for (String filter : pebbleExpressionService.filters()) {
            assertThat(filters).contains("| " + filter);
        }
    }

    @Test
    void shouldReturnAllPebbleFunctions() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        assertThat(result).containsKey("Other");
        List<String> other = result.get("Other");
        // Every function from PebbleExpressionService should be present
        for (PebbleFunction fn : pebbleExpressionService.functions()) {
            String formatted = ExpressionContextService.formatFunction(fn);
            assertThat(other).contains(formatted);
        }
    }

    @Test
    void shouldReturnKestraConfigurationPaths() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        List<String> other = result.get("Other");
        assertThat(other).contains("kestra.environment", "kestra.url");
    }

    @Test
    void shouldReturnEmptyInputsForFlowWithoutInputs() {
        // Given
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then
        assertThat(result.get("Inputs")).isEmpty();
        assertThat(result.get("Variables")).isEmpty();
    }

    @Test
    void shouldReturnAllCategoriesForMinimalFlow() {
        // Given — minimal flow with just one task
        String yaml = """
            id: minimal
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        // When
        Map<String, List<String>> result = expressionContextService.buildExpressionContext(flow, null);

        // Then — all categories should be present even if some are empty
        assertThat(result).containsKeys(
            "Task Outputs", "Execution Context", "Inputs", "Variables",
            "Secrets", "KV Pairs", "Namespace Files", "Filters", "Other"
        );
        // Execution context and filters should never be empty
        assertThat(result.get("Execution Context")).isNotEmpty();
        assertThat(result.get("Filters")).isNotEmpty();
        assertThat(result.get("Other")).isNotEmpty();
    }

    @Test
    void shouldFormatFunctionWithNoArgs() {
        // Given
        PebbleFunction fn = new PebbleFunction("now", List.of());

        // When
        String formatted = ExpressionContextService.formatFunction(fn);

        // Then
        assertThat(formatted).isEqualTo("now()");
    }

    @Test
    void shouldFormatFunctionWithArgsAndDefaults() {
        // Given
        PebbleFunction fn = new PebbleFunction("secret", List.of(
            new PebbleFunction.Argument("key", "'MY_SECRET'")
        ));

        // When
        String formatted = ExpressionContextService.formatFunction(fn);

        // Then
        assertThat(formatted).isEqualTo("secret('MY_SECRET')");
    }

    @Test
    void shouldFormatFunctionWithArgsWithoutDefaults() {
        // Given
        PebbleFunction fn = new PebbleFunction("render", List.of(
            new PebbleFunction.Argument("expression", null)
        ));

        // When
        String formatted = ExpressionContextService.formatFunction(fn);

        // Then
        assertThat(formatted).isEqualTo("render(expression)");
    }

    @Test
    void shouldFormatFunctionWithMixedArgs() {
        // Given
        PebbleFunction fn = new PebbleFunction("jq", List.of(
            new PebbleFunction.Argument("value", null),
            new PebbleFunction.Argument("expression", "'.data'")
        ));

        // When
        String formatted = ExpressionContextService.formatFunction(fn);

        // Then
        assertThat(formatted).isEqualTo("jq(value, '.data')");
    }
}
