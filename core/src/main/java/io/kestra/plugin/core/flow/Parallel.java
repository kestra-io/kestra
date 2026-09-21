package io.kestra.plugin.core.flow;

import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run child tasks in parallel.",
    description = """
        Starts all child tasks concurrently, bounded by `concurrent` if set (0 = no cap). Each branch can contain its own sequences or nested flows.

        Use when independent steps can run at the same time to shorten wall-clock duration."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = """
                Run tasks in parallel
                """,
            code = """
                id: parallel
                namespace: company.team

                tasks:
                  - id: parallel
                    type: io.kestra.plugin.core.flow.Parallel
                    tasks:
                      - id: 1st
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ task.id }} > {{ taskrun.startDate }}"

                      - id: 2nd
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ task.id }} > {{ taskrun.id }}"

                  - id: last
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ task.id }} > {{ taskrun.startDate }}"
                """
        ),
        @Example(
            full = true,
            title = """
                Run two sequences in parallel
                """,
            code = """
                id: parallel_sequences
                namespace: company.team

                tasks:
                  - id: parallel
                    type: io.kestra.plugin.core.flow.Parallel
                    tasks:
                      - id: sequence1
                        type: io.kestra.plugin.core.flow.Sequential
                        tasks:
                          - id: task1
                            type: io.kestra.plugin.core.debug.Return
                            format: "{{ task.id }}"

                          - id: task2
                            type: io.kestra.plugin.core.debug.Return
                            format: "{{ task.id }}"

                      - id: sequence2
                        type: io.kestra.plugin.core.flow.Sequential
                        tasks:
                          - id: task3
                            type: io.kestra.plugin.core.debug.Return
                            format: "{{ task.id }}"

                          - id: task4
                            type: io.kestra.plugin.core.debug.Return
                            format: "{{ task.id }}"
                """
        ),
        @Example(
            full = true,
            title = """
                Stop the other branches as soon as one task fails
                """,
            code = """
                id: parallel_fail_fast
                namespace: company.team

                tasks:
                  - id: parallel
                    type: io.kestra.plugin.core.flow.Parallel
                    onChildFailure: CANCELLED
                    tasks:
                      - id: fails_fast
                        type: io.kestra.plugin.core.execution.Fail

                      - id: long_running
                        type: io.kestra.plugin.core.flow.Sleep
                        duration: PT1M
                """
        )
    }
)
public class Parallel extends AbstractBranch<VoidOutput> implements OnChildFailureInterface {
    @NotNull
    @Builder.Default
    @Schema(
        title = "Number of concurrent parallel tasks that can be running at any point in time",
        description = "If the value is `0`, no limit exist and all tasks will start at the same time."
    )
    private final Property<@PositiveOrZero Integer> concurrent = Property.ofValue(0);

    @NotNull
    @Builder.Default
    @Schema(
        title = "What to do with the other still-running tasks when one task fails.",
        description = """
            `CONTINUE` (default): other tasks keep running to completion, as today.

            `CANCELLED` / `FAILED`: as soon as a task fails with no retry left, every other still-running task in this Parallel is interrupted and lands in the given state. The Parallel itself still resolves to `FAILED` and its `errors`/`finally` tasks still run normally."""
    )
    private final Property<OnChildFailure> onChildFailure = Property.ofValue(OnChildFailure.FAIL);

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.PARALLEL);

        GraphUtils.parallel(
            subGraph,
            this.tasks,
            this.errors,
            this._finally,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveParallelNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun,
            runContext.render(this.concurrent).as(Integer.class).orElseThrow()
        );
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        return FlowableUtils.resolveSequentialState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
    }
}
