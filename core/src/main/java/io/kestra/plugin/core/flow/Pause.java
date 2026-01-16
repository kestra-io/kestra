package io.kestra.plugin.core.flow;

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
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.GraphTask;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.ListUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pause the current execution and wait for approval (either by humans or other automated processes).",
    description = "All tasks downstream from the Pause task will be put on hold until the execution is manually resumed from the UI.\n\n" +
        "The Execution will be in a Paused state, and you can either manually resume it by clicking on the \"Resume\" button in the UI or by calling the POST API endpoint `/api/v1/executions/{executionId}/resume`. The execution can also be resumed automatically after the `pauseDuration`."
)
@Plugin(
    examples = {
        @Example(
            title = "Pause the execution and wait for a manual approval.",
            full = true,
            code = """
                id: human_in_the_loop
                namespace: company.team

                tasks:
                  - id: before_approval
                    type: io.kestra.plugin.core.debug.Return
                    format: Output data that needs to be validated by a human

                  - id: pause
                    type: io.kestra.plugin.core.flow.Pause

                  - id: run_post_approval
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                      - echo "Manual approval received! Continuing the execution..."

                  - id: post_resume
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ task.id }} started on {{ taskrun.startDate }} after the Pause"
                """
        ),
        @Example(
            title = "Vacation approval process pausing the execution for approval and waiting for input from a human to approve or reject the request.",
            full = true,
            code = """
                id: vacation_approval_process
                namespace: company.team

                inputs:
                  - id: request.name
                    type: STRING
                    defaults: Rick Astley

                  - id: request.start_date
                    type: DATE
                    defaults: 2042-07-01

                  - id: request.end_date
                    type: DATE
                    defaults: 2042-07-07

                  - id: slack_webhook_uri
                    type: URI
                    defaults: https://reqres.in/api/slack

                tasks:
                  - id: send_approval_request
                    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                    url: "{{ inputs.slack_webhook_uri }}"
                    payload: |
                      {
                        "channel": "#vacation",
                        "text": "Validate holiday request for {{ inputs.request.name }}. To approve the request, click on the `Resume` button here http://localhost:28080/ui/executions/{{flow.namespace}}/{{flow.id}}/{{execution.id}}"
                      }

                  - id: wait_for_approval
                    type: io.kestra.plugin.core.flow.Pause
                    onResume:
                      - id: approved
                        description: Whether to approve the request
                        type: BOOLEAN
                        defaults: true
                      - id: reason
                        description: Reason for approval or rejection
                        type: STRING
                        defaults: Well-deserved vacation

                  - id: approve
                    type: io.kestra.plugin.core.http.Request
                    uri: https://reqres.in/api/products
                    method: POST
                    contentType: application/json
                    body: "{{ inputs.request }}"

                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: Status is {{ outputs.wait_for_approval.onResume.reason }}. Process finished with {{ outputs.approve.body }}
                """
        ),
        @Example(
            title = "Pause the execution and set the execution to WARNING if it has not been resumed after 5 minutes.",
            full = true,
            code = """
                id: pause_warn
                namespace: company.team

                tasks:
                  - id: pause
                    type: io.kestra.plugin.core.flow.Pause
                    pauseDuration: PT5M
                    behavior: WARN

                  - id: post_resume
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ task.id }} started on {{ taskrun.startDate }} after the Pause"
                """
        )
    },
    aliases = "io.kestra.core.tasks.flows.Pause"
)
public class Pause extends Task implements FlowableTask<Pause.Output> {
    @Schema(
        title = "Duration of the pause — useful if you want to pause the execution for a fixed amount of time.",
        description = "**Deprecated**: use `pauseDuration` instead.",
        implementation = Duration.class
    )
    @Deprecated
    private Property<Duration> delay;

    @Deprecated
    public void setDelay(Property<Duration> delay) {
        this.delay = delay;
        this.pauseDuration = delay;
    }

    @Schema(
        title = "Duration of the pause - if not set, the task will wait forever to be manually resumed except if a timeout is set, in this case, the timeout will be honored.",
        description = "The duration is a string in [ISO 8601 Duration](https://en.wikipedia.org/wiki/ISO_8601#Durations) format, e.g. `PT1H` for 1 hour, `PT30M` for 30 minutes, `PT10S` for 10 seconds, `P1D` for 1 day, etc. If no pauseDuration and no timeout are configured, the execution will never end until it's manually resumed from the UI or API.",
        implementation = Duration.class
    )
    private Property<Duration> pauseDuration;

