package io.kestra.core.services;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.AbstractGraphTask;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.flows.Worker;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.Rethrow.throwPredicate;

@Singleton
public class ExecutionService {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    private MetricRepositoryInterface metricRepository;

    public Execution restart(final Execution execution, @Nullable Integer revision) throws Exception {
        if (!execution.getState().isTerminated()) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        final Flow flow = flowRepositoryInterface.findByExecution(execution);

        Set<String> taskRunToRestart = this.taskRunToRestart(
            execution,
            taskRun -> taskRun.getState().getCurrent().isFailed()
        );

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
                State.Type.RESTARTED,
                taskRunToRestart.contains(originalTaskRun.getId()))
            ))
            .collect(Collectors.toList());

        // Worker task, we need to remove all child in order to be restarted
        this.removeWorkerTask(flow, execution, taskRunToRestart, mappingTaskRunId)
            .forEach(r -> newTaskRuns.removeIf(taskRun -> taskRun.getId().equals(r)));

        // We need to remove global error tasks and flowable error tasks if any
        flow
            .allErrorsWithChilds()
            .forEach(task -> newTaskRuns.removeIf(taskRun -> taskRun.getTaskId().equals(task.getId())));

        // Build and launch new execution
        Execution newExecution = execution
            .childExecution(
                newExecutionId,
                newTaskRuns,
                execution.withState(State.Type.RESTARTED).getState()
            );

        return revision != null ? newExecution.withFlowRevision(revision) : newExecution;
    }

    private Set<String> taskRunToRestart(Execution execution, Predicate<TaskRun> predicate) {
        // Original tasks to be restarted
        Set<String> finalTaskRunToRestart = this
            .taskRunWithAncestors(
                execution,
                execution
                    .getTaskRunList()
                    .stream()
                    .filter(predicate)
                    .collect(Collectors.toList())
            );

        if (finalTaskRunToRestart.size() == 0) {
            throw new IllegalArgumentException("No task found to restart execution from!");
        }

        return finalTaskRunToRestart;
    }

    public Execution replay(final Execution execution, String taskRunId, @Nullable Integer revision) throws Exception {
        if (!execution.getState().isTerminated()) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        final Flow flow = flowRepositoryInterface.findByExecution(execution);
        GraphCluster graphCluster = GraphService.of(flow, execution);

        Set<String> taskRunToRestart = this.taskRunToRestart(
            execution,
            taskRun -> taskRun.getId().equals(taskRunId)
        );

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
                State.Type.RESTARTED,
                taskRunToRestart.contains(originalTaskRun.getId()))
            ))
            .collect(Collectors.toList());

        // remove all child for replay task id
        Set<String> taskRunToRemove = GraphService.successors(graphCluster, List.of(taskRunId))
            .stream()
            .filter(task -> task instanceof AbstractGraphTask)
            .map(task -> ((AbstractGraphTask) task))
            .filter(task -> task.getTaskRun() != null)
            .filter(task -> !task.getTaskRun().getId().equals(taskRunId))
            .filter(task -> !taskRunToRestart.contains(task.getTaskRun().getId()))
            .map(s -> mappingTaskRunId.get(s.getTaskRun().getId()))
            .collect(Collectors.toSet());

        taskRunToRemove
            .forEach(r -> newTaskRuns.removeIf(taskRun -> taskRun.getId().equals(r)));

        // Worker task, we need to remove all child in order to be restarted
        this.removeWorkerTask(flow, execution, taskRunToRestart, mappingTaskRunId)
            .forEach(r -> newTaskRuns.removeIf(taskRun -> taskRun.getId().equals(r)));

        // Build and launch new execution
        Execution newExecution = execution.childExecution(
            newExecutionId,
            newTaskRuns,
            execution.withState(State.Type.RESTARTED).getState()
        );

        return revision != null ? newExecution.withFlowRevision(revision) : newExecution;
    }

    public Execution markAs(final Execution execution, String taskRunId, State.Type newState) throws Exception {
        if (!execution.getState().isTerminated()) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        final Flow flow = flowRepositoryInterface.findByExecution(execution);

        Set<String> taskRunToRestart = this.taskRunToRestart(
            execution,
            taskRun -> taskRun.getId().equals(taskRunId)
        );

        Execution newExecution = execution;

        for (String s : taskRunToRestart) {
            TaskRun originalTaskRun = newExecution.findTaskRunByTaskRunId(s);
            boolean isFlowable = flow.findTaskByTaskId(originalTaskRun.getTaskId()).isFlowable();

            if (!isFlowable || s.equals(taskRunId)) {
                TaskRun newTaskRun = originalTaskRun.withState(newState);

                if (originalTaskRun.getAttempts() != null && originalTaskRun.getAttempts().size() > 0) {
                    ArrayList<TaskRunAttempt> attempts = new ArrayList<>(originalTaskRun.getAttempts());
                    attempts.set(attempts.size() - 1, attempts.get(attempts.size() - 1).withState(newState));
                    newTaskRun = newTaskRun.withAttempts(attempts);
                }

                newExecution = newExecution.withTaskRun(newTaskRun);
            } else {
                newExecution = newExecution.withTaskRun(originalTaskRun.withState(State.Type.RUNNING));
            }
        }

        return newExecution
            .withState(State.Type.RESTARTED);
    }

    public PurgeResult purge(
        Boolean purgeExecution,
        Boolean purgeLog,
        Boolean purgeMetric,
        Boolean purgeStorage,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state
    ) throws IOException {
        PurgeResult purgeResult = this.executionRepository
            .find(
                null,
                namespace,
                flowId,
                null,
                endDate,
                state
            )
            .map(execution -> {
                PurgeResult.PurgeResultBuilder<?, ?> builder = PurgeResult.builder();

                if (purgeExecution) {
                    builder.executionsCount(this.executionRepository.purge(execution));
                }

                if (purgeLog) {
                    builder.logsCount(this.logRepository.purge(execution));
                }

                if(purgeMetric) {
                    this.metricRepository.purge(execution);
                }

                if (purgeStorage) {
                    builder.storagesCount(storageInterface.deleteByPrefix(URI.create("/" + storageInterface.executionPrefix(
                        execution))).size());
                }

                return (PurgeResult) builder.build();
            })
            .reduce((a, b) -> a
                .toBuilder()
                .executionsCount(a.getExecutionsCount() + b.getExecutionsCount())
                .logsCount(a.getLogsCount() + b.getLogsCount())
                .storagesCount(a.getStoragesCount() + b.getStoragesCount())
                .build()
            )
            .blockingGet();

        if (purgeResult != null) {
            return purgeResult;
        }

        return PurgeResult.builder().build();
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    public static class PurgeResult {
        @Builder.Default
        private int executionsCount = 0;

        @Builder.Default
        private int logsCount = 0;

        @Builder.Default
        private int storagesCount = 0;
    }

    private Set<String> removeWorkerTask(Flow flow, Execution execution, Set<String> taskRunToRestart, Map<String, String> mappingTaskRunId) throws InternalException {
        Set<String> workerTaskRunId = taskRunToRestart
            .stream()
            .filter(throwPredicate(s -> {
                TaskRun taskRun = execution.findTaskRunByTaskRunId(s);
                Task task = flow.findTaskByTaskId(taskRun.getTaskId());
                return (task instanceof Worker);
            }))
            .collect(Collectors.toSet());

        GraphCluster graphCluster = GraphService.of(flow, execution);

        return GraphService.successors(graphCluster, new ArrayList<>(workerTaskRunId))
            .stream()
            .filter(task -> task instanceof AbstractGraphTask)
            .map(task -> (AbstractGraphTask) task)
            .filter(task -> task.getTaskRun() != null)
            .filter(s -> !workerTaskRunId.contains(s.getTaskRun().getId()))
            .map(s -> mappingTaskRunId.get(s.getTaskRun().getId()))
            .collect(Collectors.toSet());
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
        Task task = flow.findTaskByTaskId(originalTaskRun.getTaskId());

        State alterState;
        if (!task.isFlowable() || task instanceof Worker) {
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
}
