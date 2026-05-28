package io.kestra.plugin.core.trigger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.Window;
import io.kestra.core.utils.*;
import org.apache.commons.lang3.stream.Streams;
import org.slf4j.Logger;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.triggers.multipleflows.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.LabelService;
import io.kestra.core.validations.FlowTriggerValidation;

import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.models.flows.State.Type.PAUSED;
import static io.kestra.core.topologies.FlowTopologyService.SIMULATED_EXECUTION;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a Flow based on other Flows’ executions.",
    description = """
        Fires when upstream Flow executions meet `dependsOn` (required) and optional trigger `when` condition. Lets you chain Flows owned by different teams.

        Upstream execution outputs are exposed under `trigger.outputs`; you can also pass `inputs` to the downstream Flow."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = """
                1) Trigger the `transform` flow after the `extract` flow finishes successfully. \
                The `extract` flow generates a `date` output that is passed to the \
                `transform` flow as an input. \

                ```yaml
                id: extract
                namespace: company.team

                tasks:
                  - id: final_date
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ execution.startDate | dateAdd(-2, 'DAYS') | date('yyyy-MM-dd') }}"

                outputs:
                  - id: date
                    type: STRING
                    value: "{{ outputs.final_date.value }}"
                ```

                The `transform` flow is triggered after the `extract` flow finishes successfully.""",
            code = """
                id: transform
                namespace: company.team

                inputs:
                  - id: date
                    type: STRING
                    defaults: "2025-01-01"

                variables:
                  result: |
                    Ingestion done in {{ trigger.executionId }}.
                    Now transforming data up to {{ inputs.date }}

                tasks:
                  - id: run_transform
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ render(vars.result) }}"

                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ render(vars.result) }}"

                triggers:
                  - id: run_after_extract
                    type: io.kestra.plugin.core.trigger.Flow
                    inputs:
                      date: "{{ trigger.outputs.date }}"
                    dependsOn:
                      - namespace: company.team
                        flowId: extract
                        states: [SUCCESS]"""
        ),
        @Example(
            full = true,
            title = """
                2) Trigger the `silver_layer` flow once the `bronze_layer` flow finishes successfully by 9 AM.

                ```yaml
                id: bronze_layer
                namespace: company.team

                tasks:
                  - id: raw_data
                    type: io.kestra.plugin.core.log.Log
                    message: Ingesting raw data
                ```""",
            code = """
                id: silver_layer
                namespace: company.team

                tasks:
                  - id: transform_data
                    type: io.kestra.plugin.core.log.Log
                    message: deduplication, cleaning, and minor aggregations

                triggers:
                  - id: flow_trigger
                    type: io.kestra.plugin.core.trigger.Flow
                    window:
                      deadline: "09:00:00"
                    dependsOn:
                      - namespace: company.team
                        flowId: bronze_layer
                        states: [SUCCESS]"""
        ),
        @Example(
            full = true,
            title = """
                3) Create a `System Flow` to send a Slack alert on any failure or warning state \
                within the `company` namespace. This example uses the Slack webhook secret to \
                notify the `#general` channel about the failed flow.""",
            code = """
                id: alert
                namespace: system

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.notifications.slack.SlackExecution
                    url: "{{secret('SLACK_WEBHOOK')}}" # format: https://hooks.slack.com/services/xzy/xyz/xyz
                    channel: "#general"
                    executionId: "{{trigger.executionId}}"

                triggers:
                  - id: alert_on_failure
                    type: io.kestra.plugin.core.trigger.Flow
                    states:
                      - FAILED
                      - WARNING
                    when: "{{flow.namespace | startsWith('company')}}\""""
        ),
        @Example(
            full = true,
            title = """
                4) Create a `System Flow` to send a Sentry issue on any failure or warning state \
                within the `company.payroll` namespace. This example uses the Sentry Execution task and a Flow trigger with `dependsOn`.""",
            code = """
                id: sentry_execution_example
                namespace: company.team

                tasks:
                - id: send_alert
                  type: io.kestra.plugin.notifications.sentry.SentryExecution
                  executionId: "{{ trigger.executionId }}"
                  transaction: "/execution/id/{{ trigger.executionId }}"
                  dsn: "{{ secret('SENTRY_DSN') }}"
                  level: ERROR

                triggers:
                - id: failed_prod_workflows
                  type: io.kestra.plugin.core.trigger.Flow
                  dependsOn:
                  - states
                      - FAILED
                      - WARNING
                    namespace: company.payroll"""
        ),
        @Example(
            full = true,
            title = """
                5) Chain two different flows (`flow_a` and `flow_b`) and trigger `flow_b` only after `flow_a` completes successfully with matching labels. Note that this example shows two separate flows.""",
            code = """
                id: flow_a
                namespace: company.team
                labels:
                  type: orchestration
                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World!
                ---
                id: flow_b
                namespace: company.team
                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World!
                triggers:
                  - id: on_completion
                    type: io.kestra.plugin.core.trigger.Flow
                    dependsOn:
                      - namespace: company.team
                        flowId: flow_a
                        states: [SUCCESS]
                        labels:
                          type: orchestration
                """
        )

    }
)
@Slf4j
@FlowTriggerValidation
public class Flow extends AbstractTrigger implements TriggerOutput<Flow.Output> {
    private static final String TRIGGER_VAR = "trigger";
    private static final String OUTPUTS_VAR = "outputs";
    static final String DEPENDS_ON_CONDITION_PREFIX = "depends_on_";

