package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.time.Instant;
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
import io.kestra.core.models.hierarchies.Relation;
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
        description = "for instance Continue and Stop for a question asked by an agent. A decision left unset shows the translated Approve or Deny label."
    )
    private Decisions decisions;

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
        title = "Title of the request, shown to the reviewers.",
        description = "Enterprise Edition only."
    )
    private Property<String> title;

    @Schema(
        title = "Users and groups allowed to review the request.",
        description = "Enterprise Edition only."
    )
    private Assignment assignment;

    @JsonProperty("case")
    @Getter(AccessLevel.NONE)
    @Schema(
        title = "Approval case opened for the request.",
        description = "Enterprise Edition only."
    )
    private CaseConfig _case;

    @Schema(
        title = "Whether the assignees and the requester get in-app notifications, true when unset.",
        description = "Enterprise Edition only."
    )
    private Property<Boolean> inAppNotification;

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

    public CaseConfig getCase() {
        return this._case;
    }

    @Override
    public List<Task> getErrors() {
        return errors;
    }

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.CHOICE);
        if (ListUtils.isEmpty(this.onWait) && ListUtils.isEmpty(this.onApprove) && ListUtils.isEmpty(this.onDeny)) {
            subGraph.addEdge(subGraph.getTaskNode(), subGraph.getFinally(), new Relation());
        }

        GraphUtils.sequentialThenSwitchCase(
            subGraph,
            ListUtils.emptyOnNull(this.onWait),
            Map.of(
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
        // autoApprove records its decision at creation, so it goes straight to its branch instead of running onWait.
        Decision.Type decision = this.decisionType(runContext);
        if (decision == null) {
            return FlowableUtils.resolveTasks(this.onWait, parentTaskRun);
        }
        if (decision == Decision.Type.EXPIRED) {
            return Collections.emptyList();
        }
        return FlowableUtils.resolveTasks(decision == Decision.Type.APPROVED ? this.onApprove : this.onDeny, parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.decisionType(runContext) == null) {
            // the Branch phase never schedules 'finally' when onWait failed, so it is scheduled here.
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
        if (decision == null) {
            return this.resolveWaitState(runContext, execution, parentTaskRun);
        }

        Optional<State.Type> branchState = this.resolveBranchState(runContext, execution, parentTaskRun);

        if (branchState.isEmpty() || decision == null || branchState.get().isTerminatedInError()) {
            return branchState;
        }

        Behavior behavior = runContext.render(this.behaviorFor(decision)).as(Behavior.class).orElse(Behavior.CONTINUE);
        return Optional.of(this.applyBehavior(behavior, branchState.get()));
    }

    /** The Branch phase's own resolved state, before a configured behavior is mapped over it. */
    private Optional<State.Type> resolveBranchState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = ListUtils.emptyOnNull(this.childTasks(runContext, parentTaskRun)).stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .toList();

        List<ResolvedTask> finallyTasks = FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun);

        // computeFinalState(null, ...) ignores real children, so an empty branch with a 'finally' must go through resolveState().
        if (ListUtils.isEmpty(childTasks) && ListUtils.isEmpty(finallyTasks)) {
            return Optional.of(execution.computeFinalState(null, parentTaskRun, this.isAllowFailure(), this.isAllowWarning()));
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

    /** Execution-level target state of a decided task run (SUCCEED/CANCEL/KILL only), empty when the branch itself failed. */
    @Override
    public Optional<State.Type> executionLevelOutcome(RunContext runContext, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException {
        Decision.Type decision = this.decisionType(runContext);
        if (decision == null) {
            return Optional.empty();
        }

        Optional<State.Type> branchState = this.resolveBranchState(runContext, execution, taskRun);
        if (branchState.isEmpty() || branchState.get().isTerminatedInError()) {
            return Optional.empty();
        }

        Behavior behavior = runContext.render(this.behaviorFor(decision)).as(Behavior.class).orElse(Behavior.CONTINUE);
        return switch (behavior) {
            case SUCCEED -> Optional.of(State.Type.SUCCESS);
            case CANCEL -> Optional.of(State.Type.CANCELLED);
            case KILL -> Optional.of(State.Type.KILLED);
            case CONTINUE, WARN, FAIL -> Optional.empty();
        };
    }

    /** PAUSING while onWait runs, its error state if onWait failed, PAUSED once it succeeded (immediately, when onWait is empty). */
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
            return parentTaskRun.getState().getCurrent() == State.Type.PAUSING ? Optional.empty() : Optional.of(State.Type.PAUSING);
        }

        return waitResult.get().isTerminatedInError() ? waitResult : Optional.of(State.Type.PAUSED);
    }

    @SuppressWarnings("unchecked")
    private Decision.Type decisionType(RunContext runContext) {
        Map<String, Object> outputs = runContext.currentOutput();
        Object decision = outputs == null ? null : outputs.get("decision");
        return decision == null ? null : Decision.Type.valueOf((String) decision);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Approval.Output outputs(RunContext runContext) throws Exception {
        Map<String, Object> current = runContext.currentOutput();
        Decision.Type decision = this.decisionType(runContext);

        // skipCache: the task instance is shared across executions.
        String title = current != null && current.get("title") != null ? (String) current.get("title") : this.renderTitle(runContext);
        String url = current != null && current.get("url") != null ? (String) current.get("url") : this.executionUrl(runContext);
        String caseId = current == null ? null : (String) current.get("caseId");

        if (decision == null) {
            boolean autoApprove = runContext.render(this.autoApprove).as(Boolean.class).orElse(false);
            if (autoApprove) {
                return Output.builder().url(url).title(title).decision(Decision.Type.APPROVED).auto(true).build();
            }
            return Output.builder().url(url).title(title).caseId(caseId).build();
        }

        return Output.builder()
            .url(url)
            .title(title)
            .caseId(caseId)
            .decision(decision)
            .auto(Boolean.TRUE.equals(current.get("auto")))
            .by((String) current.get("by"))
            .on((String) current.get("on"))
            .comment((String) current.get("comment"))
            .inputs((Map<String, Object>) current.get("inputs"))
            .due(current.get("due") == null ? null : Instant.parse(current.get("due").toString()))
            .build();
    }

    private String renderTitle(RunContext runContext) throws IllegalVariableEvaluationException {
        return this.title == null ? null : runContext.render(this.title).as(String.class).orElse(null);
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
    public Map<String, Object> resumeOutputs(Map<String, Object> inputs, Resumed resumed, @Nullable Decision decision) {
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

        Duration timeout = runContext.render(this.getTimeout()).as(Duration.class).orElse(null);
        if (timeout == null) {
            return Optional.empty();
        }

        // state is unused for Approval: the delay records expiredDecision() instead of resuming the task run.
        return Optional.of(ExecutionDelay.builder()
            .taskRunId(taskRun.getId())
            .executionId(taskRun.getExecutionId())
            .date(DateUtils.plusOrThrow(taskRun.getState().maxDate(), timeout))
            .state(State.Type.FAILED)
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build());
    }

    @Override
    public Map<String, Object> pauseDelayOutputs(ExecutionDelay delay) {
        return Map.of("due", delay.getDate().toString());
    }

    @Override
    public Optional<Decision> expiredDecision() {
        return Optional.of(new Decision(Decision.Type.EXPIRED, null));
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
        private Instant due;

        @Schema(title = "A link to the execution page.")
        private String url;

        @Schema(title = "The rendered request title. Enterprise Edition only.")
        private String title;

        @Schema(title = "Identifier of the approval case. Enterprise Edition only.")
        private String caseId;
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    public static class Assignment {
        @Schema(title = "Users allowed to review the request.")
        private List<String> users;

        @Schema(title = "Groups allowed to review the request.")
        private List<String> groups;
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    public static class CaseConfig {
        @Schema(title = "Open an approval case for the request.")
        private Property<Boolean> enabled;

        @Schema(title = "Identifier of an existing case of the same execution to reuse, for multi-step approvals.")
        private Property<String> id;

        @Schema(title = "Severity of the case.")
        private Property<String> severity;

        @Schema(title = "Description of the case.")
        private Property<String> description;

        @Schema(title = "Deadline of the case, defaults to `timeout`.")
        private Property<Duration> sla;
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
        private Property<String> approve;

        private Property<String> deny;
    }
}
