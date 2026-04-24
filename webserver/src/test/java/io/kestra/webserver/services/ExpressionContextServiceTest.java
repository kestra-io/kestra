package io.kestra.webserver.services;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.kestra.core.Helpers;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.pebble.PebbleExpressionService;
import io.kestra.core.runners.pebble.PebbleFunction;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContext;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.libs.copilot.services.ai.PebbleExpressionsFormatter;

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

    @lombok.SneakyThrows
    private Flow parseFlow(String yaml) {
        return pluginDefaultService.parseFlowWithAllDefaults(null, yaml, false);
    }

    @Test
    void shouldReturnInputExpressions() {
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

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> inputs = result.categories().get(ExpressionCategory.INPUTS);
        assertThat(inputs).contains("inputs.myString", "inputs.myInt");
    }

    @Test
    void shouldReturnVariableExpressions() {
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

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> variables = result.categories().get(ExpressionCategory.VARIABLES);
        assertThat(variables).contains("vars.myVar", "vars.otherVar");
    }

    @Test
    void shouldReturnTaskOutputExpressions() {
        // Return task produces an output with a "value" field
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

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> outputs = result.categories().get(ExpressionCategory.TASK_OUTPUTS);
        assertThat(outputs).anyMatch(e -> e.startsWith("outputs.t1."));
    }

    @Test
    void shouldFilterOutputsByTaskId() {
        // t2 should not see t2's own outputs, only t1's
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

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, "t2");

        List<String> outputs = result.categories().get(ExpressionCategory.TASK_OUTPUTS);
        assertThat(outputs).anyMatch(e -> e.startsWith("outputs.t1."));
        assertThat(outputs).noneMatch(e -> e.startsWith("outputs.t2."));
    }

    @Test
    void shouldUseBracketNotationForHyphenatedTaskIds() {
        // Tasks whose IDs contain hyphens must use outputs['task-id'].prop in Pebble,
        // because the hyphen is parsed as subtraction in dot notation.
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: http-call
                type: io.kestra.plugin.core.debug.Return
                format: hello
              - id: normal_task
                type: io.kestra.plugin.core.debug.Return
                format: world
            """;
        Flow flow = parseFlow(yaml);

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> outputs = result.categories().get(ExpressionCategory.TASK_OUTPUTS);
        // Hyphenated ID → bracket notation
        assertThat(outputs).anyMatch(e -> e.startsWith("outputs['http-call']."));
        assertThat(outputs).noneMatch(e -> e.startsWith("outputs.http-call."));
        // Plain ID → dot notation (unchanged)
        assertThat(outputs).anyMatch(e -> e.startsWith("outputs.normal_task."));
    }

    @Test
    void shouldReturnTriggerOutputsWithoutTriggerIdInPath() {
        // Schedule trigger produces outputs like trigger.date, trigger.next
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

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        // Trigger outputs should use "trigger.*" prefix, NOT "trigger.mySchedule.*"
        List<String> outputs = result.categories().get(ExpressionCategory.TASK_OUTPUTS);
        assertThat(outputs).noneMatch(e -> e.contains("trigger.mySchedule."));
        // Schedule trigger must expose its well-known date fields
        assertThat(outputs).contains("trigger.date", "trigger.next", "trigger.previous");
    }

    @Test
    void shouldReturnExecutionContextPaths() {
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> ctx = result.categories().get(ExpressionCategory.EXECUTION_CONTEXT);
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
            "taskrun.iteration",
            "kestra.environment",
            "kestra.url"
        );
    }

    @Test
    void shouldReturnLabelExpressions() {
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

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> ctx = result.categories().get(ExpressionCategory.EXECUTION_CONTEXT);
        assertThat(ctx).contains("labels.env", "labels.team");
    }

    @Test
    void shouldReturnAllPebbleFilters() {
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> filters = result.categories().get(ExpressionCategory.FILTERS);
        assertThat(filters).isNotEmpty();
        // Filters are plain names — no "| " prefix; the category display name already notes pipe syntax
        assertThat(filters).noneMatch(f -> f.startsWith("| "));
        // Every filter from PebbleExpressionService should be present
        for (String filter : pebbleExpressionService.filters()) {
            assertThat(filters).contains(filter);
        }
    }

    @Test
    void shouldReturnAllPebbleFunctions() {
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        List<String> functions = result.categories().get(ExpressionCategory.FUNCTIONS);
        // Every function should be present as PebbleFunction.toString() signature, e.g. "now()" or "secret(key='MY_SECRET')"
        for (PebbleFunction fn : pebbleExpressionService.functions()) {
            assertThat(functions).contains(fn.toString());
        }
    }

    @Test
    void shouldReturnEmptyInputsForFlowWithoutInputs() {
        String yaml = """
            id: test-flow
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        assertThat(result.categories().get(ExpressionCategory.INPUTS)).isEmpty();
        assertThat(result.categories().get(ExpressionCategory.VARIABLES)).isEmpty();
    }

    @Test
    void buildGlobalExpressionContextShouldContainOnlyFiltersAndFunctions() {
        ExpressionContext result = expressionContextService.buildGlobalExpressionContext();

        assertThat(result.categories()).containsOnlyKeys(
            ExpressionCategory.FILTERS,
            ExpressionCategory.FUNCTIONS
        );
        assertThat(result.categories().get(ExpressionCategory.FILTERS)).isNotEmpty();
        assertThat(result.categories().get(ExpressionCategory.FUNCTIONS)).isNotEmpty();

        // Verify no flow-scoped category entries
        List<String> allEntries = result.categories().values().stream()
            .flatMap(List::stream)
            .toList();
        assertThat(allEntries).noneMatch(e -> e.startsWith("flow."));
        assertThat(allEntries).noneMatch(e -> e.startsWith("execution."));
        assertThat(allEntries).noneMatch(e -> e.startsWith("task."));
        assertThat(allEntries).noneMatch(e -> e.startsWith("taskrun."));
        assertThat(allEntries).noneMatch(e -> e.startsWith("trigger."));
        assertThat(allEntries).noneMatch(e -> e.startsWith("outputs."));
        assertThat(allEntries).noneMatch(e -> e.startsWith("inputs."));
    }

    @Test
    void formatForPromptShouldSkipEmptyCategoriesAndRenderNonEmpty() {
        // Build a context where INPUTS is empty (no inputs in flow), FILTERS is non-empty
        String yaml = """
            id: format-test
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        ExpressionContext context = expressionContextService.buildExpressionContext(parseFlow(yaml), null);

        String result = PebbleExpressionsFormatter.format(context.toDisplayNameMap());

        // The formatted string must not be empty (filters + functions present)
        assertThat(result).isNotBlank();

        // Empty categories (INPUTS, VARIABLES for this flow) must produce no line in the output
        String inputsCategoryName = ExpressionCategory.INPUTS.displayName();
        String variablesCategoryName = ExpressionCategory.VARIABLES.displayName();
        assertThat(result).doesNotContain(inputsCategoryName + ":");
        assertThat(result).doesNotContain(variablesCategoryName + ":");

        // Non-empty categories (FILTERS, FUNCTIONS) must be present
        String filtersCategoryName = ExpressionCategory.FILTERS.displayName();
        String functionsCategoryName = ExpressionCategory.FUNCTIONS.displayName();
        assertThat(result).contains(filtersCategoryName + ":");
        assertThat(result).contains(functionsCategoryName + ":");
    }

    @Test
    void shouldReturnAllCategoriesForMinimalFlow() {
        String yaml = """
            id: minimal
            namespace: io.kestra.test
            tasks:
              - id: t1
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;
        Flow flow = parseFlow(yaml);

        ExpressionContext result = expressionContextService.buildExpressionContext(flow, null);

        // All 9 categories should be present (even if some are empty)
        assertThat(result.categories()).containsKeys(
            ExpressionCategory.TASK_OUTPUTS,
            ExpressionCategory.EXECUTION_CONTEXT,
            ExpressionCategory.INPUTS,
            ExpressionCategory.VARIABLES,
            ExpressionCategory.SECRETS,
            ExpressionCategory.KV_PAIRS,
            ExpressionCategory.NAMESPACE_FILES,
            ExpressionCategory.FILTERS,
            ExpressionCategory.FUNCTIONS
        );
        // Execution context, filters, and functions should never be empty
        assertThat(result.categories().get(ExpressionCategory.EXECUTION_CONTEXT)).isNotEmpty();
        assertThat(result.categories().get(ExpressionCategory.FILTERS)).isNotEmpty();
        assertThat(result.categories().get(ExpressionCategory.FUNCTIONS)).isNotEmpty();
    }
}
