package io.kestra.core.models.executions;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.Label;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.services.LabelService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.MapUtils;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

@Builder(toBuilder = true)
@Slf4j
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Execution implements DeletedInterface, TenantInterface {

    @With
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId;

    @NotNull
    String id;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @NotNull
    @With
    Integer flowRevision;

    @With
    List<TaskRun> taskRunList;

    @With
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, Object> inputs;

    @With
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, Object> outputs;

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    List<Label> labels;

    @With
    Map<String, Object> variables;

    @NotNull
    State state;

    String parentId;

    String originalId;

    @With
    ExecutionTrigger trigger;

    @NotNull
    @Builder.Default
    boolean deleted = false;

    @With
    ExecutionMetadata metadata;

    @With
    @Nullable
    Instant scheduleDate;

    @NonFinal
    @Setter
    String traceParent;

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow}.
     *
     * @param flow The Flow.
     * @param labels The Flow labels.
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final Flow flow, final List<Label> labels) {
        return newExecution(flow, null, labels, Optional.empty());
    }

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow} and
     * inputs.
     *
     * @param flow The Flow.
     * @param inputs The Flow's inputs.
     * @param labels The Flow labels.
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final Flow flow,
        final BiFunction<Flow, Execution, Map<String, Object>> inputs,
        final List<Label> labels,
        final Optional<ZonedDateTime> scheduleDate) {
        Execution execution = builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .scheduleDate(scheduleDate.map(ChronoZonedDateTime::toInstant).orElse(null))
            .build();

        List<Label> executionLabels = new ArrayList<>(LabelService.labelsExcludingSystem(flow));
        if (labels != null) {
            executionLabels.addAll(labels);
        }
        if (executionLabels.stream().noneMatch(label -> Label.CORRELATION_ID.equals(label.key()))) {
            // add a correlation ID if none exist
            executionLabels.add(new Label(Label.CORRELATION_ID, execution.getId()));
        }
        execution = execution.withLabels(executionLabels);

        if (inputs != null) {
            execution = execution.withInputs(inputs.apply(flow, execution));
        }

        return execution;
    }


    public static class ExecutionBuilder {

        void prebuild() {
            this.originalId = this.id;
            this.metadata = ExecutionMetadata.builder()
                .originalCreatedDate(Instant.now())
                .build();
        }
    }

    public static ExecutionBuilder builder() {
        return new CustomExecutionBuilder();
    }

    private static class CustomExecutionBuilder extends ExecutionBuilder {

        @Override
        public Execution build() {
            this.prebuild();
            return super.build();
        }
    }

    public Execution withState(State.Type state) {
        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            this.taskRunList,
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state.withState(state),
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent
        );
    }

    public Execution withLabels(List<Label> labels) {

        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            this.taskRunList,
            this.inputs,
            this.outputs,
            labels,
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent
        );
    }

    public Execution withTaskRun(TaskRun taskRun) throws InternalException {
        ArrayList<TaskRun> newTaskRunList = new ArrayList<>(this.taskRunList);

        boolean b = Collections.replaceAll(
            newTaskRunList,
            this.findTaskRunByTaskRunId(taskRun.getId()),
            taskRun
        );

        if (!b) {
            throw new IllegalStateException(
                "Can't replace taskRun '" + taskRun.getId() + "' on execution'" + this.getId()
                    + "'");
        }

        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            newTaskRunList,
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent
        );
    }

    public Execution childExecution(String childExecutionId, List<TaskRun> taskRunList,
        State state) {
        return new Execution(
            this.tenantId,
            childExecutionId != null ? childExecutionId : this.getId(),
            this.namespace,
            this.flowId,
            this.flowRevision,
            taskRunList,
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            state,
            childExecutionId != null ? this.getId() : null,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent
        );
    }

    public List<TaskRun> findTaskRunsByTaskId(String id) {
        if (this.taskRunList == null) {
            return Collections.emptyList();
        }

        return this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getTaskId().equals(id))
            .toList();
    }

    public TaskRun findTaskRunByTaskRunId(String id) throws InternalException {
        Optional<TaskRun> find = (this.taskRunList == null ? Collections.<TaskRun>emptyList()
            : this.taskRunList)
            .stream()
            .filter(taskRun -> taskRun.getId().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException(
                "Can't find taskrun with taskrunId '" + id + "' on execution '" + this.id + "' "
                    + this.toStringState());
        }

        return find.get();
    }

    public TaskRun findTaskRunByTaskIdAndValue(String id, List<String> values)
        throws InternalException {
        Optional<TaskRun> find = (this.taskRunList == null ? Collections.<TaskRun>emptyList()
            : this.taskRunList)
            .stream()
            .filter(taskRun -> taskRun.getTaskId().equals(id) && findParentsValues(taskRun,
                true).equals(values))
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException(
                "Can't find taskrun with taskrunId '" + id + "' & value '" + values
                    + "' on execution '" + this.id + "' " + this.toStringState());
        }

        return find.get();
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks Used only from the flow
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @param resolvedErrors finally tasks
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(
        List<ResolvedTask> resolvedTasks,
        List<ResolvedTask> resolvedErrors,
        List<ResolvedTask> resolvedFinally
    ) {
        return this.findTaskDependingFlowState(resolvedTasks, resolvedErrors, resolvedFinally, null);
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks
     * <p>
     * if the current have errors, return tasks from errors if not, return the normal tasks
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @param resolvedFinally finally tasks
     * @param parentTaskRun the parent task
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(
        List<ResolvedTask> resolvedTasks,
        @Nullable List<ResolvedTask> resolvedErrors,
        @Nullable List<ResolvedTask> resolvedFinally,
        TaskRun parentTaskRun
    ) {
        resolvedTasks = removeDisabled(resolvedTasks);
        resolvedErrors = removeDisabled(resolvedErrors);
        resolvedFinally = removeDisabled(resolvedFinally);

        List<TaskRun> errorsFlow = this.findTaskRunByTasks(resolvedErrors, parentTaskRun);
        List<TaskRun> finallyFlow = this.findTaskRunByTasks(resolvedFinally, parentTaskRun);

        // finally is already started, just continue theses finally
        if (!finallyFlow.isEmpty()) {
            return resolvedFinally == null ? Collections.emptyList() : resolvedFinally;
        }

        // Check if flow has failed task
        if (!errorsFlow.isEmpty() || this.hasFailed(resolvedTasks, parentTaskRun)) {
            // Check if among the failed task, they will be retried
            if (!this.hasFailedNoRetry(resolvedTasks, parentTaskRun)) {
                return Collections.emptyList();
            }

            if (resolvedFinally != null && resolvedErrors != null && !this.isTerminated(resolvedErrors, parentTaskRun)) {
                return resolvedErrors;
            } else if (resolvedFinally == null) {
                return resolvedErrors == null ? Collections.emptyList() : resolvedErrors;
            }
        }

        if (resolvedFinally != null && (
            this.isTerminated(resolvedTasks, parentTaskRun) || this.hasFailed(resolvedTasks, parentTaskRun
        ))) {
            return resolvedFinally;
        }

        return resolvedTasks;
    }

    public List<ResolvedTask> findTaskDependingFlowState(List<ResolvedTask> resolvedTasks) {
        resolvedTasks = removeDisabled(resolvedTasks);

        return resolvedTasks;
    }

    private List<ResolvedTask> removeDisabled(List<ResolvedTask> tasks) {
        if (tasks == null) {
            return null;
        }

        return tasks
            .stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .toList();
    }

    public List<TaskRun> findTaskRunByTasks(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        if (resolvedTasks == null || this.taskRunList == null) {
            return Collections.emptyList();
        }

        return this
            .getTaskRunList()
            .stream()
            .filter(t -> resolvedTasks
                .stream()
                .anyMatch(
                    resolvedTask -> FlowableUtils.isTaskRunFor(resolvedTask, t, parentTaskRun))
            )
            .toList();
    }

    public Optional<TaskRun> findFirstByState(State.Type state) {
        if (this.taskRunList == null) {
            return Optional.empty();
        }

        return this.taskRunList
            .stream()
            .filter(t -> t.getState().getCurrent() == state)
            .findFirst();
    }

    public Optional<TaskRun> findFirstRunning() {
        if (this.taskRunList == null) {
            return Optional.empty();
        }

        return this.taskRunList
            .stream()
            .filter(t -> t.getState().isRunning())
            .findFirst();
    }

    public Optional<TaskRun> findLastNotTerminated() {
        if (this.taskRunList == null) {
            return Optional.empty();
        }

        return Streams.findLast(this.taskRunList
            .stream()
            .filter(t -> !t.getState().isTerminated() || !t.getState().isPaused())
        );
    }

    public Optional<TaskRun> findLastByState(List<TaskRun> taskRuns, State.Type state) {
        return Streams.findLast(taskRuns
            .stream()
            .filter(t -> t.getState().getCurrent() == state)
        );
    }

    public Optional<TaskRun> findLastCreated(List<TaskRun> taskRuns) {
        return Streams.findLast(taskRuns
            .stream()
            .filter(t -> t.getState().isCreated())
        );
    }

    public Optional<TaskRun> findLastRunning(List<TaskRun> taskRuns) {
        return Streams.findLast(taskRuns
            .stream()
            .filter(t -> t.getState().isRunning())
        );
    }

    public Optional<TaskRun> findLastTerminated(List<TaskRun> taskRuns) {
        return Streams.findLast(taskRuns
            .stream()
            .filter(t -> t.getState().isTerminated())
        );
    }

    public boolean isTerminated(List<ResolvedTask> resolvedTasks) {
        return this.isTerminated(resolvedTasks, null);
    }

    public boolean isTerminated(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        long terminatedCount = this
            .findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .filter(taskRun -> taskRun.getState().isTerminated())
            .count();

        return terminatedCount == resolvedTasks.size();
    }

    public boolean hasWarning() {
        return this.taskRunList != null && this.taskRunList
            .stream()
            .anyMatch(taskRun -> taskRun.getState().getCurrent() == State.Type.WARNING);
    }

    public boolean hasWarning(List<ResolvedTask> resolvedTasks) {
        return this.hasWarning(resolvedTasks, null);
    }

    public boolean hasWarning(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().getCurrent() == State.Type.WARNING);
    }

    public boolean hasFailed() {
        return this.taskRunList != null && this.taskRunList
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public boolean hasFailed(List<ResolvedTask> resolvedTasks) {
        return this.hasFailed(resolvedTasks, null);
    }

    public boolean hasFailed(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public boolean hasFailedNoRetry(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> {
                ResolvedTask resolvedTask = resolvedTasks.stream()
                    .filter(t -> t.getTask().getId().equals(taskRun.getTaskId())).findFirst()
                    .orElse(null);
                if (resolvedTask == null) {
                    log.warn("Can't find task for taskRun '{}' in parentTaskRun '{}'",
                        taskRun.getId(), parentTaskRun.getId());
                    return false;
                }
                return !taskRun.shouldBeRetried(resolvedTask.getTask().getRetry())
                    && taskRun.getState().isFailed();
            });
    }

    public boolean hasCreated() {
        return this.taskRunList != null && this.taskRunList
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isCreated());
    }

    public boolean hasCreated(List<ResolvedTask> resolvedTasks) {
        return this.hasCreated(resolvedTasks, null);
    }

    public boolean hasCreated(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isCreated());
    }

    public boolean hasRunning(List<ResolvedTask> resolvedTasks) {
        return this.hasRunning(resolvedTasks, null);
    }

    public boolean hasRunning(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isRunning());
    }

    public State.Type guessFinalState(Flow flow) {
        return this.guessFinalState(ResolvedTask.of(flow.getTasks()), null, false, false);
    }

    public State.Type guessFinalState(List<ResolvedTask> currentTasks, TaskRun parentTaskRun,
        boolean allowFailure, boolean allowWarning) {
        List<TaskRun> taskRuns = this.findTaskRunByTasks(currentTasks, parentTaskRun);
        var state = this
            .findLastByState(taskRuns, State.Type.KILLED)
            .map(taskRun -> taskRun.getState().getCurrent())
            .or(() -> this
                .findLastByState(taskRuns, State.Type.FAILED)
                .map(taskRun -> taskRun.getState().getCurrent())
            )
            .or(() -> this
                .findLastByState(taskRuns, State.Type.WARNING)
                .map(taskRun -> taskRun.getState().getCurrent())
            )
            .or(() -> this
                .findLastByState(taskRuns, State.Type.PAUSED)
                .map(taskRun -> taskRun.getState().getCurrent())
            )
            .orElse(State.Type.SUCCESS);

        if (state == State.Type.FAILED && allowFailure) {
            if (allowWarning) {
                return State.Type.SUCCESS;
            }
            return State.Type.WARNING;
        }
        if (State.Type.WARNING.equals(state) && allowWarning) {
            return State.Type.SUCCESS;
        }
        return state;
    }

    @JsonIgnore
    public boolean hasTaskRunJoinable(TaskRun taskRun) {
        if (this.taskRunList == null) {
            return true;
        }

        TaskRun current = this.taskRunList
            .stream()
            .filter(r -> r.isSame(taskRun))
            .findFirst()
            .orElse(null);

        if (current == null) {
            return true;
        }

        // attempts & retry need to be saved
        if (
            (current.getAttempts() == null && taskRun.getAttempts() != null) ||
                (current.getAttempts() != null && taskRun.getAttempts() != null
                    && current.getAttempts().size() < taskRun.getAttempts().size())
        ) {
            return true;
        }

        // same status
        if (current.getState().getCurrent() == taskRun.getState().getCurrent()) {
            return false;
        }

        // failedExecutionFromExecutor call before, so the workerTaskResult
        // don't have changed to failed but taskRunList will contain a failed
        // same for restart, the CREATED status is directly on execution taskrun
        // so we don't changed if current execution is terminated
        if (current.getState().isTerminated() && !taskRun.getState().isTerminated()) {
            return false;
        }

        // restart case mostly
        // execution contains more state than taskrun so workerTaskResult is outdated
        if (current.getState().getHistories().size() > taskRun.getState().getHistories().size()) {
            return false;
        }

        return true;
    }

    /**
     * Convert an exception on Executor and add log to the current {@code RUNNING} taskRun, on the
     * lastAttempts. If no Attempt is found, we create one (must be nominal case). The executor will
     * catch the {@code FAILED} taskRun emitted and will failed the execution. In the worst case, we
     * FAILED the execution (only from {@link io.kestra.plugin.core.trigger.Flow}).
     *
     * @param e the exception throw from Executor
     * @return a new execution with taskrun failed if possible or execution failed is other case
     */
    public FailedExecutionWithLog failedExecutionFromExecutor(Exception e) {
        if (log.isWarnEnabled()) {
            log.warn(
                "[namespace: {}] [flow: {}] [execution: {}] Flow failed from executor in {} with exception '{}'",
                this.getNamespace(),
                this.getFlowId(),
                this.getId(),
                this.getState().humanDuration(),
                e.getMessage(),
                e
            );
        }

        return this
            .findLastNotTerminated()
            .map(taskRun -> {
                TaskRunAttempt lastAttempt = taskRun.lastAttempt();
                if (lastAttempt == null) {
                    return newAttemptsTaskRunForFailedExecution(taskRun, e);
                } else {
                    return lastAttemptsTaskRunForFailedExecution(taskRun, lastAttempt, e);
                }
            })
            .map(t -> {
                try {
                    return new FailedExecutionWithLog(
                        this.withTaskRun(t.getTaskRun()),
                        t.getLogs()
                    );
                } catch (InternalException ex) {
                    return null;
                }
            })
            .orElseGet(() -> new FailedExecutionWithLog(
                    this.state.getCurrent() != State.Type.FAILED ? this.withState(State.Type.FAILED)
                        : this,
                    RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(this))
                )
            );
    }

    /**
     * Create a new attempt for failed worker execution
     *
     * @param taskRun the task run where we need to add an attempt
     * @param e the exception raise
     * @return new taskRun with added attempt
     */
    private static FailedTaskRunWithLog newAttemptsTaskRunForFailedExecution(TaskRun taskRun,
        Exception e) {
        return new FailedTaskRunWithLog(
            taskRun
                .withAttempts(
                    Collections.singletonList(TaskRunAttempt.builder()
                        .state(new State())
                        .build()
                        .withState(State.Type.FAILED))
                )
                .withState(State.Type.FAILED),
            RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(taskRun))
        );
    }

    /**
     * Add exception log to last attempts
     *
     * @param taskRun the task run where we need to add an attempt
     * @param lastAttempt the lastAttempt found to add
     * @param e the exception raise
     * @return new taskRun with updated attempt with logs
     */
    private static FailedTaskRunWithLog lastAttemptsTaskRunForFailedExecution(TaskRun taskRun,
        TaskRunAttempt lastAttempt, Exception e) {
        return new FailedTaskRunWithLog(
            taskRun
                .withAttempts(
                    Stream
                        .concat(
                            taskRun.getAttempts().stream().limit(taskRun.getAttempts().size() - 1),
                            Stream.of(lastAttempt
                                .withState(State.Type.FAILED))
                        )
                        .toList()
                )
                .withState(State.Type.FAILED),
            RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(taskRun))
        );
    }

    @Value
    public static class FailedTaskRunWithLog {

        private TaskRun taskRun;
        private List<LogEntry> logs;
    }

    @Value
    @Builder
    public static class FailedExecutionWithLog {

        private Execution execution;
        private List<LogEntry> logs;
    }

    /**
     * Transform an exception to {@link ILoggingEvent}
     *
     * @param e the current execption
     * @return the {@link ILoggingEvent} waited to generate {@link LogEntry}
     */
    public static ILoggingEvent loggingEventFromException(Exception e) {
        LoggingEvent loggingEvent = new LoggingEvent();
        loggingEvent.setLevel(ch.qos.logback.classic.Level.ERROR);
        loggingEvent.setThrowableProxy(new ThrowableProxy(e));
        loggingEvent.setMessage(e.getMessage());
        loggingEvent.setThreadName(Thread.currentThread().getName());
        loggingEvent.setTimeStamp(Instant.now().toEpochMilli());
        loggingEvent.setLoggerName(Execution.class.getName());

        return loggingEvent;
    }

    public Map<String, Object> outputs() {
        if (this.taskRunList == null) {
            return ImmutableMap.of();
        }

        // we pre-compute the map of taskrun by id to avoid traversing the list of all taskrun for each taskrun
        Map<String, TaskRun> byIds = this.taskRunList.stream().collect(Collectors.toMap(
            taskRun -> taskRun.getId(),
            taskRun -> taskRun
        ));

        Map<String, Object> result = new HashMap<>();
        for (TaskRun current : this.taskRunList) {
            if (current.getOutputs() != null) {
                result = MapUtils.merge(result, outputs(current, byIds));
            }
        }

        return result;
    }

    private Map<String, Object> outputs(TaskRun taskRun, Map<String, TaskRun> byIds) {
        List<TaskRun> parents = findParents(taskRun, byIds)
            .stream()
            .filter(r -> r.getValue() != null)
            .toList();

        if (parents.isEmpty()) {
            if (taskRun.getValue() == null) {
                return Map.of(taskRun.getTaskId(), taskRun.getOutputs());
            } else {
                return Map.of(taskRun.getTaskId(),
                    Map.of(taskRun.getValue(), taskRun.getOutputs()));
            }
        }

        Map<String, Object> result = MapUtils.newHashMap(1);
        Map<String, Object> current = result;

        for (TaskRun t : parents) {
            HashMap<String, Object> item = MapUtils.newHashMap(1);
            current.put(t.getValue(), item);
            current = item;
        }

        if (taskRun.getOutputs() != null) {
            if (taskRun.getValue() != null) {
                current.put(taskRun.getValue(), taskRun.getOutputs());
            } else {
                current.putAll(taskRun.getOutputs());
            }
        }

        return Map.of(taskRun.getTaskId(), result);
    }


    public List<Map<String, Object>> parents(TaskRun taskRun) {
        List<Map<String, Object>> result = new ArrayList<>();

        List<TaskRun> parents = findParents(taskRun);
        Collections.reverse(parents);

        for (TaskRun childTaskRun : parents) {
            HashMap<String, Object> current = new HashMap<>();

            if (childTaskRun.getValue() != null) {
                current.put("taskrun", Map.of("value", childTaskRun.getValue()));
            }

            if (childTaskRun.getOutputs() != null && !childTaskRun.getOutputs().isEmpty()) {
                current.put("outputs", childTaskRun.getOutputs());
            }

            if (!current.isEmpty()) {
                result.add(current);
            }
        }

        return result;
    }

    /**
     * Find all parents from this {@link TaskRun}. The list is starting from deeper parent and end
     * on the closest parent, so the first element is the task that starts first. This method
     * doesn't return the current tasks.
     *
     * @param taskRun current child
     * @return List of parent {@link TaskRun}
     */
    public List<TaskRun> findParents(TaskRun taskRun) {
        if (taskRun.getParentTaskRunId() == null || this.taskRunList == null) {
            return Collections.emptyList();
        }

        ArrayList<TaskRun> result = new ArrayList<>();
        boolean ended = false;
        while (!ended) {
            final TaskRun finalTaskRun = taskRun;
            Optional<TaskRun> find = this.taskRunList
                .stream()
                .filter(t -> t.getId().equals(finalTaskRun.getParentTaskRunId()))
                .findFirst();

            if (find.isPresent()) {
                result.add(find.get());
                taskRun = find.get();
            } else {
                ended = true;
            }
        }

        Collections.reverse(result);

        return result;
    }

    /**
     * Find all parents from this {@link TaskRun}. This method does the same as #findParents(TaskRun
     * taskRun) but for performance reason, as it's called a lot, we pre-compute the map of taskrun
     * by ID and use it here.
     */
    private List<TaskRun> findParents(TaskRun taskRun, Map<String, TaskRun> taskRunById) {
        if (taskRun.getParentTaskRunId() == null || taskRunById.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<TaskRun> result = new ArrayList<>();
        boolean ended = false;
        while (!ended) {
            final TaskRun finalTaskRun = taskRun;
            TaskRun find = taskRunById.get(finalTaskRun.getParentTaskRunId());

            if (find != null) {
                result.add(find);
                taskRun = find;
            } else {
                ended = true;
            }
        }

        Collections.reverse(result);

        return result;
    }

    public List<String> findParentsValues(TaskRun taskRun, boolean withCurrent) {
        return (withCurrent ?
            Stream.concat(findParents(taskRun).stream(), Stream.of(taskRun)) :
            findParents(taskRun).stream()
        )
            .filter(t -> t.getValue() != null)
            .map(TaskRun::getValue)
            .toList();
    }


    public Execution toDeleted() {
        return this.toBuilder()
            .deleted(true)
            .build();
    }

    public String toString(boolean pretty) {
        if (!pretty) {
            return super.toString();
        }

        return "Execution(" +
            "\n  id=" + this.getId() +
            "\n  flowId=" + this.getFlowId() +
            "\n  state=" + this.getState().getCurrent().toString() +
            "\n  taskRunList=" +
            "\n  [" +
            "\n    " +
            (this.taskRunList == null ? "" : this.taskRunList
                .stream()
                .map(t -> t.toString(true))
                .collect(Collectors.joining(",\n    "))
            ) +
            "\n  ], " +
            "\n  inputs=" + this.getInputs() +
            "\n)";
    }

    public String toStringState() {
        return "(" +
            "\n  state=" + this.getState().getCurrent().toString() +
            "\n  taskRunList=" +
            "\n  [" +
            "\n    " +
            (this.taskRunList == null ? "" : this.taskRunList
                .stream()
                .map(TaskRun::toStringState)
                .collect(Collectors.joining(",\n    "))
            ) +
            "\n  ] " +
            "\n)";
    }

    public Long toCrc32State() {
        CRC32 crc32 = new CRC32();
        crc32.update(this.toStringState().getBytes());

        return crc32.getValue();
    }
}
