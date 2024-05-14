package io.kestra.core.tasks.flows;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a specific task until the expected result.",
    description = """
        Use it to wait for an HTTP response or a job to end.
        Conditions is always check after the task execution.
        So you can use the child task output
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
                    task:
                      id: return
                      type: io.kestra.core.tasks.debugs.Return
                      format: "{{ outputs.waitfor.iterationCount }}"
                    condition: "{{ outputs.return.value != '4' }}"
                """
        )

    }
)
public class WaitFor extends Task implements FlowableTask<WaitFor.Output> {
    @Valid
    protected List<Task> errors;

    @Valid
    @PluginProperty
    @NotNull
    private Task task;

    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The condition to execute again the task that must be a boolean.",
        description = "Boolean coercion allows 0, -0, null and '' to evaluate to false, all other values will evaluate to true."
    )
    private String condition;

    @Schema(
        title = "Maximum count of iterations."
    )
    @Builder.Default
    private Integer maxIterations = 100;

    @Schema(
        title = "Maximum duration of the task."
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
    public AbstractGraph tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            List.of(task),
            this.errors,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                Stream.of(task),
                this.getErrors() != null ? this.getErrors().stream() : Stream.empty()
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(List.of(task), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {

        return FlowableUtils.resolveWaitForNext(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    public Instant nextExecutionDate(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (!this.reachedMaximums(runContext, execution, parentTaskRun, false)) {
            String continueLoop = runContext.render(this.condition);
            if (TruthUtils.isTruthy(continueLoop)) {

                return Instant.now().plus(this.interval);
            }
        }

        return null;
    }

    private boolean reachedMaximums(RunContext runContext, Execution execution, TaskRun parentTaskRun, Boolean printLog) {
        TaskRun childTaskRun = this.getChildTaskRun(execution, parentTaskRun).orElse(null);
        Logger logger = runContext.logger();

        if (childTaskRun == null) {
            return false;
        }

        if (this.maxIterations != null && childTaskRun.attemptNumber() >= this.maxIterations) {
            if (printLog) {logger.warn("Max iterations reached");}
            return true;
        }

        if (this.maxDuration != null &&
            childTaskRun.getAttempts().getFirst().getState().getStartDate().plus(this.maxDuration).isBefore(Instant.now())) {
            if (printLog) {logger.warn("Max duration reached");}

            return true;
        }

        return false;
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        TaskRun childTaskRun = this.getChildTaskRun(execution, parentTaskRun).orElse(null);
        if (childTaskRun != null && nextExecutionDate(runContext, execution, parentTaskRun) != null) {
            return Optional.of(State.Type.RUNNING);
        }

        if (childTaskRun != null && this.reachedMaximums(runContext, execution, parentTaskRun, true) && this.failOnMaxReached) {
            return Optional.of(State.Type.FAILED);
        }

        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun,
            runContext,
            isAllowFailure()
        );
    }

    public Optional<TaskRun> getChildTaskRun(Execution execution, TaskRun parentTaskRun) {
        if (execution.getTaskRunList() == null) {
            return Optional.empty();
        }
        return execution
            .getTaskRunList()
            .stream()
            .filter(t -> t.getParentTaskRunId() != null && t.getParentTaskRunId().equals(parentTaskRun.getId()) && t.getState().isSuccess())
            .findFirst();
    }

    @Override
    public WaitFor.Output outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        String value = parentTaskRun != null ?
            parentTaskRun.getOutputs().get("iterationCount").toString() : "0";

        return Output.builder()
            .iterationCount(Integer.parseInt(value) + 1)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private Integer iterationCount;
        private Map<String, Object> lastOutputs;
    }
}
