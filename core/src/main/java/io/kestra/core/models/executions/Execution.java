package io.kestra.core.models.executions;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.kestra.core.debug.Breakpoint;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.Label;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.services.LabelService;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

@Builder(toBuilder = true)
@Slf4j
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Execution implements SoftDeletable<Execution>, TenantInterface, HasUID, DispatchEvent {
    // !!! WARNING !!!
    // When you add anything in this class, make sure to also update ApiExecution and ApiLightExecution in the webserver module
    // !!!!!!!!!!!!!!!

    public static final String STATE_START_DATE_FIELD = "state.startDate";
    public static final String STATE_END_DATE_FIELD = "state.endDate";

    @NotNull
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
    @Schema(implementation = Object.class)
    Map<String, Object> inputs;

    @With
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(implementation = Object.class)
    Map<String, Object> outputs;

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    List<Label> labels;

    @With
    @Schema(implementation = Object.class)
    Map<String, Object> variables;

    @NotNull
    State state;

    String parentId;

    @NotNull
    String originalId;

    @With
    ExecutionTrigger trigger;

    @NotNull
    @Builder.Default
    boolean deleted = false;

    @NotNull
    @With
    ExecutionMetadata metadata;

    @With
    @Nullable
    Instant scheduleDate;

    @NonFinal
    @Setter
    String traceParent;

    @With
    @Nullable
    List<TaskFixture> fixtures;

    @Nullable
    ExecutionKind kind;

    @Nullable
    List<Breakpoint> breakpoints;

    @Nullable
    LoopRun loopRun;

    @Override
    @JsonIgnore
    public String uid() {
        return id;
    }

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow}.
     *
     * @param flow The Flow.
     * @param labels The Flow labels.
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final FlowInterface flow, final List<Label> labels) {
        return newExecution(flow, null, labels, Optional.empty());
    }

    public List<Label> getLabels() {
        return ListUtils.emptyOnNull(this.labels);
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
    public static Execution newExecution(final FlowInterface flow,
        final BiFunction<FlowInterface, Execution, Map<String, Object>> inputs,
        final List<Label> labels,
        final Optional<ZonedDateTime> scheduleDate) {
        return newExecution(flow, inputs, labels, scheduleDate, null);
    }

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow} and
     * inputs.
     *
     * @param flow The Flow.
     * @param inputs The Flow's inputs.
     * @param labels The Flow labels.
     * @param kind The ExecutionKind.
     *
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final FlowInterface flow,
        final BiFunction<FlowInterface, Execution, Map<String, Object>> inputs,
        final List<Label> labels,
        final Optional<ZonedDateTime> scheduleDate,
        @Nullable final ExecutionKind kind) {
        Execution execution = builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .scheduleDate(scheduleDate.map(ChronoZonedDateTime::toInstant).orElse(null))
            .variables(flow.getVariables())
            .kind(kind)
            .build();

        List<Label> executionLabels = new ArrayList<>(LabelService.labelsExcludingSystem(flow.getLabels()));
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

    @Override
    public String key() {
        return id;
    }

    /**
     * Customization of Lombok-generated builder.
     */
    public static class ExecutionBuilder {

        /**
         * Enforce unique values of {@link Label} when using the builder.
         *
         * @param labels The labels.
         * @return Deduplicated labels.
         */
        public ExecutionBuilder labels(List<Label> labels) {
            this.labels = Label.deduplicate(labels);
            return this;
        }

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
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints,
            this.loopRun
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
            Label.deduplicate(labels),
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints,
            this.loopRun
        );
    }

    public Execution withTaskRun(TaskRun taskRun) throws InternalException {
        List<TaskRun> newTaskRunList = this.taskRunList == null ? new ArrayList<>() : new ArrayList<>(this.taskRunList);

        boolean b = Collections.replaceAll(
            newTaskRunList,
            this.findTaskRunByTaskRunId(taskRun.getId()),
            taskRun
        );

        if (!b) {
            throw new IllegalStateException(
                "Can't replace taskRun '" + taskRun.getId() + "' on execution'" + this.getId()
                    + "'"
            );
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
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints,
            this.loopRun
        );
    }

    public Execution withBreakpoints(List<Breakpoint> newBreakpoints) {
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
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            newBreakpoints,
            this.loopRun
        );
    };

    public Execution addLabel(Label label) {
        List<Label> existingLabel = this.labels == null ? new ArrayList<>(1) : new ArrayList<>(this.labels);
        if (existingLabel.stream().noneMatch(l -> l.key().equals(label.key()))) {
            existingLabel.add(label);
        }

        return withLabels(existingLabel);
    }

    /**
     * Creates a child execution with the given parameters.
     * Child executions derived from the original execution, to restart or replay it.
     * When restarting, set the <code>childExecutionId</code> to the original execution ID, when replaying, set it to null.
     */
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
            // preserve the parentId when restarting, this is needed for loop sub-executions
            childExecutionId != null ? this.getId() : this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints,
            this.loopRun
        );
    }

    /**
     * Creates a derived loop execution from the current execution and the loop task run
     * with the given index information (value and index).
     */
    public Execution loopExecution(TaskRun taskRun, int index, @Nullable String key, String value) {
        return new Execution(
            this.tenantId,
            IdUtils.create(),
            this.namespace,
            this.flowId,
            this.flowRevision,
            null,
            null, // we don't copy inputs to reduce the size, the RunVariables must get them from the parent execution
            this.outputs,
            this.labels,
            this.variables,
            this.state,
            this.id,
            this.originalId,
            null, // we don't copy triggers to reduce the size, the RunVariables must get them from the parent execution
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            ExecutionKind.LOOP,
            this.breakpoints,
            new LoopRun(this, taskRun.getTaskId(), taskRun.getId(), index, key, value, computeParents())
        );
    }

    /**
     * Computes loop parents from the current execution's loop run for inclusion in a new loop execution.
     */
    private List<LoopRun.Parent> computeParents() {
        if (this.loopRun == null) {
            return null;
        }

        List<LoopRun.Parent> parents = new ArrayList<>();
        if (this.loopRun.parents() != null) {
            parents.addAll(this.loopRun.parents());
        }

        parents.add(new LoopRun.Parent(this.loopRun.index(), this.loopRun.key(), this.loopRun.value()));
        return parents;
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
        Optional<TaskRun> find = (this.taskRunList == null ? Collections.<TaskRun> emptyList()
            : this.taskRunList)
            .stream()
            .filter(taskRun -> taskRun.getId().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException(
                "Can't find taskrun with taskrunId '" + id + "' on execution '" + this.id + "' "
                    + this.toStringState()
            );
        }

        return find.get();
    }

    public TaskRun findTaskRunByTaskIdAndValue(String id, List<String> values)
        throws InternalException {
        Optional<TaskRun> find = (this.taskRunList == null ? Collections.<TaskRun> emptyList()
            : this.taskRunList)
            .stream()
            .filter(
                taskRun -> taskRun.getTaskId().equals(id) && findParentsValues(
                    taskRun,
                    true
                ).equals(values)
            )
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException(
                "Can't find taskrun with taskrunId '" + id + "' & value '" + values
                    + "' on execution '" + this.id + "' " + this.toStringState()
            );
        }

        return find.get();
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks Used only from the flow
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @param resolvedFinally finally tasks
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(
        List<ResolvedTask> resolvedTasks,
        List<ResolvedTask> resolvedErrors,
        List<ResolvedTask> resolvedFinally) {
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
        TaskRun parentTaskRun) {
        return findTaskDependingFlowState(resolvedTasks, resolvedErrors, resolvedFinally, parentTaskRun, null);
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
     * @param terminalState the parent task terminal state
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(
        List<ResolvedTask> resolvedTasks,
        @Nullable List<ResolvedTask> resolvedErrors,
        @Nullable List<ResolvedTask> resolvedFinally,
        TaskRun parentTaskRun,
        @Nullable State.Type terminalState) {
        resolvedTasks = removeDisabled(resolvedTasks);
        resolvedErrors = removeDisabled(resolvedErrors);
        resolvedFinally = removeDisabled(resolvedFinally);

        List<TaskRun> finallyFlow = this.findTaskRunByTasks(resolvedFinally, parentTaskRun);
        // finally is already started, just continue it
        if (!finallyFlow.isEmpty()) {
            return resolvedFinally == null ? Collections.emptyList() : resolvedFinally;
        }

        List<TaskRun> errorsFlow = this.findTaskRunByTasks(resolvedErrors, parentTaskRun);
        // check if the parent task should fail, and there are error tasks so we start them
        if (errorsFlow.isEmpty() && terminalState == State.Type.FAILED) {
            return resolvedErrors == null ? resolvedFinally == null ? Collections.emptyList() : resolvedFinally : resolvedErrors;
        }

        // Check if flow has failed tasks
        if (!errorsFlow.isEmpty() || this.hasFailed(resolvedTasks, parentTaskRun)) {
            // Check if among the failed task, they will be retried
            if (!this.hasFailedNoRetry(resolvedTasks, parentTaskRun) && terminalState != State.Type.FAILED) {
                return Collections.emptyList();
            }

            if (resolvedFinally != null && resolvedErrors != null && !this.isTerminated(resolvedErrors, parentTaskRun)) {
                return resolvedErrors;
            } else if (resolvedFinally == null) {
                return resolvedErrors == null ? Collections.emptyList() : resolvedErrors;
            }
        }

        if (
            resolvedFinally != null && (this.isTerminated(resolvedTasks, parentTaskRun) || this.hasFailedNoRetry(
                resolvedTasks, parentTaskRun
            ))
        ) {
            return resolvedFinally;
        }

        return resolvedTasks;
    }

    /**
     * Remove disabled tasks from the list of resolved tasks.
     */
    public List<ResolvedTask> removeDisabled(List<ResolvedTask> tasks) {
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

        // to avoid nested loops, we pre-compute a per-uid resolved task map for fast retrieval
        Map<String, ResolvedTask> resolvedTaskMap = HashMap.newHashMap(resolvedTasks.size());
        resolvedTasks.forEach(resolvedTask -> resolvedTaskMap.put(resolvedTask.uid(), resolvedTask));
        return this
            .getTaskRunList()
            .stream()
            .filter(t -> resolvedTaskMap.containsKey(IdUtils.fromParts(t.getTaskId(), t.getValue()))
                    && (parentTaskRun == null || parentTaskRun.getId().equals(t.getParentTaskRunId()))
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

    /*
     * Using reversed().findFirst() is intended for better performance,
     * as these methods are used heavily.
     * Do not replace it with Streams.findLast() in these methods,
     * as Streams.findLast() performs worse.
     *
     * See: @see <a href="https://github.com/kestra-io/kestra/pull/14385">KESTRA#14385</a>
     */
    public Optional<TaskRun> findLastNotTerminated() {
        if (this.taskRunList == null) {
            return Optional.empty();
        }

        return this.taskRunList
            .reversed()
            .stream()
            .filter(t -> !t.getState().isTerminated() || !t.getState().isPaused())
            .findFirst();
    }

    public Optional<TaskRun> findLastByState(List<TaskRun> taskRuns, State.Type state) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().getCurrent() == state)
            .findFirst();
    }

    public Optional<TaskRun> findLastCreated(List<TaskRun> taskRuns) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().isCreated())
            .findFirst();
    }

    public Optional<TaskRun> findLastTerminated(List<TaskRun> taskRuns) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().isTerminated())
            .findFirst();
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

    public boolean hasFailed() {
        return this.taskRunList != null && this.taskRunList
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public boolean hasFailed(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public boolean hasFailedNoRetry(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            // NOTE: we check on isFailed first to avoid the costly shouldBeRetried() method
            .anyMatch(taskRun -> taskRun.getState().isFailed() && shouldNotBeRetried(resolvedTasks, parentTaskRun, taskRun));
    }

    private static boolean shouldNotBeRetried(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun, TaskRun taskRun) {
        ResolvedTask resolvedTask = resolvedTasks.stream()
            .filter(t -> t.getTask().getId().equals(taskRun.getTaskId())).findFirst()
            .orElse(null);
        if (resolvedTask == null) {
            log.warn(
                "Can't find task for taskRun '{}' in parentTaskRun '{}'",
                taskRun.getId(), parentTaskRun.getId()
            );
            return false;
        }
        return !taskRun.shouldBeRetried(resolvedTask.getTask().getRetry());
    }

    public State.Type guessFinalState(Flow flow) {
        return this.guessFinalState(ResolvedTask.of(flow.getTasks()), null, false, false);
    }

    public State.Type guessFinalState(List<ResolvedTask> currentTasks, TaskRun parentTaskRun,
        boolean allowFailure, boolean allowWarning) {
        return guessFinalState(currentTasks, parentTaskRun, allowFailure, allowWarning, State.Type.SUCCESS);
    }

    public State.Type guessFinalState(List<ResolvedTask> currentTasks, TaskRun parentTaskRun,
        boolean allowFailure, boolean allowWarning, State.Type terminalState) {
        List<TaskRun> taskRuns = this.findTaskRunByTasks(currentTasks, parentTaskRun);

        // Single pass over taskRuns, tracking the highest-priority terminal state found.
        // Priority order: KILLED > FAILED > WARNING > PAUSED
        State.Type state = terminalState;
        for (TaskRun taskRun : taskRuns) {
            State.Type current = taskRun.getState().getCurrent();
            if (current == State.Type.KILLED) {
                state = State.Type.KILLED;
                break; // highest priority, no need to continue
            } else if (current == State.Type.FAILED && state != State.Type.KILLED) {
                state = State.Type.FAILED;
            } else if (current == State.Type.WARNING && state != State.Type.KILLED && state != State.Type.FAILED) {
                state = State.Type.WARNING;
            } else if (current == State.Type.PAUSED && state == terminalState) {
                state = State.Type.PAUSED;
            }
        }

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
     * catch the {@code FAILED} taskRun emitted and will fail the execution. In the worst case, we
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
            .map(taskRun ->
            {
                TaskRunAttempt lastAttempt = taskRun.lastAttempt();
                if (lastAttempt == null) {
                    return newAttemptsTaskRunForFailedExecution(taskRun, e);
                } else {
                    return lastAttemptsTaskRunForFailedExecution(taskRun, lastAttempt, e);
                }
            })
            .map(t ->
            {
                try {
                    return new FailedExecutionWithLog(
                        this.withTaskRun(t.taskRun()),
                        t.logs()
                    );
                } catch (InternalException ex) {
                    return null;
                }
            })
            .orElseGet(
                () -> new FailedExecutionWithLog(
                    this.state.getCurrent() != State.Type.FAILED ? this.withState(State.Type.FAILED)
                        : this,
                    RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(this))
                )
            );
    }

    public Optional<TaskFixture> getFixtureForTaskRun(TaskRun taskRun) {
        if (this.fixtures == null) {
            return Optional.empty();
        }

        return this.fixtures.stream()
            .filter(fixture -> Objects.equals(fixture.getId(), taskRun.getTaskId()) && Objects.equals(fixture.getValue(), taskRun.getValue()))
            .findFirst();
    }

    /**
     * Create a new attempt for failed worker execution
     *
     * @param taskRun the task run where we need to add an attempt
     * @param e the exception raise
     * @return new taskRun with added attempt
     */
    private FailedTaskRunWithLog newAttemptsTaskRunForFailedExecution(TaskRun taskRun,
        Exception e) {
        return new FailedTaskRunWithLog(
            taskRun
                .withAttempts(
                    Collections.singletonList(
                        TaskRunAttempt.builder()
                            .state(new State())
                            .build()
                            .withState(State.Type.FAILED)
                    )
                )
                .withState(State.Type.FAILED),
            RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(taskRun, kind))
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
    private FailedTaskRunWithLog lastAttemptsTaskRunForFailedExecution(TaskRun taskRun, TaskRunAttempt lastAttempt, Exception e) {
        TaskRun failed = taskRun
            .withAttempts(
                Stream
                    .concat(
                        taskRun.getAttempts().stream().limit(taskRun.getAttempts().size() - 1),
                        Stream.of(lastAttempt.getState().isFailed() ? lastAttempt : lastAttempt.withState(State.Type.FAILED))
                    )
                    .toList()
            );
        return new FailedTaskRunWithLog(
            failed.getState().isFailed() ? failed : failed.withState(State.Type.FAILED),
            RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(taskRun, kind))
        );
    }

    public record FailedTaskRunWithLog(
        TaskRun taskRun,
        List<LogEntry> logs) {
    }

    public record FailedExecutionWithLog(
        Execution execution,
        List<LogEntry> logs) {
    }

    /**
     * Transform an exception to {@link ILoggingEvent}
     *
     * @param e the current exception
     * @return the {@link ILoggingEvent} waited to generate {@link LogEntry}
     */
    public static ILoggingEvent loggingEventFromException(Throwable e) {
        LoggingEvent loggingEvent = new LoggingEvent();
        loggingEvent.setLevel(ch.qos.logback.classic.Level.ERROR);
        loggingEvent.setThrowableProxy(new ThrowableProxy(e));
        loggingEvent.setMessage(e.getMessage());
        loggingEvent.setThreadName(Thread.currentThread().getName());
        loggingEvent.setTimeStamp(Instant.now().toEpochMilli());
        loggingEvent.setLoggerName(Execution.class.getName());

        return loggingEvent;
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

        List<TaskRun> result = new ArrayList<>();
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
     * Find all children of this {@link TaskRun}.
     */
    public List<TaskRun> findChildren(TaskRun parentTaskRun) {
        return taskRunList.stream()
            .filter(taskRun -> parentTaskRun.getId().equals(taskRun.getParentTaskRunId()))
            .toList();
    }

    public List<String> findParentsValues(TaskRun taskRun, boolean withCurrent) {
        return (withCurrent ? Stream.concat(findParents(taskRun).stream(), Stream.of(taskRun)) : findParents(taskRun).stream())
            .filter(t -> t.getValue() != null)
            .map(TaskRun::getValue)
            .toList();
    }

    @Override
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
            (this.taskRunList == null ? ""
                : this.taskRunList
                    .stream()
                    .map(t -> t.toString(true))
                    .collect(Collectors.joining(",\n    ")))
            +
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
            (this.taskRunList == null ? ""
                : this.taskRunList
                    .stream()
                    .map(TaskRun::toStringState)
                    .collect(Collectors.joining(",\n    ")))
            +
            "\n  ] " +
            "\n)";
    }

    public Long toCrc32State() {
        CRC32 crc32 = new CRC32();
        crc32.update(this.toStringState().getBytes());

        return crc32.getValue();
    }
}