    @Nullable
    @Schema(
        title = "Pass upstream flow's outputs to inputs of the current flow.",
        description = """
            The inputs property passes data objects or a file to the downstream flow as long as those outputs are defined on the flow-level in the upstream flow.
            ::alert{type="warning"}
            Make sure that the inputs and task outputs defined in this Flow trigger match the outputs of the upstream flow. Otherwise, the downstream flow execution will not to be created. If that happens, go to the Logs tab on the Flow page to investigate the error.
            ::"""
    )
    @PluginProperty
    private Map<String, Object> inputs;

    @Nullable
    @Schema(
        title = "List of execution states that will be evaluated by the trigger",
        description = """
            By default, only executions in a terminal state or in the PAUSED state will be evaluated.
            Note that a Flow trigger cannot react to the `CREATED` state because the Flow trigger reacts to state transitions. The `CREATED` state is the initial state of an execution and does not represent a state transition.
            ::alert{type="info"}
            The trigger will be evaluated for each state change of matching executions. If a flow has two `Pause` tasks, the execution will transition from PAUSED to a RUNNING state twice — one for each Pause task. In this case, a Flow trigger listening to a `PAUSED` state will be evaluated twice.
            ::"""
    )
    @Builder.Default
    private List<State.Type> states = ListUtils.concat(State.Type.terminatedTypes(), List.of(PAUSED));

    @Valid
    @Schema(
        title = "Dependencies on upstream flow executions",
        description = "Express dependencies on upstream flow executions, which must be met for the flow trigger to be evaluated."
    )
    @PluginProperty
    private List<Dependency> dependsOn;

    @Valid
    @Schema(
        title = "Window configuration for the dependsOn trigger",
        description = "Configure the time window within which all dependsOn conditions must be met."
    )
    @PluginProperty
    private Window window;

    @Schema(
        title = "Mode for evaluating dependsOn conditions",
        description = """
            Specifies how the dependsOn conditions should be evaluated: ALL, ANY, or AT_LEAST.
            When using AT_LEAST, you must also set `minSatisfied` to the minimum number of conditions that must be satisfied within the window."""
    )
    @PluginProperty
    @NotNull
    @Builder.Default
    private MultipleCondition.Mode mode = MultipleCondition.Mode.ALL;

    @Schema(
        title = "Minimum number of satisfied dependsOn conditions for AT_LEAST mode",
        description = "When mode is set to AT_LEAST, this specifies the minimum number of conditions that must be satisfied within the window."
    )
    @PluginProperty
    @Positive
    private Integer minSatisfied;