    @Schema(
        title = "Pause behavior, by default set to RESUME. This property controls happens when a pause task reach its duration.",
        description = """
            Tasks that are resumed before the duration (for example, from the UI) will not use the behavior property but will always succeed.
            Possible values are:
            - RESUME: continues with the execution
            - WARN: ends the Pause task in WARNING and continues with the execution
            - FAIL: fails the Pause task
            - CANCEL: cancels the execution"""
    )
    @NotNull
    @Builder.Default
    protected Property<Behavior> behavior = Property.ofValue(Behavior.RESUME);

    @Valid
    @Schema(
        title = "A runnable task that will be executed when it's paused"
    )
    @PluginProperty
    private Task onPause;

    @Valid
    @Schema(
        title = "Inputs to be passed to the execution when it's resumed",
        description = "Before resuming the execution, the user will be prompted to fill in these inputs. The inputs can be used to pass additional data to the execution, which is useful for human-in-the-loop scenarios. The `onResume` inputs work the same way as regular [flow inputs](https://kestra.io/docs/workflow-components/inputs) — they can be of any type and can have default values. You can access those values in downstream tasks using the `onResume` output of the Pause task.")
    @PluginProperty
    private List<Input<?>> onResume;

    @Valid
    protected List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Valid
    @PluginProperty
    @Deprecated
    private List<Task> tasks;

    @Override
    public AbstractGraph tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        if (ListUtils.isEmpty(tasks) && ListUtils.isEmpty(errors) && ListUtils.isEmpty(_finally)) {
            return new GraphTask(this, taskRun, parentValues, RelationType.SEQUENTIAL);
        }

        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            this.getOnPause() != null ? ListUtils.concat(List.of(this.getOnPause()), this.tasks) : ListUtils.emptyOnNull(this.tasks),
            this.errors,
            this._finally,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return ListUtils.concat(
            this.getTasks(),
            this.getOnPause() != null ? List.of(this.getOnPause()) : null,
            this.getErrors(),
            this.getFinally()
        );
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<Task> childTasks = new ArrayList<>(ListUtils.emptyOnNull(this.getTasks()));
        if (onPause != null) {
            childTasks.addFirst(onPause);
        }
        return FlowableUtils.resolveTasks(childTasks, parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun) || (parentTaskRun.getState().getCurrent() == State.Type.PAUSED)) {
            return Collections.emptyList();
        }

        // get back the original state of the Pause task
        State.Type terminalState = findTerminalState(parentTaskRun);
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun,
            terminalState
        );
    }

    @SuppressWarnings("unchecked")
    private static State.Type findTerminalState(TaskRun parentTaskRun) {
        Map<String, Object> resumed = (Map<String, Object>) parentTaskRun.getOutputs().get("resumed");
        return resumed.isEmpty() || !resumed.containsKey("to") ? State.Type.SUCCESS : State.Type.valueOf((String) resumed.get("to"));
    }

    private boolean needPause(TaskRun parentTaskRun) {
        return parentTaskRun.getState().getCurrent() == State.Type.RUNNING &&
            parentTaskRun.getState().getHistories().stream().noneMatch(history -> history.getState() == State.Type.PAUSED);
    }

    // This method is only called when there are subtasks
    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun)) {
            return Optional.of(State.Type.PAUSED);
        }

        // get back the original state of the Pause task
        State.Type terminalState = findTerminalState(parentTaskRun);
        return FlowableUtils.resolveState(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            isAllowFailure(),
            isAllowWarning(),
            terminalState
        );
    }

    public Map<String, Object> generateOutputs(Map<String, Object> inputs, Resumed resumed) {
        Output build = Output.builder()
            .onResume(inputs)
            .resumed(resumed)
            .build();

        return JacksonMapper.toMap(build);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private Map<String, Object> onResume;

        @Schema(title = "Resumed information: when and by who the execution was resumed")
        private Resumed resumed;
    }

    public record Resumed(@Nullable String by, LocalDateTime on, State.Type to) {
        public static Resumed now() {
            return new Resumed(null, LocalDateTime.now(), State.Type.SUCCESS);
        }

        public static Resumed now(State.Type to) {
            return new Resumed(null, LocalDateTime.now(), to);
        }

        public static Resumed now(String by) {
            return new Resumed(by, LocalDateTime.now(), State.Type.SUCCESS);
        }

        public static Resumed now(String by, State.Type to) {
            return new Resumed(by, LocalDateTime.now(), to);
        }
    }

    public enum Behavior {
        RESUME(State.Type.RUNNING),
        WARN(State.Type.WARNING),
        CANCEL(State.Type.CANCELLED),
        FAIL(State.Type.FAILED);

        private final State.Type executionState;

        Behavior(State.Type executionState) {
            this.executionState = executionState;
        }

        public State.Type mapToState() {
            return this.executionState;
        }
    }
}
