package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.UriProvider;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pause the flow until a reviewer approves or denies it.",
    description = """
        Runs `onWait`, then pauses the execution until a reviewer decides `onApprove` or `onDeny` from the UI, the `/executions/{id}/actions/review` API, or an EE app.

        `onDeny` always runs on a denial; `denyBehavior` (`CANCEL` by default) then decides what happens to the execution."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: approval
                namespace: company.team

                tasks:
                  - id: approval
                    type: io.kestra.plugin.core.flow.Approval
                    onApprove:
                      - id: approved
                        type: io.kestra.plugin.core.log.Log
                        message: Approved
                    onDeny:
                      - id: denied
                        type: io.kestra.plugin.core.log.Log
                        message: Denied
                """
        )
    }
)
public class Approval extends Task implements FlowableTask<Approval.Output>, PausableTask {
    @Schema(
        title = "Fields on the review dialog, in the same shape as flow inputs."
    )
    @PluginProperty
    private List<@Valid Input<?>> inputs;

    @Valid
    @Schema(
        title = "Names of the two decisions in the dialog and in the app.",
        description = "for instance Continue and Stop for a question asked by an agent."
    )
    @Builder.Default
    private Decisions decisions = Decisions.builder()
        .approve(Property.ofValue("Approve"))
        .deny(Property.ofValue("Deny"))
        .build();

    @Schema(
        title = "Whether the reviewer has to comment."
    )
    @NotNull
    @Builder.Default
    private Property<CommentRequired> commentRequired = Property.ofValue(CommentRequired.NEVER);

    @Schema(
        title = "Skip the review and approve automatically when true.",
        description = "No pause, no `onWait`, no case and no notification; `onApprove` runs straight away with `auto: true` in the outputs."
    )
    @NotNull
    @Builder.Default
    private Property<Boolean> autoApprove = Property.ofValue(false);

    @Schema(
        title = "What happens to the execution after `onApprove` finishes."
    )
    @NotNull
    @Builder.Default
    private Property<Behavior> approveBehavior = Property.ofValue(Behavior.CONTINUE);

    @Schema(
        title = "What happens to the execution after `onDeny` finishes."
    )
    @NotNull
    @Builder.Default
    private Property<Behavior> denyBehavior = Property.ofValue(Behavior.CANCEL);

    @Schema(
        title = "What happens to the execution when the request expires without a decision."
    )
    @NotNull
    @Builder.Default
    private Property<Behavior> expireBehavior = Property.ofValue(Behavior.CANCEL);

    @Schema(
        title = "Tasks executed before the execution pauses."
    )
    @PluginProperty
    private List<@Valid Task> onWait;

    @Schema(
        title = "Tasks executed after approval."
    )
    @PluginProperty
    private List<@Valid Task> onApprove;

    @Schema(
        title = "Tasks executed after denial."
    )
    @PluginProperty
    private List<@Valid Task> onDeny;

