package io.kestra.core.tasks.executions;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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
    title = "Fail the execution",
    description = "Used to fail the execution for example on a switch branch or on some conditions based on the execution context."
)
@Plugin(
    examples = {
        @Example(
            title = "Fail on a switch branch",
            full = true,
            code = {
                "id: fail-on-switch\n" +
                "namespace: io.kestra.tests\n" +
                "\n" +
                "inputs:\n" +
                "  - name: param\n" +
                "    type: STRING\n" +
                "    required: true\n" +
                "\n" +
                "tasks:\n" +
                "  - id: switch\n" +
                "    type: io.kestra.core.tasks.flows.Switch\n" +
                "    value: \"{{inputs.param}}\"\n" +
                "    cases:\n" +
                "      case1:\n" +
                "        - id: case1\n" +
                "          type: io.kestra.core.tasks.log.Log\n" +
                "          message: Case 1\n" +
                "      case2:\n" +
                "        - id: case2\n" +
                "          type: io.kestra.core.tasks.log.Log\n" +
                "          message: Case 2\n" +
                "      notexist:\n" +
                "        - id: fail\n" +
                "          type: io.kestra.core.tasks.executions.Fail\n" +
                "      default:\n" +
                "        - id: default\n" +
                "          type: io.kestra.core.tasks.log.Log\n" +
                "          message: default"
            }
        ),
        @Example(
            title = "Fail on a condition",
            full = true,
            code = {
                "id: fail-on-condition\n" +
                "namespace: io.kestra.tests\n" +
                "\n" +
                "inputs:\n" +
                "  - name: param\n" +
                "    type: STRING\n" +
                "    required: true\n" +
                "\n" +
                "tasks:\n" +
                "  - id: before\n" +
                "    type: io.kestra.core.tasks.debugs.Echo\n" +
                "    format: I'm before the fail on condition \n" +
                "  - id: fail\n" +
                "    type: io.kestra.core.tasks.executions.Fail\n" +
                "    condition: '{{inputs.param == \"fail\"}}'\n" +
                "  - id: after\n" +
                "    type: io.kestra.core.tasks.debugs.Echo\n" +
                "    format: I'm after the fail on condition "
            }
        )
    }
)
public class Fail extends Task implements RunnableTask<VoidOutput> {
    @PluginProperty(dynamic = true)
    @Schema(
        title = "Optional condition, must coerce to a boolean.",
        description = "Boolean coercion allows 0, -0, and '' to coerce to false, all other values to coerce to true."
    )
    private String condition;

    @PluginProperty(dynamic = true)
    @Schema(title = "Optional error message.")
    @Builder.Default
    private String errorMessage = "Task failure";

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        if (condition != null) {
            String rendered = runContext.render(condition);
            if (TruthUtils.isTruthy(rendered)) {
                runContext.logger().error(runContext.render(errorMessage));
                throw new Exception("Fail on a condition");
            }
            return null;
        }

        throw new Exception(runContext.render(errorMessage));
    }
}
