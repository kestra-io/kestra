package io.kestra.plugin.core.execution;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TruthUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Intentionally fail the execution.",
    description = "Used to fail the execution, for example, on a switch branch or on some conditions based on the execution context."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Fail on a switch branch",
            code = """
            id: fail_on_switch
            namespace: company.team

            inputs:
              - id: param
                type: STRING
                required: true

            tasks:
              - id: switch
                type: io.kestra.plugin.core.flow.Switch
                value: "{{inputs.param}}"
                cases:
                  case1:
                    - id: case1
                      type: io.kestra.plugin.core.log.Log
                      message: Case 1
                  case2:
                    - id: case2
                      type: io.kestra.plugin.core.log.Log
                      message: Case 2
                  notexist:
                    - id: fail
                      type: io.kestra.plugin.core.execution.Fail
                  default:
                    - id: default
                      type: io.kestra.plugin.core.log.Log
                      message: default
            """
        ),
        @Example(
            full = true,
            title = "Fail on a condition",
            code = """
            id: fail_on_condition
            namespace: company.team

            inputs:
              - name: param
                type: STRING
                required: true

            tasks:
              - id: before
                type: io.kestra.plugin.core.debug.Echo
                format: I'm before the fail on condition

              - id: fail
                type: io.kestra.plugin.core.execution.Fail
                condition: '{{ inputs.param == "fail" }}'

              - id: after
                type: io.kestra.plugin.core.debug.Echo
                format: I'm after the fail on condition
            """
        ),
        @Example(
            full = true,
            title = "Using errorLogs function to send error message to Slack",
            code = """
            id: error_logs
            namespace: company.team

            tasks:
            - id: fail
                type: io.kestra.plugin.core.execution.Fail
                errorMessage: Something went wrong, make sure to fix it asap!

            errors:
            - id: slack
                type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                url: "{{ secret('SLACK_WEBHOOK') }}"
                payload: |
                {
                  "text": "Failure alert for flow `{{ flow.namespace }}.{{ flow.id }}` with ID `{{ execution.id }}`. Here is a bit more context about why the execution failed: `{{ errorLogs()[0]['message'] }}`"
                }
            """
        )
    },
    aliases = "io.kestra.core.tasks.executions.Fail"
)
public class Fail extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Optional condition, must coerce to a boolean.",
        description = "Boolean coercion allows 0, -0, and '' to coerce to false, all other values to coerce to true."
    )
    private Property<String> condition;

    @Schema(title = "Optional error message")
    @Builder.Default
    private Property<String> errorMessage = Property.ofValue("Task failure");

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        if (condition != null) {
            String rendered = runContext.render(condition).as(String.class).orElse(null);
            if (TruthUtils.isTruthy(rendered)) {
                runContext.logger().error(runContext.render(errorMessage).as(String.class).orElse(null));
                throw new Exception("Fail on a condition");
            }
            return null;
        }

        throw new Exception(runContext.render(errorMessage).as(String.class).orElse(null));
    }
}
