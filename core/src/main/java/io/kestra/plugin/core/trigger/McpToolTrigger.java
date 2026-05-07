package io.kestra.plugin.core.trigger;

import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.services.LabelService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.Regex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor
@Schema(
    title = "Expose a flow as an MCP tool.",
    description = """
        Registers this flow as a named tool on the configured MCP server, making it callable by \
        MCP-compatible AI agents such as Claude Desktop, Claude Code, Cursor, and Codex. \
        The tool's JSON schema is auto-generated from the flow's declared `inputs` and `outputs`. \
        Each invocation creates a new flow execution tagged with `system.from:mcp` for observability.

        A flow can be registered on exactly one MCP server at a time (controlled by `mcpServer`). \
        Multiple flows can share the same MCP server, each appearing as a separate tool in the tool list.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Expose a greeting flow as an MCP tool on the default server.",
            full = true,
            code = """
                id: hello_world
                namespace: company.team

                inputs:
                  - id: user
                    type: STRING
                    defaults: John Doe

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.output.OutputValues
                    values:
                      myresult: "hello {{ inputs.user }}"

                outputs:
                  - id: result
                    type: STRING
                    value: "{{ outputs.hello.values.myresult }}"

                triggers:
                  - id: mcp
                    type: io.kestra.plugin.core.trigger.McpToolTrigger
                    toolName: hello_world
                    title: Hello World greeting tool
                    toolDescription: Returns a personalised greeting for the given user name.
                    mcpServer: default
                """
        ),
        @Example(
            title = "Read-only reporting tool registered on a dedicated analytics MCP server.",
            full = true,
            code = """
                id: sales_report
                namespace: company.analytics

                inputs:
                  - id: period
                    type: STRING
                    description: "Reporting period, e.g. 2025-Q1"

                tasks:
                  - id: generate_report
                    type: io.kestra.plugin.core.log.Log
                    message: "Generating report for {{ inputs.period }}"

                triggers:
                  - id: mcp
                    type: io.kestra.plugin.core.trigger.McpToolTrigger
                    toolName: sales_report
                    title: Sales Report Generator
                    toolDescription: Generates a sales report for the given period. Safe to call repeatedly with the same arguments.
                    mcpServer: analytics-server
                    annotations:
                      readOnly: true
                      destructive: false
                      idempotent: true
                """
        ),
    }
)
public class McpToolTrigger extends AbstractTrigger implements TriggerOutput<McpToolTrigger.Output> {
    @PluginProperty
    @NotNull
    @Size(min = 1, max = 64)
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9_.-]*[a-zA-Z0-9])?$",
        message = "Tool name must contain only alphanumeric characters hyphens underscores or dots and must start and end with an alphanumeric character"
    )
    @Schema(
        title = "Unique tool identifier used by AI agents to invoke this tool.",
        description = """
            Must contain only alphanumeric characters, hyphens, underscores, or dots, and must \
            start and end with an alphanumeric character. Maximum 64 characters. \
            Choose a short, descriptive name that reflects the flow's purpose \
            (e.g., `get_customer_orders` or `send_slack_notification`).
            """
    )
    private String toolName;

    @PluginProperty
    @NotNull
    @Schema(
        title = "Human-readable display name shown to AI agents in the tool list.",
        description = "A concise, descriptive title that helps the AI agent understand what this tool does at a glance."
    )
    private String title;

    @PluginProperty
    @NotNull
    @Schema(
        title = "Description of what this tool does and when an AI agent should call it.",
        description = """
            Used by AI agents to decide whether to invoke this tool for a given user request. \
            A well-written description significantly improves tool-selection accuracy. \
            Describe what the tool does, when it should be called, and what inputs it expects.
            """
    )
    private String toolDescription;

    @PluginProperty
    @Builder.Default
    @Schema(
        title = "Behavioural hints that inform AI agents how to invoke this tool safely.",
        description = "These annotations follow the MCP tool annotation specification and help AI agents reason about side effects, idempotency, and safety before calling the tool."
    )
    private Annotations annotations = new Annotations(false, true, true, false, false);

    @PluginProperty
    @NotNull
    @Builder.Default
    @Schema(
        title = "ID of the MCP server on which this tool is registered.",
        description = """
            Must match the `id` of an existing MCP server configured in **Admin → MCP Servers**. \
            Defaults to `default`, the server that is automatically provisioned for every tenant.
            """
    )
    private String mcpServer = DEFAULT_SERVER_ID;

    public static final String DEFAULT_SERVER_ID = "default";

    public Execution evaluate(
        Flow flow,
        Map<String, Object> input,
        Map<String, Object> additionalInputs,
        List<Label> additionalLabels
    ) {
        Execution execution = Execution.builder()
            .inputs(input)
            .flowId(flow.getId())
            .state(new State())
            .id(IdUtils.create())
            .flowRevision(flow.getRevision())
            .namespace(flow.getNamespace())
            .tenantId(flow.getTenantId())
            .kind(null)
            .trigger(ExecutionTrigger.of(
                this,
                (io.kestra.core.models.tasks.Output) new McpToolTrigger.Output(additionalInputs)
            ))
            .build();
        List<Label> labels = new ArrayList<>(LabelService.labelsExcludingSystem(flow.getLabels()));
        labels.add(new Label(Label.CORRELATION_ID, execution.getId()));
        labels.addAll(additionalLabels);
        return execution.withLabels(labels);
    }

    public static class Output extends HashMap<String, Object> implements io.kestra.core.models.tasks.Output  {
        public Output(Map<String, Object> map) {
            super(map);
        }
    }


    /**
     * Tool behaviour hints following the MCP annotation specification.
     * These flags inform AI agents about the side effects and safety characteristics of this tool.
     */
    @Schema(description = "Tool behaviour hints following the MCP annotation specification.")
    public record Annotations(
        @Schema(
            title = "Whether this tool is read-only.",
            description = "Set to `true` if the tool does not modify any state or produce side effects. When `true`, `destructive` and `idempotent` are not meaningful."
        )
        boolean readOnly,

        @Schema(
            title = "Whether this tool may interact with external entities beyond its closed domain.",
            description = "Set to `true` if the tool reaches external systems such as third-party APIs, databases, or file systems outside the Kestra environment."
        )
        boolean openWorld,

        @Schema(
            title = "Whether this tool may perform destructive updates.",
            description = "Only meaningful when `readOnly` is `false`. Set to `false` for tools whose effects can be safely undone or are non-destructive (e.g., creating a record). Default is `true`."
        )
        boolean destructive,

        @Schema(
            title = "Whether calling this tool repeatedly with the same arguments has the same effect as calling it once.",
            description = "Only meaningful when `readOnly` is `false`. Set to `true` for tools whose repeated invocation produces no additional side effects beyond the first call."
        )
        boolean idempotent,

        @Schema(
            title = "Whether the tool result should be returned directly to the user without further AI processing.",
            description = "When `true`, the AI agent forwards the raw tool output to the user rather than interpreting or summarising it."
        )
        boolean returnDirect
    ) {}
}