    public Optional<Execution> evaluate(Optional<MultipleConditionWindow> multipleConditionWindow, RunContext runContext, io.kestra.core.models.flows.Flow flow, Execution current) {
        Logger logger = runContext.logger();

        // merge outputs from all the matched executions
        Map<String, Object> outputs = current.getOutputs();
        if (multipleConditionWindow.isPresent()) {
            outputs = MapUtils.deepMerge(outputs, multipleConditionWindow.get().getOutputs());
        }

        List<Label> labels = LabelService.fromTrigger(runContext, flow, this);
        Streams.of(current.getLabels())
            .filter(label -> label.key().equals(Label.CORRELATION_ID))
            .findFirst()
            .ifPresent(label -> labels.add(label));

        Execution.ExecutionBuilder builder = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .labels(labels)
            .state(new State())
            .trigger(
                ExecutionTrigger.of(
                    this,
                    Output.builder()
                        .executionId(current.getId())
                        .executionLabels(Label.toNestedMap(current.getLabels().stream().filter(label -> !label.key().equals(Label.CORRELATION_ID)).collect(Collectors.toList())))
                        .namespace(current.getNamespace())
                        .flowId(current.getFlowId())
                        .flowRevision(current.getFlowRevision())
                        .state(current.getState().getCurrent())
                        .startDate(current.getState().getStartDate())
                        .endDate(current.getState().getEndDate().orElse(null))
                        .firstFailedTaskId(ListUtils.emptyOnNull(current.getTaskRunList()).stream().filter(t -> t.getState().getCurrent().isFailed()).map(TaskRun::getTaskId).findFirst().orElse(null))
                        .lastTaskId(ListUtils.emptyOnNull(current.getTaskRunList()).reversed().stream().map(TaskRun::getTaskId).findFirst().orElse(null))
                        .outputs(outputs)
                        .build()
                )
            );

        try {
            if (this.inputs != null) {
                if (outputs != null && !outputs.isEmpty()) {
                    builder.inputs(runContext.render(this.inputs, Map.of(TRIGGER_VAR, Map.of(OUTPUTS_VAR, outputs))));
                } else {
                    builder.inputs(runContext.render(this.inputs));
                }
            } else {
                builder.inputs(new HashMap<>());
            }
            return Optional.of(builder.build());
        } catch (Exception e) {
            logger.warn(
                "Failed to trigger flow {}.{} for trigger {}, invalid inputs",
                flow.getNamespace(),
                flow.getId(),
                this.getId(),
                e
            );
            var failedExecution = builder.build().withState(State.Type.FAILED);
            return Optional.of(failedExecution);
        }
    }

    // WARNING: when adding a new attribute to this class, update the hashing function inside the DependsOnMultipleCondition class.
    @Builder
    @Getter
    public static class Dependency {
        @Schema(title = "The namespace of the flow")
        @PluginProperty
        private String namespace;

        @Schema(title = "The flow ID")
        @PluginProperty
        private String flowId;

        @Schema(title = "The execution states")
        @PluginProperty
        private List<State.Type> states;

        @Schema(title = "A key/value map of labels")
        @PluginProperty
        private Map<String, Object> labels;

        @Builder.Default
        @Schema(
            title = "A condition that determines whether the trigger should run for that dependency.",
            description = "A Pebble expression evaluated at trigger time. The trigger fires only when the expression evaluates to a truthy value (`true`, a non-empty string, a non-zero number). Use this to gate trigger execution on dynamic runtime values such as execution labels, flow variables, or environment conditions."
        )
        private Property<String> when = Property.ofValue("true");

        public Condition asCondition() {
            return new DependencyCondition(this);
        }
    }

    public MultipleCondition dependsOnAsMultipleCondition() {
        return this.dependsOn == null ? null : new DependsOnMultipleCondition(this.dependsOn, this.getId(), this.window, this.mode, this.minSatisfied);
    }

    @Hidden
    static class DependencyCondition implements Condition {
        private final Dependency dependency;

