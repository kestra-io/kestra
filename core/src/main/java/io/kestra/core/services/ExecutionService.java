package io.kestra.core.services;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
public class ExecutionService {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    public Execution restart(final Execution execution, @Nullable Integer revision) throws Exception {
        if (!execution.getState().isTerninated()) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        return restartExecutionFromFailed(execution, State.Type.RESTARTED, revision);
    }

    public Execution replay(final Execution execution, String taskRunId, @Nullable Integer revision) throws Exception {
        if (!execution.getState().isTerninated()) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        return replayExecutionFromTaskRunId(execution, taskRunId, State.Type.RESTARTED, revision);
    }

    private Set<String> getAncestors(Execution execution, TaskRun taskRun) {
        return Stream
            .concat(
                execution
                    .findChilds(taskRun)
                    .stream(),
                Stream.of(taskRun)
            )
            .map(TaskRun::getId)
            .collect(Collectors.toSet());
    }

    private Execution replayExecutionFromTaskRunId(final Execution execution, String taskRunId, State.Type newStateType, Integer revision) throws IllegalArgumentException, InternalException {
        final Flow flow = flowRepositoryInterface.findByExecution(execution);
        GraphCluster graphCluster = GraphService.of(flow, execution);

        Set<String> taskRunToRestart = this.taskRunWithAncestors(
            execution,
            execution
                .getTaskRunList()
                .stream()
                .filter(taskRun -> taskRun.getId().equals(taskRunId))
                .collect(Collectors.toList())
        );

        if (taskRunToRestart.size() == 0) {
            throw new IllegalArgumentException("No task found to restart execution from !");
        }

        Map<String, String> mappingTaskRunId = this.mapTaskRunId(execution, false);
        final String newExecutionId = IdUtils.create();

        List<TaskRun> newTaskRuns = execution
            .getTaskRunList()
            .stream()
            .map(throwFunction(originalTaskRun -> this.mapTaskRun(
                flow,
                originalTaskRun,
                mappingTaskRunId,
                newExecutionId,
                newStateType,
                taskRunToRestart.contains(originalTaskRun.getId()))
            ))
            .collect(Collectors.toList());

        Set<String> taskRunToRemove = GraphService.successors(graphCluster, List.of(taskRunId))
            .stream()
            .filter(task -> task.getTaskRun() != null)
            .filter(task -> !task.getTaskRun().getId().equals(taskRunId))
            .filter(task -> !taskRunToRestart.contains(task.getTaskRun().getId()))
            .map(s -> mappingTaskRunId.get(s.getTaskRun().getId()))
            .collect(Collectors.toSet());

        taskRunToRemove
            .forEach(r -> newTaskRuns.removeIf(taskRun -> taskRun.getId().equals(r)));

        // Build and launch new execution
        Execution newExecution = execution.childExecution(
            newExecutionId,
            newTaskRuns,
            execution.withState(newStateType).getState()
        );

        return revision != null ? newExecution.withFlowRevision(revision) : newExecution;
    }

    private Map<String, String> mapTaskRunId(Execution execution, boolean keep) {
        return execution
            .getTaskRunList()
            .stream()
            .map(t -> new AbstractMap.SimpleEntry<>(
                t.getId(),
                keep ? t.getId() : IdUtils.create()
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private TaskRun mapTaskRun(
        Flow flow,
        TaskRun originalTaskRun,
        Map<String, String> mappingTaskRunId,
        String newExecutionId,
        State.Type newStateType,
        Boolean toRestart
    ) throws InternalException {
        boolean isFlowable = flow.findTaskByTaskId(originalTaskRun.getTaskId()).isFlowable();

        State alterState;
        if (!isFlowable) {
            // The current task run is the reference task run, its default state will be newState
            alterState = originalTaskRun.withState(newStateType).getState();
        }
        else {
            // The current task run is an ascendant of the reference task run
            alterState = originalTaskRun.withState(State.Type.RUNNING).getState();
        }

        return originalTaskRun
            .forChildExecution(
                mappingTaskRunId,
                newExecutionId,
                toRestart ? alterState : null
            );
    }

    private Set<String> taskRunWithAncestors(Execution execution, List<TaskRun> taskRuns) {
        return taskRuns
            .stream()
            .flatMap(throwFunction(taskRun -> this.getAncestors(execution, taskRun).stream()))
            .collect(Collectors.toSet());
    }

    private Execution restartExecutionFromFailed(final Execution execution, State.Type newStateType, Integer revision) throws InternalException {
        final Flow flow = flowRepositoryInterface.findByExecution(execution);

        Set<String> taskRunToRestart = this.taskRunWithAncestors(
            execution,
            execution
                .getTaskRunList()
                .stream()
                .filter(taskRun -> taskRun.getState().getCurrent().isFailed())
                .collect(Collectors.toList())
        );

        if (taskRunToRestart.size() == 0) {
            throw new IllegalArgumentException("No failed task found to restart execution from !");
        }

        Map<String, String> mappingTaskRunId = this.mapTaskRunId(execution, revision == null);
        final String newExecutionId = revision != null ? IdUtils.create() : null;

        List<TaskRun> newTaskRuns = execution
            .getTaskRunList()
            .stream()
            .map(throwFunction(originalTaskRun -> this.mapTaskRun(
                flow,
                originalTaskRun,
                mappingTaskRunId,
                newExecutionId,
                newStateType,
                taskRunToRestart.contains(originalTaskRun.getId()))
            ))
            .collect(Collectors.toList());

        // Build and launch new execution
        Execution newExecution = execution
            .childExecution(
                newExecutionId,
                newTaskRuns,
                execution.withState(newStateType).getState()
            );

        return revision != null ? newExecution.withFlowRevision(revision) : newExecution;
    }
}