    protected List<@Valid Task> errors;

    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<@Valid Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Override
    public List<Task> getErrors() {
        return errors;
    }

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.CHOICE);

        GraphUtils.switchCase(
            subGraph,
            Map.of(
                "wait", ListUtils.emptyOnNull(this.onWait),
                "approve", ListUtils.emptyOnNull(this.onApprove),
                "deny", ListUtils.emptyOnNull(this.onDeny)
            ),
            this.errors,
            this._finally,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return ListUtils.concat(this.onWait, this.onApprove, this.onDeny, this.errors, this._finally);
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        // decision is checked before isWaiting so autoApprove (a decision recorded at creation, on a task
        // run that never pauses) goes straight to its branch instead of running onWait.
        Decision.Type decision = this.decisionType(runContext);
        if (decision != null) {
            if (decision == Decision.Type.EXPIRED) {
                return Collections.emptyList();
            }
            return FlowableUtils.resolveTasks(decision == Decision.Type.APPROVED ? this.onApprove : this.onDeny, parentTaskRun);
        }

        if (this.isWaiting(parentTaskRun)) {
            return FlowableUtils.resolveTasks(this.onWait, parentTaskRun);
        }

        return Collections.emptyList();
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.decisionType(runContext) == null && this.isWaiting(parentTaskRun)) {
            // 'finally' is only scheduled here when onWait just terminated in error, since the Branch
            // phase (which schedules it otherwise) is never reached in that case.
            Optional<State.Type> waitState = this.resolveWaitState(runContext, execution, parentTaskRun);
            boolean onWaitFailed = waitState.isPresent() && waitState.get().isTerminatedInError();

            return FlowableUtils.resolveSequentialNexts(
                execution,
                this.childTasks(runContext, parentTaskRun),
                FlowableUtils.resolveTasks(this.errors, parentTaskRun),
                onWaitFailed ? FlowableUtils.resolveTasks(this._finally, parentTaskRun) : null,
                parentTaskRun
            );
        }

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun
        );
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        Decision.Type decision = this.decisionType(runContext);
        if (decision == null && this.isWaiting(parentTaskRun)) {
            return this.resolveWaitState(runContext, execution, parentTaskRun);
        }

        Optional<State.Type> branchState = this.resolveBranchState(runContext, execution, parentTaskRun);

        if (branchState.isEmpty() || decision == null || branchState.get().isTerminatedInError()) {
            return branchState;
        }

        Behavior behavior = runContext.render(this.behaviorFor(decision).skipCache()).as(Behavior.class).orElse(Behavior.CONTINUE);
        return Optional.of(this.applyBehavior(behavior, branchState.get()));
    }

    /** The Branch phase's own resolved state, before a configured behavior is mapped over it. */
    private Optional<State.Type> resolveBranchState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = ListUtils.emptyOnNull(this.childTasks(runContext, parentTaskRun)).stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .toList();

        List<ResolvedTask> finallyTasks = FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun);

        // guessFinalState(null, ...) resolves immediately regardless of any real scheduled child, so it
        // is only safe when there is truly nothing left to wait for — an empty branch (e.g. EXPIRED) with
        // a configured 'finally' must still go through resolveState() so that finally is awaited.
        if (ListUtils.isEmpty(childTasks) && ListUtils.isEmpty(finallyTasks)) {
            return Optional.of(execution.guessFinalState(null, parentTaskRun, this.isAllowFailure(), this.isAllowWarning()));
        }

        return FlowableUtils.resolveState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            finallyTasks,
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
    }

    private Property<Behavior> behaviorFor(Decision.Type decision) {
        return switch (decision) {
            case APPROVED -> this.approveBehavior;
            case DENIED -> this.denyBehavior;
            case EXPIRED -> this.expireBehavior;
        };
    }

    /** A branch that resolved without error is mapped through the configured behavior; a genuine failure inside the branch is never hidden by it. */
    private State.Type applyBehavior(Behavior behavior, State.Type branchState) {
        return switch (behavior) {
            case CONTINUE -> branchState;
            case WARN -> State.Type.WARNING;
            case FAIL -> State.Type.FAILED;
            case SUCCEED -> State.Type.SUCCESS;
            case CANCEL -> State.Type.CANCELLED;
            case KILL -> State.Type.KILLED;
        };
    }

    /** The execution-level target state for a terminal decided task run (SUCCEED/CANCEL/KILL only), recomputing the branch's own pre-{@link #applyBehavior} state so a genuine failure is never mistaken for one. */
    public Optional<State.Type> executionLevelOutcome(RunContext runContext, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException {
        Decision.Type decision = this.decisionType(runContext);
        if (decision == null) {
            return Optional.empty();
        }

        Optional<State.Type> branchState = this.resolveBranchState(runContext, execution, taskRun);
        if (branchState.isEmpty() || branchState.get().isTerminatedInError()) {
            return Optional.empty();
        }

        Behavior behavior = runContext.render(this.behaviorFor(decision).skipCache()).as(Behavior.class).orElse(Behavior.CONTINUE);
        return switch (behavior) {
            case SUCCEED -> Optional.of(State.Type.SUCCESS);
            case CANCEL -> Optional.of(State.Type.CANCELLED);
            case KILL -> Optional.of(State.Type.KILLED);
            case CONTINUE, WARN, FAIL -> Optional.empty();
        };
    }

    /** Empty while onWait runs, its error state if onWait just failed, PAUSED otherwise (immediately, when onWait is empty). */
    private Optional<State.Type> resolveWaitState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> onWaitTasks = FlowableUtils.resolveTasks(this.onWait, parentTaskRun);
        if (ListUtils.isEmpty(onWaitTasks)) {
            return Optional.of(State.Type.PAUSED);
        }

        Optional<State.Type> waitResult = FlowableUtils.resolveState(
            execution,
            onWaitTasks,
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            null,
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );

        if (waitResult.isEmpty()) {
            return Optional.empty();
        }

        return waitResult.get().isTerminatedInError() ? waitResult : Optional.of(State.Type.PAUSED);
    }

    /**
     * True while the task run is running its {@code onWait} tasks and has never yet reached PAUSED,
     * mirroring {@link Pause}'s own {@code needPause} check.
     */
    public boolean isWaiting(TaskRun taskRun) {
        return taskRun.getState().getCurrent() == State.Type.RUNNING &&
            taskRun.getState().getHistories().stream().noneMatch(history -> history.getState() == State.Type.PAUSED);
    }

    /** Excludes an autoApprove task run: {@link #isWaiting(TaskRun)} alone can't see that a decision was already recorded at creation. */
    public boolean isWaiting(RunContext runContext, TaskRun taskRun) {
        return this.isWaiting(taskRun) && this.decisionType(runContext) == null;
    }

    @SuppressWarnings("unchecked")
    private Decision.Type decisionType(RunContext runContext) {
        Map<String, Object> outputs = runContext.currentOutput();
        Object decision = outputs == null ? null : outputs.get("decision");
        return decision == null ? null : Decision.Type.valueOf((String) decision);
    }

    @Override
    public Approval.Output outputs(RunContext runContext) throws Exception {
        String url = this.executionUrl(runContext);
        Decision.Type decision = this.decisionType(runContext);

        if (decision == null) {
            // the executor reuses this task instance across every execution of the flow, so the property's
            // render cache must be skipped or a later execution would reuse the first execution's value.
            boolean autoApprove = runContext.render(this.autoApprove.skipCache()).as(Boolean.class).orElse(false);
            if (autoApprove) {
                return Output.builder().url(url).decision(Decision.Type.APPROVED).auto(true).build();
            }
            return Output.builder().url(url).build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> current = runContext.currentOutput();
        return Output.builder()
            .url(url)
            .decision(decision)
            .auto(Boolean.TRUE.equals(current.get("auto")))
            .by((String) current.get("by"))
            .on((String) current.get("on"))
            .comment((String) current.get("comment"))
            .inputs((Map<String, Object>) current.get("inputs"))
            .due((String) current.get("due"))
            .build();
    }

    private String executionUrl(RunContext runContext) {
        UriProvider uriProvider = ((DefaultRunContext) runContext).services().uriProvider();
        var flowInfo = runContext.flowInfo();
        var taskRunInfo = runContext.taskRunInfo();
        var uri = uriProvider.executionUrl(flowInfo.tenantId(), flowInfo.namespace(), flowInfo.id(), taskRunInfo.executionId());
        return uri == null ? null : uri.toString();
    }

    @Override
    public List<Input<?>> resumeInputs() {
        return this.inputs;
    }

    @Override
    public Map<String, Object> resumeOutputs(Map<String, Object> inputs, Pause.Resumed resumed, @Nullable Decision decision) {
        Output build = Output.builder()
            .decision(decision != null ? decision.type() : null)
            .auto(false)
            .by(resumed.by())
            .on(resumed.on().toString())
            .comment(decision != null ? decision.comment() : null)
            .inputs(inputs)
            .build();

        return JacksonMapper.toMap(build);
    }

    @Override
    public State.Type resumedTaskRunState(State.Type newState) {
        return switch (newState) {
            case CANCELLED -> State.Type.CANCELLED;
            case KILLING -> State.Type.KILLED;
            default -> State.Type.RUNNING;
        };
    }

    @Override
    public Optional<ExecutionDelay> pauseDelay(TaskRun taskRun, RunContext runContext) throws IllegalVariableEvaluationException, InternalException {
        if (this.getTimeout() == null) {
            return Optional.empty();
        }

        // the executor reuses this task instance across every execution of the flow, so the property's
        // render cache must be skipped or a later execution would reuse the first execution's value.
        Duration timeout = runContext.render(this.getTimeout().skipCache()).as(Duration.class).orElse(null);
        if (timeout == null) {
            return Optional.empty();
        }

        // state is unused for Approval: ExecutionDelayProcessor calls decide(EXPIRED) instead of markAs.
        return Optional.of(ExecutionDelay.builder()
            .taskRunId(taskRun.getId())
            .executionId(taskRun.getExecutionId())
            .date(DateUtils.plusOrThrow(taskRun.getState().maxDate(), timeout))
            .state(State.Type.FAILED)
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build());
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The decision: APPROVED, DENIED or EXPIRED.")
        private Decision.Type decision;

        @Schema(title = "True when the request was approved automatically, without a reviewer.")
        private boolean auto;

        @Schema(title = "Username of the reviewer, empty when expired or auto-approved.")
        private String by;

        @Schema(title = "When the decision was made.")
        private String on;

        @Schema(title = "The reviewer's comment.")
        private String comment;

        @Schema(title = "Values entered in the review dialog.")
        private Map<String, Object> inputs;

        @Schema(title = "When the request expires without a decision, computed from `timeout`. Empty without one.")
        private String due;

        @Schema(title = "A link to the execution page.")
        private String url;
    }

    public record Decision(Type type, @Nullable String comment) {
        public enum Type {
            APPROVED,
            DENIED,
            EXPIRED
        }
    }

    public enum CommentRequired {
        NEVER,
        ALWAYS,
        ON_DENY,
        ON_APPROVE
    }

    public enum Behavior {
        CONTINUE,
        WARN,
        FAIL,
        SUCCEED,
        CANCEL,
        KILL
    }

    @Builder
    @Getter
    public static class Decisions {
        @Builder.Default
        private Property<String> approve = Property.ofValue("Approve");

        @Builder.Default
        private Property<String> deny = Property.ofValue("Deny");
    }
}
