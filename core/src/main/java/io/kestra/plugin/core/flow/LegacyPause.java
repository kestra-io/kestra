package io.kestra.plugin.core.flow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.ListUtils;

import lombok.NoArgsConstructor;

/** Keeps deprecated Pause handlers and pre-upgrade child task runs executable until scoped handlers are removed. */
@Plugin(internal = true)
@NoArgsConstructor
@SuppressWarnings("removal")
public class LegacyPause extends Pause implements FlowableTask<Pause.Output> {
    private LegacyPause(Pause pause) {
        super(pause.toBuilder());
    }

    /** Selects the compatibility orchestrator only for a built-in Pause with deprecated scoped handlers. */
    public static Pause of(Pause pause) {
        return pause.getClass() != Pause.class || (ListUtils.isEmpty(pause.getErrors()) && ListUtils.isEmpty(pause.getFinally()))
            ? pause
            : new LegacyPause(pause);
    }

    @Override
    public AbstractGraph tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster graph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);
        GraphUtils.sequential(graph, this.onPauseTasks(), this.errors, this._finally, taskRun, execution);
        return graph;
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) {
        return FlowableUtils.resolveTasks(this.onPauseTasks(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        if (this.needsPause(parentTaskRun) || parentTaskRun.getState().getCurrent() == State.Type.PAUSED) {
            return List.of();
        }

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.existingOnPauseTasks(execution, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun,
            this.terminalState(runContext)
        );
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        if (this.needsPause(parentTaskRun)) {
            return Optional.of(State.Type.PAUSED);
        }

        List<ResolvedTask> tasks = this.existingOnPauseTasks(execution, parentTaskRun);
        List<ResolvedTask> errorTasks = FlowableUtils.resolveTasks(this.errors, parentTaskRun);
        List<ResolvedTask> finallyTasks = FlowableUtils.resolveTasks(this._finally, parentTaskRun);
        State.Type terminalState = this.terminalState(runContext);
        Optional<State.Type> state = FlowableUtils.resolveState(
            execution,
            tasks,
            errorTasks,
            finallyTasks,
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning(),
            terminalState
        );
        if (state.isEmpty() && !execution.hasFailed(tasks, parentTaskRun)
            && execution.findTaskDependingFlowState(tasks, errorTasks, finallyTasks, parentTaskRun, terminalState).isEmpty()) {
            return Optional.of(execution.computeFinalState(tasks, parentTaskRun, this.isAllowFailure(), this.isAllowWarning(), terminalState));
        }
        return state;
    }

    @Override
    public Optional<State.Type> resumedTaskRunState(State.Type newState) {
        return newState == State.Type.CANCELLED ? Optional.of(State.Type.CANCELLED) : Optional.empty();
    }

    private List<ResolvedTask> existingOnPauseTasks(Execution execution, TaskRun parentTaskRun) {
        List<ResolvedTask> hooks = FlowableUtils.resolveTasks(this.onPauseTasks(), parentTaskRun);
        return execution.findTaskRunByTasks(hooks, parentTaskRun).isEmpty() ? List.of() : hooks;
    }

    private boolean needsPause(TaskRun taskRun) {
        return taskRun.getState().getCurrent() == State.Type.RUNNING
            && taskRun.getState().getHistories().stream().noneMatch(history -> history.getState() == State.Type.PAUSED);
    }

    private State.Type terminalState(RunContext runContext) {
        Map<String, Object> outputs = runContext.currentOutput();
        if (outputs != null && outputs.get("resumed") instanceof Map<?, ?> resumed && resumed.get("to") != null) {
            State.Type target = State.Type.valueOf(resumed.get("to").toString());
            return super.resumedTaskRunState(target).orElse(State.Type.SUCCESS);
        }
        return State.Type.SUCCESS;
    }
}
