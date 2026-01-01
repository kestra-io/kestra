package io.kestra.core.services;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.hierarchies.AbstractGraphTask;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.multipart.CompletedPart;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.*;

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
    private FlowInputOutput flowInputOutput;

    @Inject
    private ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;

    @Inject
    private ConcurrencyLimitService concurrencyLimitService;

    @Inject
    private ConditionService conditionService;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private VariablesService variablesService;

    public Execution getExecutionIfPause(final String tenant, final @NotNull String executionId, boolean withACL) {
        Execution execution = getExecution(tenant, executionId, withACL);

        if (!execution.getState().isPaused()) {
            throw new IllegalStateException("Execution '"+ executionId + "' is not paused, can't resume it");
        }

        return execution;
    }

    public Execution getExecution(final String tenant, final @NotNull String executionId, boolean withACL) {
        Optional<Execution> maybeExecution = withACL ?
            executionRepository.findById(tenant, executionId) :
            executionRepository.findByIdWithoutAcl(tenant, executionId);

        return maybeExecution
            .orElseThrow(() -> new NoSuchElementException("Execution '"+ executionId + "' not found."));
    }

    /**
     * Retry set the given taskRun in created state
     * and return the execution in running state
     **/
    public Execution retryTask(Execution execution, Flow flow, String taskRunId) throws InternalException {
        TaskRun taskRun = execution.findTaskRunByTaskRunId(taskRunId).withState(State.Type.CREATED);
        List<TaskRun> taskRunList = execution.getTaskRunList();

        if (taskRun.getParentTaskRunId() != null) {
            // we need to find the parent to remove any errors or finally tasks already executed
            TaskRun parentTaskRun = execution.findTaskRunByTaskRunId(taskRun.getParentTaskRunId());
            Task parentTask = flow.findTaskByTaskId(parentTaskRun.getTaskId());
            if (parentTask instanceof FlowableTask<?> flowableTask) {
                if (flowableTask.getErrors() != null) {
                    List<Task> allErrors = Stream.concat(flowableTask.getErrors().stream()
                                .filter(task -> task.isFlowable() && ((FlowableTask<?>) task).getErrors() != null)
                                .flatMap(task -> ((FlowableTask<?>) task).getErrors().stream()),
                            flowableTask.getErrors().stream())
                        .toList();
                    allErrors.forEach(error -> taskRunList.removeIf(t -> t.getTaskId().equals(error.getId())));
                }

                if (flowableTask.getFinally() != null) {
                    List<Task> allFinally = Stream.concat(flowableTask.getFinally().stream()
                                .filter(task -> task.isFlowable() && ((FlowableTask<?>) task).getFinally() != null)
                                .flatMap(task -> ((FlowableTask<?>) task).getFinally().stream()),
                            flowableTask.getFinally().stream())
                        .toList();
                    allFinally.forEach(error -> taskRunList.removeIf(t -> t.getTaskId().equals(error.getId())));
                }
            }

            return execution.withTaskRunList(taskRunList).withTaskRun(taskRun).withState(State.Type.RUNNING);
        }

        return execution.withTaskRun(taskRun).withState(State.Type.RUNNING);
    }

    public Execution retryWaitFor(Execution execution, String flowableTaskRunId) {
        AtomicReference<Boolean> firstDone = new AtomicReference<>(false);
        List<TaskRun> newTaskRuns = execution
            .getTaskRunList()
            .stream()
            .map(taskRun -> {
                if (taskRun.getId().equals(flowableTaskRunId)) {
                    // Keep only CREATED/RUNNING
                    // To avoid having large history
                    return taskRun.resetAttempts().incrementIteration();
                }

                if (flowableTaskRunId.equals(taskRun.getParentTaskRunId())) {
                    // Clean children
                    return null;
                }

                return taskRun;
            })
            .filter(Objects::nonNull)
            .toList();

        return execution.withTaskRunList(newTaskRuns).withState(State.Type.RUNNING);
    }

    public Execution pauseFlowable(Execution execution, TaskRun updateFlowableTaskRun) throws InternalException {

        return execution.withTaskRun(updateFlowableTaskRun.withState(State.Type.PAUSED)).withState(State.Type.PAUSED);
    }

    public Execution restart(final Execution execution, @Nullable Integer revision) throws Exception {
        if (!execution.getState().canBeRestarted()) {
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
            .collect(Collectors.toCollection(ArrayList::new));

        // Worker task, we need to remove all child in order to be restarted
        this.removeWorkerTask(flow, execution, taskRunToRestart, mappingTaskRunId)
            .forEach(r -> newTaskRuns.removeIf(taskRun -> taskRun.getId().equals(r)));

        // We need to remove global error tasks and flowable error tasks if any
        flow
            .allErrorsWithChildren()
            .forEach(task -> newTaskRuns.removeIf(taskRun -> taskRun.getTaskId().equals(task.getId())));

        // We need to remove global finally tasks and flowable error tasks if any
        flow
            .allFinallyWithChildren()
            .forEach(task -> newTaskRuns.removeIf(taskRun -> taskRun.getTaskId().equals(task.getId())));

        // We need to remove afterExecution tasks
        ListUtils.emptyOnNull(flow.getAfterExecution())
            .forEach(task -> newTaskRuns.removeIf(taskRun -> taskRun.getTaskId().equals(task.getId())));

        // Build and launch new execution
        Execution newExecution = execution
            .childExecution(
                newExecutionId,
                newTaskRuns,
                execution.withState(State.Type.RESTARTED).getState()
            );

        List<Label> newLabels = new ArrayList<>(ListUtils.emptyOnNull(execution.getLabels()));
        if (!newLabels.contains(new Label(Label.RESTARTED, "true"))) {
            newLabels.add(new Label(Label.RESTARTED, "true"));
        }
        newExecution = newExecution.withMetadata(execution.getMetadata().nextAttempt()).withLabels(newLabels);

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
                    .toList()
            );

        if (finalTaskRunToRestart.isEmpty()) {
            throw new IllegalArgumentException("No task found to restart execution from!");
        }

        return finalTaskRunToRestart;
    }

    public Execution replay(final Execution execution, @Nullable String taskRunId, @Nullable Integer revision) throws Exception {
        final String newExecutionId = IdUtils.create();
        List<TaskRun> newTaskRuns = new ArrayList<>();
        if (taskRunId != null) {
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
                    .toList()
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

        List<Label> newLabels = new ArrayList<>(ListUtils.emptyOnNull(execution.getLabels()));
        if (!newLabels.contains(new Label(Label.REPLAY, "true"))) {
            newLabels.add(new Label(Label.REPLAY, "true"));
        }
        newExecution = newExecution.withMetadata(execution.getMetadata().nextAttempt()).withLabels(newLabels);

        return revision != null ? newExecution.withFlowRevision(revision) : newExecution;
    }

    public Execution changeTaskRunState(final Execution execution, Flow flow, String taskRunId, State.Type newState) throws Exception {
        Execution newExecution = markAs(execution, flow, taskRunId, newState);

        // if the execution was terminated, it could have executed errors/finally/afterExecutions, we must remove them as the execution will be restarted
        if (execution.getState().isTerminated()) {
            List<TaskRun> newTaskRuns =  newExecution.getTaskRunList();
            // We need to remove global error tasks and flowable error tasks if any
            flow
                .allErrorsWithChildren()
                .forEach(task -> newTaskRuns.removeIf(taskRun -> taskRun.getTaskId().equals(task.getId())));

            // We need to remove global finally tasks and flowable error tasks if any
            flow
                .allFinallyWithChildren()
                .forEach(task -> newTaskRuns.removeIf(taskRun -> taskRun.getTaskId().equals(task.getId())));

            // We need to remove afterExecution tasks
            ListUtils.emptyOnNull(flow.getAfterExecution())
                .forEach(task -> newTaskRuns.removeIf(taskRun -> taskRun.getTaskId().equals(task.getId())));

            return newExecution.withTaskRunList(newTaskRuns);
        } else {
            return newExecution;
        }
    }

    public Execution markAs(final Execution execution, FlowInterface flow, String taskRunId, State.Type newState) throws Exception {
        return this.markAs(execution, flow, taskRunId, newState, null, null);
    }

    @SuppressWarnings("deprecation")
    private Execution markAs(final Execution execution, FlowInterface flow, String taskRunId, State.Type newState, @Nullable Map<String, Object> onResumeInputs, @Nullable Pause.Resumed resumed) throws Exception {
        Set<String> taskRunToRestart = this.taskRunToRestart(
            execution,
            taskRun -> taskRun.getId().equals(taskRunId)
        );

        Execution newExecution = execution.withMetadata(execution.getMetadata().nextAttempt());

        final FlowWithSource flowWithSource = pluginDefaultService.injectVersionDefaults(flow, false);

        for (String s : taskRunToRestart) {
            TaskRun originalTaskRun = newExecution.findTaskRunByTaskRunId(s);
            Task task = flowWithSource.findTaskByTaskId(originalTaskRun.getTaskId());
            boolean isFlowable = task.isFlowable();

            if (!isFlowable || s.equals(taskRunId)) {
                TaskRun newTaskRun;

                State.Type targetState = newState;
                if (task instanceof Pause pauseTask) {
                    State.Type terminalState = newState == State.Type.RUNNING ? State.Type.SUCCESS : newState;
                    Pause.Resumed _resumed = resumed != null ? resumed : Pause.Resumed.now(terminalState);
                    Variables variables = variablesService.of(StorageContext.forTask(originalTaskRun), pauseTask.generateOutputs(onResumeInputs, _resumed));
                    newTaskRun = originalTaskRun.withOutputs(variables);

                    // if it's a Pause task with no subtask, we terminate the task
                    if (ListUtils.isEmpty(pauseTask.getTasks()) && ListUtils.isEmpty(pauseTask.getErrors()) && ListUtils.isEmpty(pauseTask.getFinally())) {
                        if (newState == State.Type.RUNNING) {
                            targetState = State.Type.SUCCESS;
                        } else if (newState == State.Type.KILLING) {
                            targetState = State.Type.KILLED;
                        }
                    } else {
                        // we should set the state to RUNNING so that subtasks are executed
                        targetState = State.Type.RUNNING;
                    }
                    newTaskRun =  newTaskRun.withState(targetState);
                } else {
                    newTaskRun = originalTaskRun.withState(targetState);
                }


                if (originalTaskRun.getAttempts() != null && !originalTaskRun.getAttempts().isEmpty()) {
                    ArrayList<TaskRunAttempt> attempts = new ArrayList<>(originalTaskRun.getAttempts());
                    attempts.set(attempts.size() - 1, attempts.getLast().withState(targetState));
                    newTaskRun = newTaskRun.withAttempts(attempts);
                }

                newExecution = newExecution.withTaskRun(newTaskRun);
            } else {
                newExecution = newExecution.withTaskRun(originalTaskRun.withState(State.Type.RUNNING));
            }
        }

        if (newExecution.getTaskRunList().stream().anyMatch(t -> t.getState().getCurrent() == State.Type.PAUSED)) {
            // there are still some tasks paused, this can occur with parallel pause
            return newExecution;
        }

        // we need to cancel immediately or the executor will process the next task if it's restarted.
        return newState == State.Type.CANCELLED ? newExecution.withState(State.Type.CANCELLED) : newExecution.withState(State.Type.RESTARTED);
    }

    public Execution markWithTaskRunAs(final Execution execution, String taskRunId, State.Type newState, Boolean markParents) throws Exception {
        TaskRun taskRun = execution.findTaskRunByTaskRunId(taskRunId);
        Execution updatedExecution = execution.withTaskRun(taskRun.withState(newState));

        if (markParents && taskRun.getParentTaskRunId() != null) {
            return this.markWithTaskRunAs(updatedExecution, taskRun.getParentTaskRunId(), newState, true);
        }

        return updatedExecution.withState(newState);
    }

    public PurgeResult purge(
        Boolean purgeExecution,
        Boolean purgeLog,
        Boolean purgeMetric,
        Boolean purgeStorage,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        int batchSize
    ) throws IOException {
        PurgeResult purgeResult = this.executionRepository
            .find(
                null,
                tenantId,
                null,
                namespace,
                flowId,
                startDate,
                endDate,
                state,
                null,
                null,
                null,
                true
            )
            .buffer(batchSize)
            .map(throwFunction(executions -> {
                PurgeResult.PurgeResultBuilder<?, ?> builder = PurgeResult.builder();

                if (purgeExecution) {
                    builder.executionsCount(this.executionRepository.purge(executions));
                }

                if (purgeLog) {
                    builder.logsCount(this.logRepository.purge(executions));
                }

                if (purgeMetric) {
                    builder.metricsCount(this.metricRepository.purge(executions));
                }

                if (purgeStorage) {
                    executions.forEach(throwConsumer(execution -> {
                        URI uri = StorageContext.forExecution(execution).getExecutionStorageURI(StorageContext.KESTRA_SCHEME);
                        builder.storagesCount(storageInterface.deleteByPrefix(execution.getTenantId(), execution.getNamespace(), uri).size());
                    }));
                }

                return (PurgeResult) builder.build();
            }))
            .reduce((a, b) -> a
                .toBuilder()
                .executionsCount(a.getExecutionsCount() + b.getExecutionsCount())
                .logsCount(a.getLogsCount() + b.getLogsCount())
                .storagesCount(a.getStoragesCount() + b.getStoragesCount())
                .metricsCount(a.getMetricsCount() + b.getMetricsCount())
                .build()
            )
            .block();

        if (purgeResult != null) {
            return purgeResult;
        }

        return PurgeResult.builder().build();
    }

    public void delete(
        Execution execution,
        boolean deleteLogs,
        boolean deleteMetrics,
        boolean deleteStorage
    ) throws IOException {
        this.executionRepository.purge(execution);

        if (deleteLogs) {
            this.logRepository.purge(execution);
        }

        if (deleteMetrics) {
            this.metricRepository.purge(execution);
        }

        if (deleteStorage) {
            URI uri = StorageContext.forExecution(execution).getExecutionStorageURI(StorageContext.KESTRA_SCHEME);
            storageInterface.deleteByPrefix(execution.getTenantId(), execution.getNamespace(), uri);
        }
    }

    /**
     * Resume a paused execution to a new state.
     * The execution must be paused or this call will be a no-op.
     *
     * @param execution the execution to resume
     * @param newState  should be RUNNING or KILLING, other states may lead to undefined behavior
     * @param flow      the flow of the execution
     * @return the execution in the new state.
     * @throws Exception if the state of the execution cannot be updated
     */
    public Execution resume(Execution execution, FlowInterface flow, State.Type newState, Pause.Resumed resumed) throws Exception {
        return this.resume(execution, flow, newState, (Map<String, Object>) null, resumed);
    }

    /**
     * Validates the inputs for an execution to be resumed.
     * <p>
     * The execution must be paused or this call will be a no-op.
     *
     * @param execution the execution to resume
     * @param flow      the flow of the execution
     * @return the execution in the new state.
     */
    public Mono<List<InputAndValue>> validateForResume(final Execution execution, FlowInterface flow) {
        return getFirstPausedTaskOr(execution, flow)
            .flatMap(task -> {
                if (task.isPresent() && task.get() instanceof Pause pauseTask) {
                    return Mono.just(flowInputOutput.resolveInputs(pauseTask.getOnResume(), flow, execution, Map.of()));
                } else {
                    return Mono.just(Collections.emptyList());
                }
            });
    }

    /**
     * Resume a paused execution to a new state.
     * <p>
     * The execution must be paused or this call will be a no-op.
     *
     * @param execution the execution to resume
     * @param flow      the flow of the execution
     * @param inputs    the onResume inputs
     * @return the execution in the new state.
     */
    public Mono<List<InputAndValue>> validateForResume(final Execution execution, Flow flow, @Nullable Publisher<CompletedPart> inputs) {
        return getFirstPausedTaskOr(execution, flow)
            .flatMap(task -> {
                if (task.isPresent() && task.get() instanceof Pause pauseTask) {
                    return flowInputOutput.validateExecutionInputs(pauseTask.getOnResume(), flow, execution, inputs);
                } else {
                    return Mono.just(Collections.emptyList());
                }
            });
    }

    /**
     * Resume a paused execution to a new state.
     * The execution must be paused or this call will be a no-op.
     *
     * @param execution the execution to resume
     * @param newState  should be RUNNING or KILLING, other states may lead to undefined behavior
     * @param flow      the flow of the execution
     * @param inputs    the onResume inputs
     * @return the execution in the new state.
     */
    public Mono<Execution> resume(final Execution execution, FlowInterface flow, State.Type newState, @Nullable Publisher<CompletedPart> inputs, @Nullable Pause.Resumed resumed) {
        return getFirstPausedTaskOr(execution, flow)
            .flatMap(task -> {
                if (task.isPresent() && task.get() instanceof Pause pauseTask) {
                    return flowInputOutput.readExecutionInputs(pauseTask.getOnResume(), flow, execution, inputs);
                } else {
                    return Mono.just(Collections.<String, Object>emptyMap());
                }
            })
            .handle((resumeInputs, sink) -> {
                try {
                    sink.next(resume(execution, flow, newState, resumeInputs, resumed));
                } catch (Exception e) {
                    sink.error(e);
                }
            });
    }

    private Mono<Optional<Task>> getFirstPausedTaskOr(Execution execution, FlowInterface flow){
        return Mono.create(sink -> {
            try {
                final FlowWithSource flowWithSource = pluginDefaultService.injectVersionDefaults(flow, false);
                var runningTaskRun = execution
                    .findFirstByState(State.Type.PAUSED)
                    .map(throwFunction(task -> flowWithSource.findTaskByTaskId(task.getTaskId())));
                sink.success(runningTaskRun);
            } catch (InternalException | FlowProcessingException e) {
                sink.error(e);
            }
        });
    }

    /**
     * Resume a paused execution to a new state.
     * The execution must be paused or this call will throw an IllegalArgumentException.
     *
     * @param execution the execution to resume
     * @param newState  should be RUNNING or KILLING, other states may lead to undefined behavior
     * @param flow      the flow of the execution
     * @param inputs    the onResume inputs
     * @return the execution in the new state.
     * @throws Exception if the state of the execution cannot be updated
     */
    public Execution resume(final Execution execution, FlowInterface flow, State.Type newState, @Nullable Map<String, Object> inputs,  @Nullable Pause.Resumed resumed) throws Exception {
        var pausedTaskRun = execution
            .findFirstByState(State.Type.PAUSED);

        Execution unpausedExecution;
        if (pausedTaskRun.isPresent()) {
            unpausedExecution = this.markAs(execution, flow, pausedTaskRun.get().getId(), newState, inputs, resumed);
        } else {
            // we are in a manual execution pause, not triggered by the Pause task, so we just switch the execution to the new state.
            if (!execution.getState().isPaused()) {
                throw new IllegalArgumentException("The execution is not paused");
            }
            unpausedExecution = execution.withState(newState);
        }

        this.eventPublisher.publishEvent(new CrudEvent<>(unpausedExecution, execution, CrudEventType.UPDATE));
        return unpausedExecution;
    }

    /**
     * Pause a running execution.
     * The execution must be running or this call will throw an IllegalArgumentException.
     *
     * @param execution the execution to resume
     * @return the execution in the new state.
     * @throws Exception if the state of the execution cannot be updated
     */
    public Execution pause(final Execution execution) throws Exception {
        if (!execution.getState().isRunning()) {
            throw new IllegalArgumentException("The execution is not running");
        }

        var pausedExecution = execution.withState(State.Type.PAUSED);

        this.eventPublisher.publishEvent(new CrudEvent<>(pausedExecution, execution, CrudEventType.UPDATE));
        return pausedExecution;
    }

    /**
     * Lookup for all executions triggered by given execution id, and returns all the relevant
     * {@link ExecutionKilled events} that should be requested. This method is not responsible for executing the events.
     *
     * @param tenantId    of the parent execution.
     * @param executionId of the parent execution.
     * @return a {@link Flux} of zero or more {@link ExecutionKilled}.
     */
    public Flux<ExecutionKilledExecution> killSubflowExecutions(final String tenantId, final String executionId) {
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
            .map(childExecution -> ExecutionKilledExecution
                .builder()
                .executionId(childExecution.getId())
                .isOnKillCascade(true)
                .state(ExecutionKilled.State.REQUESTED) // Event will be reentrant in the Executor.
                .tenantId(tenantId)
                .build()
            );
    }

    /**
     * Kill an execution.
     *
     * @return the execution in a KILLING state if not already terminated
     */
    public Execution kill(Execution execution, FlowInterface flow, Optional<State.Type> afterKillState) {
        // We afford the double kill potential (KILLING & afterKillState != null) to ensure we put the afterKillState
        if ((execution.getState().getCurrent() == State.Type.KILLING && afterKillState.isEmpty()) || execution.getState().isTerminated()) {
            return execution;
        }

        Execution newExecution;
        State.Type killingOrAfterKillState = afterKillState.orElse(State.Type.KILLING);
        if (execution.getState().isPaused()) {
            // Must be resumed and killed, no need to send killing event to the worker as the execution is not executing anything in it.
            // An edge case can exist where the execution is resumed automatically before we resume it with a killing.
            try {
                newExecution = this.resume(execution, flow, State.Type.KILLING, null);
                newExecution = newExecution.withState(killingOrAfterKillState);
            } catch (Exception e) {
                // if we cannot resume, we set it anyway to killing, so we don't throw
                log.warn("Unable to resume a paused execution before killing it", e);
                newExecution = execution.withState(killingOrAfterKillState);
            }
        } else {
            newExecution = execution.withState(killingOrAfterKillState);
        }

        // Because this method is expected to be called by the Executor we can return the Execution
        // immediately without publishing a CrudEvent like it's done on pause/resume method.
        return newExecution;
    }

    public Execution kill(Execution execution, FlowInterface flow) {
        return this.kill(execution, flow, Optional.empty());
    }

    /**
     * Climb up the hierarchy of parent taskruns and kill them all.
     */
    public Execution killParentTaskruns(TaskRun taskRun, Execution execution) throws InternalException {
        var parentTaskRun = execution.findTaskRunByTaskRunId(taskRun.getParentTaskRunId());
        Execution newExecution = execution;
        if (parentTaskRun.getState().getCurrent() != State.Type.KILLED) {
            newExecution = newExecution.withTaskRun(parentTaskRun.withStateAndAttempt(State.Type.KILLED));
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

        @Builder.Default
        private int metricsCount = 0;
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
        State alterState;
        if (Boolean.TRUE.equals(originalTaskRun.getDynamic())) {
            // dynamic task runs (task runs from dynamic worker task results) are fake task runs,
            // we cannot get their corresponding task so we blidinly translate them to the new state.
            alterState = originalTaskRun.withState(newStateType).getState();
        } else {
            Task task = flow.findTaskByTaskId(originalTaskRun.getTaskId());
            if (!task.isFlowable() || task instanceof WorkingDirectory) {
                // The current task run is the reference task run, its default state will be newState
                alterState = originalTaskRun.withState(newStateType).getState();
            } else {
                // The current task run is an ascendant of the reference task run
                alterState = originalTaskRun.withState(State.Type.RUNNING).getState();
            }
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
     *
     * @param retry     The retry define in the flow of the failed execution
     * @param execution The failed execution
     * @return The next retry date, null if maxAttempt || maxDuration is reached
     */
    public Instant nextRetryDate(AbstractRetry retry, Execution execution) {
        if (retry.getMaxAttempts() != null && execution.getMetadata().getAttemptNumber() >= retry.getMaxAttempts()) {

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

    /**
     * Force run the execution, it must be in a non-terminal state.
     * - CREATED executions will be moved to RUNNING
     * - QUEUED executions will be unqueued
     * - PAUSED executions will be resumed
     * All other non-terminated states will be no-op.
     */
    public Execution forceRun(Execution execution) throws Exception {
        if (execution.getState().isTerminated()) {
            throw new IllegalArgumentException("Only non terminated executions can be forced run.");
        }

        if (execution.getState().isCreated()) {
            return execution.withState(State.Type.RUNNING);
        }

        if (execution.getState().getCurrent() == State.Type.QUEUED) {
            return concurrencyLimitService.unqueue(execution,State.Type.RUNNING);
        }

        if (execution.getState().getCurrent() == State.Type.PAUSED) {
            Flow flow = flowRepositoryInterface.findByExecution(execution);
            return resume(execution, flow, State.Type.RUNNING, null);
        }

        // for all other states, we just return the same execution,
        // it will be resent to the queue and forced re-processed.
        return execution;
    }

    /**
     * Remove true if the execution is terminated, including listeners and afterExecution tasks.
     */
    public boolean isTerminated(Flow flow, Execution execution) {
        if (!execution.getState().isTerminated()) {
            return false;
        }

        List<ResolvedTask> validListeners = conditionService.findValidListeners(flow, execution);
        List<ResolvedTask> afterExecution = resolveAfterExecutionTasks(flow);
        return execution.isTerminated(validListeners) && execution.isTerminated(afterExecution);
    }

    /**
     * Resolve afterExecution tasks from a flow definition.
     */
    public List<ResolvedTask> resolveAfterExecutionTasks(Flow flow) {
        if (flow == null || flow.getAfterExecution() == null) {
            return Collections.emptyList();
        }

        return flow.getAfterExecution().stream()
            .map(ResolvedTask::of)
            .toList();
    }

    /**
     * Reset a trigger after an execution was terminated
     */
    public Trigger resetExecution(FlowWithSource flow, Execution execution, Trigger trigger) {
        if (!execution.getState().isTerminated()) {
            throw new IllegalArgumentException("Only terminated executions can be reset.");
        }

        FlowWithSource flowWithDefaults = pluginDefaultService.injectDefaults(flow, execution);
        RunContext runContext = runContextFactory.of(flowWithDefaults, flowWithDefaults.findTriggerByTriggerId(trigger.getTriggerId()));
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flowWithDefaults, null);

        return trigger.resetExecution(flowWithDefaults, execution, conditionContext);
    }
}