        DependencyCondition(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public boolean test(ConditionContext conditionContext) throws InternalException {
            if (dependency.namespace != null && !conditionContext.getExecution().getNamespace().equals(dependency.namespace)) {
                return false;
            }

            if (dependency.flowId != null && !conditionContext.getExecution().getFlowId().equals(dependency.flowId)) {
                return false;
            }

            // we need to only evaluate on namespace and flow for simulated executions
            if (ListUtils.emptyOnNull(conditionContext.getExecution().getLabels()).contains(SIMULATED_EXECUTION)) {
                return true;
            }

            if (dependency.states != null && !dependency.states.contains(conditionContext.getExecution().getState().getCurrent())) {
                return false;
            }

            if (dependency.labels != null) {
                boolean notMatched = dependency.labels.entrySet().stream()
                    .map(entry -> new Label(entry.getKey(), String.valueOf(entry.getValue())))
                    .anyMatch(label -> !ListUtils.emptyOnNull(conditionContext.getExecution().getLabels()).contains(label));
                if (notMatched) {
                    return false;
                }
            }

            return TruthUtils.isTruthy(conditionContext.getRunContext().render(dependency.when).as(String.class).orElse("true"));
        }
    }

    @Hidden
    public static class DependsOnMultipleCondition implements MultipleCondition {
        private final List<Dependency> dependencies;
        private final String id;
        private final Window window;
        private final Mode mode;
        private final Integer minSatisfied;

        DependsOnMultipleCondition(List<Dependency> dependencies, String id, Window window, Mode mode, Integer minSatisfied) {
            this.dependencies = dependencies;
            this.id = id;
            this.window = window;
            this.mode = mode;
            this.minSatisfied = minSatisfied;
        }


        @Override
        public String getId() {
            return DEPENDS_ON_CONDITION_PREFIX + id;
        }

        @Override
        public TimeWindow getTimeWindow() {
            return window == null ? TimeWindow.builder().build() : window.toTimeWindow();
        }

        @Override
        public Boolean getResetOnSuccess() {
            return window == null ? Boolean.TRUE : window.isFireOnce();
        }

        @Override
        public Map<String, Condition> getConditions() {
            return ListUtils.emptyOnNull(dependencies).stream()
                .map(
                    dependency -> Map.entry(
                        hash(dependency),
                        new DependencyCondition(dependency)
                    )
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @Override
        public Logger logger() {
            return log;
        }

        @Override
        public Mode getMode() {
            return mode;
        }

        @Override
        public Integer getMinSatisfied() {
            return minSatisfied;
        }

        private String hash(Dependency dependency) {
            return Hashing.hashToString(
                dependency.namespace,
                "_", // avoid possible mismatch between namespace and flowId
                dependency.flowId,
                dependency.when != null ? dependency.when.toString() : null,
                ListUtils.emptyOnNull(dependency.states).stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                MapUtils.emptyOnNull(dependency.labels).entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + ":" + entry.getValue()).collect(Collectors.joining(","))
            );
        }
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The execution ID that triggered the current flow",
            description = "In case multiple executions triggered the current flow, this will be the last one."
        )
        @NotNull
        private String executionId;

        @Schema(
            title = "The execution labels that triggered the current flow",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        @NotNull
        private Map<String, Object> executionLabels;

        @Schema(
            title = "The execution state",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        @NotNull
        private State.Type state;

        @Schema(
            title = "The execution start date",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        @NotNull
        private Instant startDate;

        @Schema(
            title = "The execution end date",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        @NotNull
        private Instant endDate;

        @Schema(
            title = "The namespace of the flow that triggered the current flow",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        @NotNull
        private String namespace;

        @Schema(
            title = "The flow ID whose execution triggered the current flow",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        @NotNull
        private String flowId;

        @Schema(
            title = "The flow revision that triggered the current flow",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        @NotNull
        private Integer flowRevision;

        @Schema(
            title = "The first failed task ID from the execution that triggered the current flow",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        private String firstFailedTaskId;

        @Schema(
            title = "The last task ID from the execution that triggered the current flow",
            description = "In case multiple executions triggered the current flow, this will be the last one.")
        private String lastTaskId;

        @Schema(
            title = "The extracted outputs from the flows that triggered the current flow",
            description = "As there can be multiple executions that trigger this flow, each output will be prefixed by its namespace and flow ID. For example, 'namespace.flowId.key' will be the key for the output 'key' from the flow with ID 'flowId' in namespace 'namespace'."
        )
        private Map<String, Object> outputs;
    }
}
