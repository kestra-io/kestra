package io.kestra.core.tasks.flows;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;

@SuperBuilder
@ToString
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a specific task until the expected result.",
    description = """
        Used to wait an HTTP response or a job to end.
        You can use the variable `previous` to access the previous task output.
        The variable `previous` will be null at first iteration.
        Conditions is always check after the task execution.
        Output of the child task will be accessible through the outpout of the WaitFor task.
        """
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Wait for a task to return a specific output",
            code = """
                id: waitFor
                namespace: myteam
                                
                tasks:
                  - id: waitfor
                    type: io.kestra.core.tasks.flows.WaitFor
                    maxIterations: 5
                    task:
                      id: output
                      type: io.kestra.core.tasks.outputs.OutputValues
                      values:
                        count: "{{ (previous.values.count | default('0')) | number + 1 }}"
                    condition: "{{ previous.values.count == '4'}}"
                """
        )

    }
)
public class WaitFor extends Task implements RunnableTask<Output> {
    @Valid
    @PluginProperty
    @NotNull
    private Task task;

    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The condition to execute again the task.",
        description = "The condition is a test that must return a boolean."
    )
    private String condition;

    @Schema(
        title = "Maximum count of iterations."
    )
    @Builder.Default
    private Integer maxIterations = 100;

    @Schema(
        title = "Maximum duration."
    )
    @Builder.Default
    private Duration maxDuration = Duration.ofHours(1);

    @Schema(
        title = "Interval between each iteration."
    )
    @Builder.Default
    private Duration interval = Duration.ofSeconds(1);

    @Schema(
        title = "If true, the task will fail if the maxIterations or MaxDuration is reached."
    )
    @Builder.Default
    private Boolean failOnMaxReached = false;


    @Override
    public Output run(RunContext runContext) throws Exception {
        Instant start = Instant.now();
        Integer count = 0;
        Output output = null;
        RunContext updated = runContext;
        if (this.task instanceof RunnableTask<?> runnableTask) {
            do {
                output = runnableTask.run(updated);
                updated = output != null ? runContext.addVariables("previous", output.toMap()) : runContext;

                if (updated.render(this.condition).equals("true")) {
                    return output;
                }

                count++;
                Thread.sleep(this.interval.toMillis());
            } while (Instant.now().isBefore(start.plus(this.maxDuration)) && count < this.maxIterations);
        }

        if (this.failOnMaxReached) {
            if (count >= this.maxIterations) {
                throw new Exception("Max iterations reached");
            } else {
                throw new Exception("Max duration reached");
            }
        }
        return null;
    }
}
