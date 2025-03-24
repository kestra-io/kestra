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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Pause the current execution and wait for a manual approval (either by humans or other automated processes).", 
    description = "All tasks downstream from the Pause task will be put on hold until the execution is manually resumed from the UI.\n\n" + 
      "The Execution will be in a Paused state, and you can either manually resume it by clicking on the \"Resume\" button in the UI or by calling the POST API endpoint `/api/v1/executions/{executionId}/resume`. The execution can also be resumed automatically after a timeout."
)
@Plugin(
    examples = {
        @Example(
            title = "Pause the execution and wait for a manual approval",
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
                    runner: PROCESS
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
        )
    },
    aliases = "io.kestra.core.tasks.flows.Pause"
)
public class Pause extends Task implements FlowableTask<Pause.Output> {
    @Schema(
        title = "Duration of the pause — useful if you want to pause the execution for a fixed amount of time.",
        description = "The delay is a string in the [ISO 8601 Duration](https://en.wikipedia.org/wiki/ISO_8601#Durations) format, e.g. `PT1H` for 1 hour, `PT30M` for 30 minutes, `PT10S` for 10 seconds, `P1D` for 1 day, etc. If no delay and no timeout are configured, the execution will never end until it's manually resumed from the UI or API.",
        implementation = Duration.class
    )
    private Property<Duration> delay;

    @Schema(
        title = "Timeout of the pause — useful to avoid never-ending workflows in a human-in-the-loop scenario. For example, if you want to pause the execution until a human validates some data generated in a previous task, you can set a timeout of e.g. 24 hours. If no manual approval happens within 24 hours, the execution will automatically resume without a prior data validation.",
        description = "If no delay and no timeout are configured, the execution will never end until it's manually resumed from the UI or API.",
        implementation = Duration.class
    )
    private Property<Duration> timeout;

    @Valid
    @Schema(
        title = "Inputs to be passed to the execution when it's resumed.",
        description = "Before resuming the execution, the user will be prompted to fill in these inputs. The inputs can be used to pass additional data to the execution which is useful for human-in-the-loop scenarios. The `onResume` inputs work the same way as regular [flow inputs](https://kestra.io/docs/workflow-components/inputs) — they can be of any type and can have default values. You can access those values in downstream tasks using the `onResume` output of the Pause task.")
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
        if (this.tasks == null || this.tasks.isEmpty()) {
            return new GraphTask(this, taskRun, parentValues, RelationType.SEQUENTIAL);
        }

        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            this.tasks,
            this.errors,
            this._finally,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                this.getTasks() != null ? this.getTasks().stream() : Stream.empty(),
                Stream.concat(
                    this.getErrors() != null ? this.getErrors().stream() : Stream.empty(),
                    this.getFinally() != null ? this.getFinally().stream() : Stream.empty()
                )
            )
            .toList();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.getTasks(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun) || parentTaskRun.getState().getCurrent() == State.Type.PAUSED) {
            return new ArrayList<>();
        }

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun
        );
    }

    private boolean needPause(TaskRun parentTaskRun) {
        return parentTaskRun.getState().getCurrent() == State.Type.RUNNING &&
            parentTaskRun.getState().getHistories().stream().noneMatch(history -> history.getState() == State.Type.PAUSED);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (this.needPause(parentTaskRun)) {
            return Optional.of(State.Type.PAUSED);
        }

        if (this.tasks == null || this.tasks.isEmpty()) {
            return Optional.of(State.Type.SUCCESS);
        }

        return FlowableTask.super.resolveState(runContext, execution, parentTaskRun);
    }

    public Map<String, Object> generateOutputs(Map<String, Object> inputs) {
        Output build = Output.builder()
            .onResume(inputs)
            .build();

        return JacksonMapper.toMap(build);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private Map<String, Object> onResume;
    }
}
