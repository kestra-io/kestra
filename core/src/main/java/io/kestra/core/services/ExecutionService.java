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
                .forEach(r -> newTaskRuns.removeIf(taskRun -> taskRun.getId().equals(taskRunId)));
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

                if (task instanceof Pause pauseTask) {
                    State.Type terminalState = newState == State.Type.RUNNING ? State.Type.SUCCESS : newState;
                    Pause.Resumed _resumed = resumed != null ? resumed : Pause.Resumed.now(terminalState);
                    Variables variables = variablesService.of(StorageContext.forTask(originalTaskRun), pauseTask.generateOutputs(onResumeInputs, _resumed));
                    newTaskRun = originalTaskRun.withOutputs(variables);

                    // if it's a Pause task with no subtask, we terminate the task
                    if (ListUtils.isEmpty(pauseTask.getTasks()) && ListUtils.isEmpty(pauseTask.getErrors()) && ListUtils.isEmpty(pauseTask.getFinally())) {
                        if (newState == State.Type.RUNNING) {
                            newTaskRun = newTaskRun.withState(State.Type.SUCCESS);
                        } else if (newState == State.Type.KILLING) {
                            newTaskRun = newTaskRun.withState(State.Type.KILLED);
                        } else {
                            newTaskRun = newTaskRun.withState(newState);
                        }
                    } else {
                        // we should set the state to RUNNING so that subtasks are executed
                        newTaskRun = newTaskRun.withState(State.Type.RUNNING);
                    }
                } else {
                    newTaskRun =  originalTaskRun.withState(newState);
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
        @Nullable List<State.Type> state
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
            .map(throwFunction(execution -> {
                PurgeResult.PurgeResultBuilder<?, ?> builder = PurgeResult.builder();

                if (purgeExecution) {
                    builder.executionsCount(this.executionRepository.purge(execution));
                }

                if (purgeLog) {
                    builder.logsCount(this.logRepository.purge(execution));
                }

                if (purgeMetric) {
                    builder.metricsCount(this.metricRepository.purge(execution));
                }

                if (purgeStorage) {
                    URI uri = StorageContext.forExecution(execution).getExecutionStorageURI(StorageContext.KESTRA_SCHEME);
                    builder.storagesCount(storageInterface.deleteByPrefix(execution.getTenantId(), execution.getNamespace(), uri).size());
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
execution = concurrencyLimitService.failDueToConcurrencyLimit(execution);

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
        return
