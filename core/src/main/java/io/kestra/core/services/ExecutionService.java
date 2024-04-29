package io.kestra.core.services;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.AbstractGraphTask;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.flows.Pause;
import io.kestra.core.tasks.flows.WorkingDirectory;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.multipart.StreamingFileUpload;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.Rethrow.throwPredicate;

@Singleton
@Slf4j
public class ExecutionService {

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

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    private FlowInputOutput flowInputOutput;

    @Inject
    private ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;

    /**
    * Retry set the given taskRun in created state
    * and return the execution in running state
    **/
     public Execution retryTask(Execution execution, String taskRunId) {
        List<TaskRun> newTaskRuns = execution
            .getTaskRunList()
            .stream()
            .map(taskRun -> {
                if (taskRun.getId().equals(taskRunId)) {
                    return taskRun
                        .withState(State.Type.CREATED);
                }

                return taskRun;
            })
            .toList();

        return execution.withTaskRunList(newTaskRuns).withState(State.Type.RUNNING);
    }

    public Execution restart(final Execution execution, @Nullable Integer revision) throws Exception {
        if (!(execution.getState().isTerminated() || execution.getState().isPaused())) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        final Flow flow = flowRepositoryInterface.findByExecutionWithoutAcl(execution);

        Set<String> taskRunToRestart = this.taskRunToRestart(
            execution,
            taskRun -> taskRun.getState().getCurrent().isFailed() || taskRun.getState().getCurrent().isPaused()
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

        newExecution = newExecution.withMetadata(execution.getMetadata().nextAttempt());

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

    public Execution replay(final Execution execution, @Nullable String taskRunId, @Nullable Integer revision) throws Exception {
        final String newExecutionId = IdUtils.create();
        List<TaskRun> newTaskRuns = new ArrayList<>();
        if(taskRunId != null){
            final Flow flow = flowRepositoryInterface.findByExecutionWithoutAcl(execution);

            GraphCluster graphCluster = GraphUtils.of(flow, execution);

            Set<String> taskRunToRestart = this.taskRunToRestart(
                execution,
                taskRun -> taskRun.getId().equals(taskRunId)
            );

            Map<String, String> mappingTaskRunId = this.mapTaskRunId(execution, false);

            newTaskRuns.addAll(
                execution.getTaskRunList()
                .stream()
                .map(throwFunction(originalTaskRun -> this.mapTaskRun(
                    flow,
                    originalTaskRun,
                    mappingTaskRunId,
                    newExecutionId,
                    State.Type.RESTARTED,
                    taskRunToRestart.contains(originalTaskRun.getId()))
                ))
                .collect(Collectors.toList())
            );

            // remove all child for replay task id
            Set<String> taskRunToRemove = GraphUtils.successors(graphCluster, List.of(taskRunId))
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
        }

        // Build and launch new execution
        Execution newExecution = execution.childExecution(
            newExecutionId,
            newTaskRuns,
            taskRunId == null ? new State() : execution.withState(State.Type.RESTARTED).getState()
        );

        newExecution = newExecution.withMetadata(execution.getMetadata().nextAttempt());

        return revision != null ? newExecution.withFlowRevision(revision) : newExecution;
    }

    public Execution markAs(final Execution execution, Flow flow, String taskRunId, State.Type newState) throws Exception {
         return this.markAs(execution, flow, taskRunId, newState, null, null);
    }

    private Execution markAs(final Execution execution, Flow flow, String taskRunId, State.Type newState, @Nullable Map<String, Object> onResumeInputs, @Nullable Publisher<StreamingFileUpload> onResumeFiles) throws Exception {
        Set<String> taskRunToRestart = this.taskRunToRestart(
            execution,
            taskRun -> taskRun.getId().equals(taskRunId)
        );

        Execution newExecution = execution;

        for (String s : taskRunToRestart) {
            TaskRun originalTaskRun = newExecution.findTaskRunByTaskRunId(s);
            Task task = flow.findTaskByTaskId(originalTaskRun.getTaskId());
            boolean isFlowable = task.isFlowable();

            if (!isFlowable || s.equals(taskRunId)) {
                TaskRun newTaskRun = originalTaskRun.withState(newState);

                if (task instanceof Pause pauseTask && pauseTask.getOnResume() != null) {
                    Map<String, Object> pauseOutputs = flowInputOutput.typedInputs(
                        pauseTask.getOnResume(),
                        execution,
                        onResumeInputs,
                        onResumeFiles
                    );

                    newTaskRun = newTaskRun.withOutputs(pauseTask.generateOutputs(pauseOutputs));
                }

                if (task instanceof Pause pauseTask && pauseTask.getTasks() == null && newState == State.Type.RUNNING) {
                    newTaskRun = newTaskRun.withState(State.Type.SUCCESS);
                }

                if (originalTaskRun.getAttempts() != null && !originalTaskRun.getAttempts().isEmpty()) {
                    ArrayList<TaskRunAttempt> attempts = new ArrayList<>(originalTaskRun.getAttempts());
                    attempts.set(attempts.size() - 1, attempts.get(attempts.size() - 1).withState(newState));
                    newTaskRun = newTaskRun.withAttempts(attempts);
                }

                newExecution = newExecution.withTaskRun(newTaskRun);
            } else {
                newExecution = newExecution.withTaskRun(originalTaskRun.withState(State.Type.RUNNING));
            }
        }

        if (newExecution.getTaskRunList().stream().anyMatch(t -> t.getState().getCurrent() == State.Type.PAUSED)) {
            // there is still some tasks paused, this can occur with parallel pause
            return newExecution;
        }
        return newExecution
            .withState(State.Type.RESTARTED);
    }

    public PurgeResult purge(
        Boolean purgeExecution,
        Boolean purgeLog,
        Boolean purgeMetric,
        Boolean purgeStorage,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state
    ) throws IOException {
        PurgeResult purgeResult = this.executionRepository
            .find(
                null,
                tenantId,
                namespace,
                flowId,
                null,
                endDate,
                state,
                null,
                null,
                null
            )
            .map(throwFunction(execution -> {
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
                    URI uri = StorageContext.forExecution(execution).getExecutionStorageURI(StorageContext.KESTRA_SCHEME);
                    builder.storagesCount(storageInterface.deleteByPrefix(execution.getTenantId(), uri).size());
                }

                return (PurgeResult) builder.build();
            }))
            .reduce((a, b) -> a
                .toBuilder()
                .executionsCount(a.getExecutionsCount() + b.getExecutionsCount())
                .logsCount(a.getLogsCount() + b.getLogsCount())
                .storagesCount(a.getStoragesCount() + b.getStoragesCount())
                .build()
            )
            .block();

        if (purgeResult != null) {
            return purgeResult;
        }

        return PurgeResult.builder().build();
    }

    /**
     * Resume a paused execution to a new state.
     * The execution must be paused or this call will be a no-op.
     *
     * @param execution the execution to resume
     * @param newState  should be RUNNING or KILLING, other states may lead to undefined behaviour
     * @param flow      the flow of the execution
     * @return the execution in the new state.
     * @throws Exception if the state of the execution cannot be updated
     */
    public Execution resume(Execution execution, Flow flow, State.Type newState) throws Exception {
        return this.resume(execution, flow, newState, null, null);
    }

    /**
     * Resume a paused execution to a new state.
     * The execution must be paused or this call will be a no-op.
     *
     * @param execution the execution to resume
     * @param newState  should be RUNNING or KILLING, other states may lead to undefined behaviour
     * @param flow      the flow of the execution
     * @param inputs    the onResume inputs
     * @param files     the onResume files
     * @return the execution in the new state.
     * @throws Exception if the state of the execution cannot be updated
     */
    public Execution resume(final Execution execution, Flow flow, State.Type newState, @Nullable Map<String, Object> inputs, @Nullable Publisher<StreamingFileUpload> files) throws Exception {
        var runningTaskRun = execution
            .findFirstByState(State.Type.PAUSED)
            .orElseThrow(() -> new IllegalArgumentException("No paused task found on execution " + execution.getId()));

        var unpausedExecution = this.markAs(execution, flow, runningTaskRun.getId(), newState, inputs, files);

        this.eventPublisher.publishEvent(new CrudEvent<>(execution, CrudEventType.UPDATE));
        return unpausedExecution;
    }

    /**
     * Lookup for all executions triggered by given execution id, and returns all the relevant
     * {@link ExecutionKilled events} that should be requested. This method is not responsible for executing the events.
     *
     * @param tenantId      of the parent execution.
     * @param executionId   of the parent execution.
     * @return  a {@link Flux} of zero or more {@link ExecutionKilled}.
     */
    public Flux<ExecutionKilled> killSubflowExecutions(final String tenantId, final String executionId) {
        // Lookup for all executions triggered by the current execution being killed.
        Flux<Execution> executions = executionRepository.findAllByTriggerExecutionId(
            tenantId,
            executionId
        );

        // For each child execution not already KILLED, send
        // subsequent kill events (that will be re-handled by the Executor).

        return executions
            .filter(childExecution -> {
                State state = childExecution.getState();
                return state.getCurrent() != State.Type.KILLING && state.getCurrent() != State.Type.KILLED;
            })
            .map(childExecution -> ExecutionKilled
                .builder()
                .executionId(childExecution.getId())
                .isOnKillCascade(true)
                .state(ExecutionKilled.State.REQUESTED) // Event will be reentrant in the Executor.
                .build()
            );
    }

    /**
     * Kill an execution.
     *
     * @return the execution in a KILLING state if not already terminated
     */
    public Execution kill(Execution execution, Flow flow) {
        if (execution.getState().isPaused()) {
            // Must be resumed and killed, no need to send killing event to the worker as the execution is not executing anything in it.
            // An edge case can exist where the execution is resumed automatically before we resume it with a killing.
            try {
                return this.resume(execution, flow, State.Type.KILLING);
            } catch (Exception e) {
                // if we cannot resume, we set it anyway to killing, so we don't throw
                log.warn("Unable to resume a paused execution before killing it", e);
            }
        }

        if (execution.getState().getCurrent() != State.Type.KILLING && !execution.getState().isTerminated()) {
            return execution.withState(State.Type.KILLING);
        }

        return execution;
    }

    /**
     * Climb up the hierarchy of parent taskruns and kill them all.
     */
    public Execution killParentTaskruns(TaskRun taskRun, Execution execution) throws InternalException {
        var parentTaskRun = execution.findTaskRunByTaskRunId(taskRun.getParentTaskRunId());
        Execution newExecution = execution;
        if (parentTaskRun.getState().getCurrent() != State.Type.KILLED) {
            newExecution = newExecution.withTaskRun(parentTaskRun.withState(State.Type.KILLED));
        }
        if (parentTaskRun.getParentTaskRunId() != null) {
            return killParentTaskruns(parentTaskRun, newExecution);
        }
        return newExecution;
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
                return (task instanceof WorkingDirectory);
            }))
            .collect(Collectors.toSet());

        GraphCluster graphCluster = GraphUtils.of(flow, execution);

        return GraphUtils.successors(graphCluster, new ArrayList<>(workerTaskRunId))
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
                    .findParents(taskRun)
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
        if (!task.isFlowable() || task instanceof WorkingDirectory) {
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

    /**
     * This method is used to retrieve previous existing execution
     * @param retry The retry define in the flow of the failed execution
     * @param execution The failed execution
     * @return The next retry date, null if maxAttempt || maxDuration is reached
     */
    public Instant nextRetryDate(AbstractRetry retry, Execution execution) {
        if (retry.getMaxAttempt() != null && execution.getMetadata().getAttemptNumber() >= retry.getMaxAttempt()) {

            return null;
        }

        Instant base = execution.getState().maxDate();
        Instant originalCreatedDate = execution.getMetadata().getOriginalCreatedDate();
        Instant nextDate = retry.nextRetryDate(execution.getMetadata().getAttemptNumber(), base);

        if (retry.getMaxDuration() != null && nextDate.isAfter(originalCreatedDate.plus(retry.getMaxDuration()))) {

            return null;
        }

        return nextDate;
    }
}
