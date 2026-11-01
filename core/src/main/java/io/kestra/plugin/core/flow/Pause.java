package io.kestra.plugin.core.flow;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pause the flow until it is resumed.",
    description = """
        Stops downstream task scheduling and moves the execution to PAUSED. Resume manually from the UI, via the `/executions/{id}/resume` API, or automatically after `pauseDuration` if set.

        You can declare `inputs` to collect human/automation feedback before continuing.

        `onPause` remains a non-blocking hook dispatched when the task pauses. Scoped `errors` and `finally` are deprecated but remain supported during migration. Move them to the flow, or wrap the Pause in a `Sequential` task when handlers must remain scoped."""
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
                    type: io.kestra.plugin.core.log.Log
                    message: Output data that needs to be validated by a human

                  - id: pause
                    type: io.kestra.plugin.core.flow.Pause

                  - id: run_post_approval
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                      - echo "Manual approval received! Continuing the execution..."

                  - id: post_resume
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ task.id }} started on {{ taskrun.startDate }} after the Pause"
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
                    type: io.kestra.plugin.slack.notifications.SlackIncomingWebhook
                    url: "{{ inputs.slack_webhook_uri }}"
                    payload: |
                      {
                        "channel": "#vacation",
                        "text": "Validate holiday request for {{ inputs.request.name }}. To approve the request, click on the `Resume` button here http://localhost:28080/ui/executions/{{flow.namespace}}/{{flow.id}}/{{execution.id}}"
                      }

                  - id: wait_for_approval
                    type: io.kestra.plugin.core.flow.Pause
                    inputs:
                      - id: approved
                        description: Whether to approve the request
                        type: BOOL
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
                    message: Status is {{ outputs.wait_for_approval.inputs.reason }}. Process finished with {{ outputs.approve.body }}
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
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ task.id }} started on {{ taskrun.startDate }} after the Pause"
                """
        )
    }
)
public class Pause extends Task implements PausableTask {
    @Schema(
        title = "Duration of the pause - if not set, the task will wait forever to be manually resumed except if a timeout is set, in this case, the timeout will be honored.",
        description = "The duration is a string in [ISO 8601 Duration](https://en.wikipedia.org/wiki/ISO_8601#Durations) format, e.g. `PT1H` for 1 hour, `PT30M` for 30 minutes, `PT10S` for 10 seconds, `P1D` for 1 day, etc. If no pauseDuration and no timeout are configured, the execution will never end until it's manually resumed from the UI or API.",
        implementation = Duration.class
    )
    private Property<@DurationMin(millis = 1, message = "must be a positive duration") Duration> pauseDuration;

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

    @Schema(
        title = "Inputs to be passed to the execution when it's resumed",
        description = "Before resuming the execution, the user will be prompted to fill in these inputs. The inputs can be used to pass additional data to the execution, which is useful for human-in-the-loop scenarios. They work the same way as regular [flow inputs](https://kestra.io/docs/workflow-components/inputs) — they can be of any type and can have default values. Defaults and descriptions can use Pebble expressions referencing the paused execution's inputs and earlier task outputs. Users can override the evaluated defaults before resuming. You can access those values in downstream tasks using the `inputs` output of the Pause task."
    )
    @PluginProperty
    private List<@Valid Input<?>> inputs;

    @Deprecated(forRemoval = true, since = "2.1.0")
    @Getter(AccessLevel.NONE)
    @PluginProperty
    @Schema(title = "Deprecated scoped error handlers.", description = "Use flow-level `errors`, or wrap the Pause in a Sequential task for scoped handling.")
    protected List<@Valid Task> errors;

    @Deprecated(forRemoval = true, since = "2.1.0")
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    @PluginProperty
    @Schema(title = "Deprecated scoped finalization tasks.", description = "Use flow-level `finally`, or wrap the Pause in a Sequential task for scoped handling.")
    protected List<@Valid Task> _finally;

    @Deprecated(forRemoval = true, since = "2.1.0")
    public List<Task> getErrors() {
        return this.errors;
    }

    @Deprecated(forRemoval = true, since = "2.1.0")
    public List<Task> getFinally() {
        return this._finally;
    }

    @Override
    public List<Task> onPauseTasks() {
        return this.onPause == null ? List.of() : List.of(this.onPause);
    }

    @Override
    public List<Task> allChildTasks() {
        return ListUtils.concat(
            this.onPauseTasks(),
            this.errors,
            this._finally
        );
    }

    @Deprecated(forRemoval = true, since = "2.1.0")
    public void setOnResume(List<Input<?>> onResume) {
        this.inputs = onResume;
    }

    @Override
    public List<Input<?>> resumeInputs() {
        return this.inputs;
    }

    @Override
    public Map<String, Object> resumeOutputs(Map<String, Object> inputs, Resumed resumed, @Nullable Decision decision) {
        if (decision != null) {
            throw new IllegalArgumentException("Task '%s' is a Pause and cannot be resumed with a decision.".formatted(this.getId()));
        }

        Output build = Output.builder()
            .inputs(inputs)
            .onResume(inputs)
            .resumed(resumed)
            .build();

        return JacksonMapper.toMap(build);
    }

    @Override
    public Optional<State.Type> resumedTaskRunState(State.Type newState) {
        return Optional.of(switch (newState) {
            case RUNNING -> State.Type.SUCCESS;
            case KILLING -> State.Type.KILLED;
            default -> newState;
        });
    }

    @Override
    public Optional<ExecutionDelay> pauseDelay(TaskRun taskRun, RunContext runContext) throws IllegalVariableEvaluationException, InternalException {
        if (this.pauseDuration == null && this.getTimeout() == null) {
            return Optional.empty();
        }

        Duration duration = runContext.render(this.pauseDuration).as(Duration.class).orElse(null);
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new InternalException("The Pause 'pauseDuration' must be a strictly positive duration but was '%s'.".formatted(duration));
        }
        Duration timeout = runContext.render(this.getTimeout()).as(Duration.class).orElse(null);
        Behavior behavior = runContext.render(this.behavior).as(Behavior.class).orElse(Behavior.RESUME);

        if (duration == null && timeout == null) { // rendering can lead to null, so we must re-check here
            return Optional.empty();
        }

        // if duration is set, we use it, and we use the Pause behavior as a state
        // if no duration, we use the standard timeout property and use FAILED as the target state
        return Optional.of(ExecutionDelay.builder()
            .taskRunId(taskRun.getId())
            .executionId(taskRun.getExecutionId())
            .date(DateUtils.plusOrThrow(taskRun.getState().maxDate(), duration != null ? duration : timeout))
            .state(duration != null ? behavior.mapToState() : State.Type.fail(this))
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build());
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Values entered when the execution was resumed.")
        private Map<String, Object> inputs;

        @Deprecated(forRemoval = true, since = "2.1.0")
        @Schema(title = "Deprecated, use `inputs`.")
        private Map<String, Object> onResume;

        @Schema(title = "Resumed information: when and by who the execution was resumed")
        private Resumed resumed;
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
