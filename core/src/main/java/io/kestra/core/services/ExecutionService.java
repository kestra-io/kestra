package io.kestra.core.services;

import io.kestra.core.exceptions.InternalException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
public class ExecutionService {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    public Execution restart(final Execution execution, @Nullable String taskId, @Nullable Integer revision) throws Exception {
        if (!execution.getState().isTerninated()) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        Execution newExecution;
        if (StringUtils.hasText(taskId)) {
            newExecution = newExecutionFromTaskRunId(execution, taskId, State.Type.RESTARTED, revision);
        } else {
            newExecution = newExecutionFromFailed(execution, State.Type.RESTARTED, revision);
        }

        return newExecution;
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

    private Execution newExecutionFromTaskRunId(final Execution execution, String referenceTaskId, State.Type newStateType, Integer revision) throws IllegalArgumentException, InternalException {
        final Flow flow = flowRepositoryInterface.findByExecution(execution);

        final Predicate<TaskRun> isNotReferenceTask = taskRun -> !(referenceTaskId.equals(taskRun.getTaskId()));
        final Predicate<TaskRun> isNotFailed = taskRun -> !taskRun.getState().getCurrent().equals(State.Type.FAILED);

        // Extract the reference task run index
        final long refTaskRunIndex = execution
            .getTaskRunList()
            .stream()
            .takeWhile(isNotFailed.and(isNotReferenceTask))
            .count();

        if (refTaskRunIndex == execution.getTaskRunList().size()) {
            throw new IllegalArgumentException("Task [" + referenceTaskId + "] does not exist !");
        }

        Map<String, String> mappingTaskRunId = this.mapTaskRunId(execution, false);
        final String newExecutionId = IdUtils.create();

        // Create new task run list
        List<TaskRun> newTaskRuns = IntStream
            .range(0, (int) refTaskRunIndex + 1)
            .boxed()
            .map(throwFunction(currentIndex -> {
                final TaskRun originalTaskRun = execution.getTaskRunList().get(currentIndex);

                return this.mapTaskRun(
                    flow,
                    originalTaskRun,
                    mappingTaskRunId,
                    newExecutionId,
                    newStateType,
                    currentIndex == refTaskRunIndex
                );
            }))
            .collect(Collectors.toList());

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

    private Set<String> taskRunToRestart(Execution execution, List<TaskRun> taskRuns) {
        return taskRuns
            .stream()
            .flatMap(throwFunction(taskRun -> this.getAncestors(execution, taskRun).stream()))
            .collect(Collectors.toSet());
    }

    private Execution newExecutionFromFailed(final Execution execution, State.Type newStateType, Integer revision) throws InternalException {
        final Flow flow = flowRepositoryInterface.findByExecution(execution);

        Set<String> taskRunToRestart = this.taskRunToRestart(
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
