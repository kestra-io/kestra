package io.kestra.plugin.core.flow;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
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

        A denial runs `onDeny`; the task then ends in the state that branch ended in."""
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
        if (this.decisionType(runContext) == null) {
            return this.resolveWaitState(runContext, execution, parentTaskRun);
        }

        List<ResolvedTask> childTasks = ListUtils.emptyOnNull(this.childTasks(runContext, parentTaskRun)).stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .toList();
        if (ListUtils.isEmpty(childTasks)) {
            return Optional.of(execution.computeFinalState(null, parentTaskRun, this.isAllowFailure(), this.isAllowWarning()));
        }

        return FlowableUtils.resolveState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
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
    public Approval.Output outputs(RunContext runContext) throws Exception {
        String url = this.executionUrl(runContext);
        Decision.Type decision = this.decisionType(runContext);

        if (decision == null) {
            boolean autoApprove = runContext.render(this.autoApprove).as(Boolean.class).orElse(false);
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

        @Schema(title = "A link to the execution page.")
        private String url;
    }

    public enum CommentRequired {
        NEVER,
        ALWAYS,
        ON_DENY,
        ON_APPROVE
    }

    @Builder
    @Getter
    public static class Decisions {
        private Property<String> approve;

        private Property<String> deny;
    }
}
