package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.TruthUtils;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a list of tasks repeatedly until the expected condition is met.",
    description = """
        Use this task if your workflow requires blocking calls polling for a job to finish or for some external API to return a specific HTTP response.

        You can access the outputs of the nested tasks in the `condition` property. The `condition` is evaluated after all nested task runs finish.
        """
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Run a task until it returns a specific value. Note how you don't need to take care of incrementing the iteration count. The task will loop and keep track of the iteration outputs behind the scenes — you only need to specify the exit condition for the loop.",
            code = """
                id: loop_until
                namespace: company.team

                tasks:
                  - id: loop
                    type: io.kestra.plugin.core.flow.LoopUntil
                    condition: "{{ outputs.return.value == '4' }}"
                    tasks:
                      - id: return
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ outputs.loop.iterationCount }}"
                """
        )
    },
    aliases = "io.kestra.plugin.core.flow.WaitFor"
)
public class LoopUntil extends Task implements FlowableTask<LoopUntil.Output> {
    private static final int INITIAL_LOOP_VALUE = 1;

    @Valid
    protected List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Valid
    @PluginProperty
    @NotNull
    private List<Task> tasks;

    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The condition expression that should evaluate to `true` or `false`.",
        description = "Boolean coercion allows 0, -0, null and '' to evaluate to false; all other values will evaluate to true."
    )
    private String condition;

    @Schema(
        title = "If set to `true`, the task run will end in a failed state once the `maxIterations` or `maxDuration` are reached."
    )
    @Builder.Default
    @PluginProperty
    private Boolean failOnMaxReached = false;

    @Schema(
        title = "Check the frequency configuration."
    )
    @Builder.Default
    @PluginProperty
    private CheckFrequency checkFrequency = CheckFrequency.builder().build();

    @Override
    public AbstractGraph tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            tasks,
            this.errors,
            this._finally,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                tasks.stream(),
                Stream.concat(
                    this.getErrors() != null ? this.getErrors().stream() : Stream.empty(),
                    this.getFinally() != null ? this.getFinally().stream() : Stream.empty()
                )
            )
            .toList();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(tasks, parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {

        return FlowableUtils.resolveWaitForNext(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun
        );
    }

    public Instant nextExecutionDate(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (!this.reachedMaximums(runContext, execution, parentTaskRun, false)) {
            String continueLoop = runContext.render(this.condition);
            if (!TruthUtils.isTruthy(continueLoop)) {
                return Instant.now().plus(this.checkFrequency.interval);
            }
        }

        return null;
    }

    private boolean reachedMaximums(RunContext runContext, Execution execution, TaskRun parentTaskRun, Boolean printLog) {
        Logger logger = runContext.logger();

        if (!this.childTaskRunExecuted(execution, parentTaskRun)) {
            return false;
        }

        Integer iterationCount = Optional.ofNullable(parentTaskRun.getOutputs())
            .map(outputs -> (Integer) outputs.get("iterationCount"))
            .orElse(0);
        if (this.checkFrequency.maxIterations != null && iterationCount != null && iterationCount > this.checkFrequency.maxIterations) {
            if (printLog) {logger.warn("Max iterations reached");}
            return true;
        }

        Instant creationDate = parentTaskRun.getState().getHistories().getFirst().getDate();
        if (this.checkFrequency.maxDuration != null &&
            creationDate != null && creationDate.plus(this.checkFrequency.maxDuration).isBefore(Instant.now())) {
            if (printLog) {logger.warn("Max duration reached");}

            return true;
        }

        return false;
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        boolean childTaskExecuted = this.childTaskRunExecuted(execution, parentTaskRun);
        if (childTaskExecuted && nextExecutionDate(runContext, execution, parentTaskRun) != null) {
            return Optional.empty();
        }

        if (childTaskExecuted && this.reachedMaximums(runContext, execution, parentTaskRun, true) && this.failOnMaxReached) {
            return Optional.of(State.Type.FAILED);
        }

        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            isAllowFailure(),
            isAllowWarning()
        );
    }

    public boolean childTaskRunExecuted(Execution execution, TaskRun parentTaskRun) {
        if (execution.getTaskRunList() == null) {
            return false;
        }
        return execution
            .getTaskRunList()
            .stream()
            .filter(t -> t.getParentTaskRunId() != null
                && t.getParentTaskRunId().equals(parentTaskRun.getId())
                && t.getState().isTerminatedNoFail()
            ).count() == tasks.size();

    }

    @SuppressWarnings("unchecked")
    @Override
    public LoopUntil.Output outputs(RunContext runContext) throws IllegalVariableEvaluationException {
       Map<String, Object> outputs = (Map<String, Object>) runContext.getVariables().get("outputs");
        if (outputs != null && outputs.get(this.id) != null) {
            return Output.builder().iterationCount((Integer) ((Map<String, Object>) outputs.get(this.id)).get("iterationCount")).build();
        }
        return LoopUntil.Output.builder()
            .iterationCount(INITIAL_LOOP_VALUE)
            .build();
    }

    public LoopUntil.Output outputs(TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        String value = parentTaskRun != null ?
            String.valueOf(Optional.ofNullable(parentTaskRun.getOutputs())
                .map(outputs -> outputs.get("iterationCount"))
                .orElse("0")) : "0";

        return Output.builder()
            .iterationCount(Integer.parseInt(value) + 1)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private Integer iterationCount;
    }

    @SuperBuilder(toBuilder = true)
    @Introspected
    @Getter
    @NoArgsConstructor
    public static class CheckFrequency {
        @Schema(
            title = "Maximum count of iterations."
        )
        @Builder.Default
        @PluginProperty
        private Integer maxIterations = 100;

        @Schema(
            title = "Maximum duration of the task."
        )
        @Builder.Default
        @PluginProperty
        private Duration maxDuration = Duration.ofHours(1);

        @Schema(
            title = "Interval between each iteration."
        )
        @Builder.Default
        @PluginProperty
        private Duration interval = Duration.ofSeconds(1);
    }
}
