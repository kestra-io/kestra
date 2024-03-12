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
import io.kestra.core.models.hierarchies.GraphTask;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pause the current execution and wait for a manual approval (either by humans or other automated processes). All tasks downstream from the Pause task will be put on hold until the execution is manually resumed from the UI. The Execution will be in a Paused state (_marked in purple_) and you can manually resume it by clicking on the \"Resume\" button in the UI, or by calling the POST API endpoint \"/api/v1/executions/{executionId}/resume\". The execution can also be resumed automatically after a timeout."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: human_in_the_loop
                namespace: dev

                tasks:
                  - id: before_approval
                    type: io.kestra.core.tasks.debugs.Return
                    format: Output data that needs to be validated by a human

                  - id: pause
                    type: io.kestra.core.tasks.flows.Pause
                    tasks:
                      - id: run_post_approval
                        type: io.kestra.plugin.scripts.shell.Commands
                        runner: PROCESS
                        commands:
                          - echo "Manual approval received! Continuing the execution..."

                  - id: post_resume
                    type: io.kestra.core.tasks.debugs.Return
                    format: "{{ task.id }} started on {{ taskrun.startDate }} after the Pause"
                """
        )
    }
)
public class Pause extends Sequential implements FlowableTask<VoidOutput> {
    @Schema(
        title = "Duration of the pause — useful if you want to pause the execution for a fixed amount of time.",
        description = "If no delay and no timeout are configured, the execution will never end until it's manually resumed from the UI or API."
    )
    @PluginProperty
    private Duration delay;

    @Schema(
        title = "Timeout of the pause — useful to avoid never-ending workflows in a human-in-the-loop scenario. For example, if you want to pause the execution until a human validates some data generated in a previous task, you can set a timeout of e.g. 24 hours. If no manual approval happens within 24 hours, the execution will automatically resume without a prior data validation.",
        description = "If no delay and no timeout are configured, the execution will never end until it's manually resumed from the UI or API."
    )
    @PluginProperty
    private Duration timeout;

    @Valid
    @PluginProperty
    @Deprecated
    private List<Task> tasks;

    @Override
    public AbstractGraph tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        if (this.tasks == null || this.tasks.isEmpty()) {
            return new GraphTask(this, taskRun, parentValues, RelationType.SEQUENTIAL);
        }

        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            this.tasks,
            this.errors,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun) || parentTaskRun.getState().getCurrent() == State.Type.PAUSED) {
            return new ArrayList<>();
        }

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }

    private boolean needPause(TaskRun parentTaskRun) {
        return parentTaskRun.getState().getCurrent() == State.Type.RUNNING &&
            parentTaskRun.getState().getHistories().stream().noneMatch(history -> history.getState() == State.Type.PAUSED);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun)) {
            return Optional.of(State.Type.PAUSED);
        }

        if (this.tasks == null || this.tasks.isEmpty()) {
            return Optional.of(State.Type.SUCCESS);
        }

        return super.resolveState(runContext, execution, parentTaskRun);
    }
}
