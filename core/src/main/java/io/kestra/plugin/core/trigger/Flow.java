package io.kestra.plugin.core.trigger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.LabelService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.utils.TruthUtils;
import io.kestra.core.validations.PreconditionFilterValidation;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.stream.Streams;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.models.flows.State.Type.PAUSED;
import static io.kestra.core.topologies.FlowTopologyService.SIMULATED_EXECUTION;
import static io.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow in response to a state change in one or more other flows.",
    description = """
        You can trigger a flow as soon as another flow ends. This allows you to add implicit dependencies between multiple flows, which can often be managed by different teams.

        A flow trigger must have `preconditions` which filter on other flow executions.
        It can also have standard trigger `conditions`. Neither condition type can use Pebble templating expressions; they must be declaratively defined.
        Upstream execution outputs will be available in a `trigger.outputs` variable."""
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
                    preconditions:
                      id: flows
                      flows:
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
                    preconditions:
                      id: bronze_layer
                      timeWindow:
                        type: DAILY_TIME_DEADLINE
                        deadline: "09:00:00"
                      flows:
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
                    preconditions:
                      id: company_namespace
                      where:
                        - id: company
                          filters:
                            - field: NAMESPACE
                              type: STARTS_WITH
                              value: company"""
        ),
        @Example(
            full = true,
            title = """
                4) Create a `System Flow` to send a Sentry issue on any failure or warning state \
                within the `company.payroll` namespace. This example uses the Sentry Execution task and a Flow trigger with `conditions`.""",
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
                  conditions:
                  - type: io.kestra.plugin.core.condition.ExecutionStatus
                    in:
                      - FAILED
                      - WARNING
                  - type: io.kestra.plugin.core.condition.ExecutionNamespace
                    namespace: company.payroll
                    prefix: false"""
        ),
        @Example(
            full = true,
            title = """
                5) Chain two different flows (`flow_a` and `flow_b`) and trigger the second only after the first completes successfully with matching labels. Note that this example is two separate flows.""",
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
                    states: [SUCCESS]
                    labels:
                      type: orchestration
                    preconditions:
                      id: flow_a
                        id: flow_a
                        where:
                          - id: label_filter
                            filters:
                              - field: EXPRESSION
                                type: IS_TRUE
                                value: "{{ labels.type == 'orchestration' }}
                """
        )

    },
    aliases = "io.kestra.core.models.triggers.types.Flow"
)
@Slf4j
public class Flow extends AbstractTrigger implements TriggerOutput<Flow.Output> {
    private static final String TRIGGER_VAR = "trigger";
    private static final String OUTPUTS_VAR = "outputs";

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
            Any `ExecutionStatus`-type condition will be evaluated after the list of `states`. Note that a Flow trigger cannot react to the `CREATED` state because the Flow trigger reacts to state transitions. The `CREATED` state is the initial state of an execution and does not represent a state transition.
            ::alert{type="info"}
            The trigger will be evaluated for each state change of matching executions. If a flow has two `Pause` tasks, the execution will transition from PAUSED to a RUNNING state twice — one for each Pause task. In this case, a Flow trigger listening to a `PAUSED` state will be evaluated twice.
            ::"""
    )
    @Builder.Default
    private List<State.Type> states = ListUtils.concat(State.Type.terminatedTypes(), List.of(PAUSED));

    @Valid
    @Schema(
        title = "Preconditions on upstream flow executions",
        description = "Express preconditions to be met, on a time window, for the flow trigger to be evaluated."
    )
    @PluginProperty
    private Preconditions preconditions;

    @SuppressWarnings("deprecation")
    public Optional<Execution> evaluate(Optional<MultipleConditionStorageInterface> multipleConditionStorage, RunContext runContext, io.kestra.core.models.flows.Flow flow, Execution current) {
        Logger logger = runContext.logger();

        // merge outputs from all the matched executions
        Map<String, Object> outputs = current.getOutputs();
        if (multipleConditionStorage.isPresent()) {
            List<String> multipleConditionIds = new ArrayList<>();
            if (this.preconditions != null) {
                multipleConditionIds.add(this.preconditions.getId());
            }
            ListUtils.emptyOnNull(this.conditions).stream()
                .filter(condition -> condition instanceof io.kestra.plugin.core.condition.MultipleCondition)
                .map(condition -> (io.kestra.plugin.core.condition.MultipleCondition) condition)
                .forEach(condition -> multipleConditionIds.add(condition.getId()));

            for (String id : multipleConditionIds) {
                Optional<MultipleConditionWindow> multipleConditionWindow = multipleConditionStorage.get().get(flow, id);
                if (multipleConditionWindow.isPresent()) {
                    outputs = MapUtils.deepMerge(outputs, multipleConditionWindow.get().getOutputs());
                }
            }
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
            .trigger(ExecutionTrigger.of(
                this,
                Output.builder()
                    .executionId(current.getId())
                    .executionLabels(Label.toNestedMap(current.getLabels().stream().filter(label -> !label.key().equals(Label.CORRELATION_ID)).collect(Collectors.toList())))
                    .namespace(current.getNamespace())
                    .flowId(current.getFlowId())
                    .flowRevision(current.getFlowRevision())
                    .state(current.getState().getCurrent())
                    .outputs(outputs)
                    .build()
            ));

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
            return Optional.empty();
        }
    }

    @Builder
    @Getter
    public static class Preconditions implements MultipleCondition {
        @NotNull
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]*")
        @Schema(title = "A unique id for the preconditions")
        @PluginProperty
        private String id;

        @Schema(
            title = "Define the time window for evaluating preconditions.",
            description = """
                You can set the `type` of `timeWindow` to one of the following values:
                1. `DURATION_WINDOW`: this is the default `type`. It uses a start time (`windowAdvance`) and end time (`window`) that advance to the next interval whenever the evaluation time reaches the end time, based on the defined duration `window`. For example, with a 1-day window (the default option: `window: PT1D`), the preconditions are evaluated during a 24-hour period starting at midnight (i.e., at "00:00:00+00:00") each day. If you set `windowAdvance: PT6H`, the window will start at 6 AM each day. If you set `windowAdvance: PT6H` and also override the `window` property to `PT6H`, the window will start at 6 AM and last for 6 hours. In this configuration, the preconditions will be evaluated during the following intervals: 06:00 to 12:00, 12:00 to 18:00, 18:00 to 00:00, and 00:00 to 06:00.
                2. `SLIDING_WINDOW`: this option evaluates preconditions over a fixed time `window` but always goes backward from the current time. For example, a sliding window of 1 hour (`window: PT1H`) evaluates executions within the past hour (from one hour ago up to now). It uses a default window of 1 day.
                3. `DAILY_TIME_DEADLINE`: this option declares that preconditions should be met "before a specific time in a day." Using the string property `deadline`, you can configure a daily cutoff for evaluating preconditions. For example, `deadline: "09:00:00"` specifies that preconditions must be met from midnight until 9 AM UTC time each day; otherwise, the flow will not be triggered.
                4. `DAILY_TIME_WINDOW`: this option declares that preconditions should be met "within a specific time range in a day". For example, a window from `startTime: "06:00:00"` to `endTime: "09:00:00"` evaluates executions within that interval each day. This option is particularly useful for defining freshness conditions declaratively when building data pipelines that span multiple teams and namespaces. Normally, a failure in any task in your flow will block the entire pipeline, but with this decoupled flow trigger alternative, you can proceed as soon as the data is successfully refreshed within the specified time window."""
        )
        @PluginProperty
        @Builder.Default
        @Valid
        protected TimeWindow timeWindow = TimeWindow.builder().build();

        @Schema(
            title = "Whether to reset the evaluation results of preconditions after a first successful evaluation within the given time window",
            description = """
                By default, after a successful evaluation of the set of preconditions, the evaluation result is reset. This means the same set of conditions needs to be successfully evaluated again within the same time window to trigger a new execution.
                In this setup, to create multiple executions, the same set of conditions must be evaluated to `true` multiple times within the defined window.
                You can disable this by setting this property to `false`, so that within the same window, each time one of the conditions is satisfied again after a successful evaluation, it will trigger a new execution."""
        )
        @PluginProperty
        @Builder.Default
        private Boolean resetOnSuccess = Boolean.TRUE;

        @Schema(title = "A list of preconditions to meet, in the form of upstream flows")
        @PluginProperty
        private List<UpstreamFlow> flows;

        @Valid
        @PluginProperty
        @Schema(title = "A list of preconditions to meet, in the form of execution filters")
        private List<ExecutionFilter> where;

        @JsonIgnore
        @Override
        public Map<String, Condition> getConditions() {
            AtomicInteger conditionId = new AtomicInteger();
            Map<String, Condition> flowsCondition = ListUtils.emptyOnNull(flows).stream()
                .map(upstreamFlow -> Map.entry(
                    "condition_" + conditionId.incrementAndGet(),
                    new UpstreamFlowCondition(upstreamFlow)
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Map<String, Condition> whereConditions = ListUtils.emptyOnNull(where).stream()
                .map(filter -> Map.entry(
                    "condition_" + conditionId.incrementAndGet() + "_" + filter.getId(),
                    new FilterCondition(filter)
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Map<String, Condition> conditions = HashMap.newHashMap(flowsCondition.size() + whereConditions.size());
            conditions.putAll(flowsCondition);
            conditions.putAll(whereConditions);
            return conditions;
        }

        @JsonIgnore
        public Map<String, Condition> getUpstreamFlowsConditions() {
            AtomicInteger conditionId = new AtomicInteger();
            return ListUtils.emptyOnNull(flows).stream()
                .map(upstreamFlow -> Map.entry(
                    "condition_" + conditionId.incrementAndGet(),
                    new UpstreamFlowCondition(upstreamFlow)
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @JsonIgnore
        public Map<String, Condition> getWhereConditions() {
            AtomicInteger conditionId = new AtomicInteger();
            return ListUtils.emptyOnNull(where).stream()
                .map(filter -> Map.entry(
                    "condition_" + conditionId.incrementAndGet() + "_" + filter.getId(),
                    new FilterCondition(filter)
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @Override
        public Logger logger() {
            return log;
        }
    }

    @Builder
    @Getter
    public static class UpstreamFlow {
        @NotNull
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
    }

    @Hidden
    public static class UpstreamFlowCondition extends Condition {
        private final UpstreamFlow upstreamFlow;

        private UpstreamFlowCondition(UpstreamFlow upstreamFlow) {
            this.upstreamFlow = Objects.requireNonNull(upstreamFlow);
        }

        @Override
        public boolean test(ConditionContext conditionContext) throws InternalException {
            if (upstreamFlow.namespace != null && !conditionContext.getExecution().getNamespace().equals(upstreamFlow.namespace)) {
                return false;
            }

            if (upstreamFlow.flowId != null && !conditionContext.getExecution().getFlowId().equals(upstreamFlow.flowId)) {
                return false;
            }

            // we need to only evaluate on namespace and flow for simulated executions
            if (ListUtils.emptyOnNull(conditionContext.getExecution().getLabels()).contains(SIMULATED_EXECUTION)) {
                return true;
            }

            if (upstreamFlow.states != null && !upstreamFlow.states.contains(conditionContext.getExecution().getState().getCurrent())) {
                return false;
            }

            if (upstreamFlow.labels != null) {
                boolean notMatched = upstreamFlow.labels.entrySet().stream()
                    .map(entry -> new Label(entry.getKey(), String.valueOf(entry.getValue())))
                    .anyMatch(label -> !conditionContext.getExecution().getLabels().contains(label));
                return !notMatched;
            }

            return true;
        }
    }

    @Builder
    @Getter
    public static class ExecutionFilter {
        @NotNull
        @NotEmpty
        @PluginProperty
        @Schema(title = "A unique identifier for the filter")
        private String id;

        @Builder.Default
        @PluginProperty
        @Schema(title = "The operand to apply between all filters of the precondition")
        private Operand operand = Operand.AND;

        @NotNull
        @NotEmpty
        @Valid
        @PluginProperty
        @Schema(title = "The list of filters")
        private List<Filter> filters;
    }

    public enum Operand {
        AND,
        OR
    }

    @Builder
    @Getter
    @PreconditionFilterValidation
    public static class Filter {
        @NotNull
        @PluginProperty
        @Schema(
            title = "The field which will be filtered"
        )
        private Field field;

        @NotNull
        @PluginProperty
        @Schema(
            title = "The type of filter",
            description = "Can be set to one of the following: `EQUAL_TO`, `NOT_EQUAL_TO`, `IS_NULL`, `IS_NOT_NULL`, `IS_TRUE`, `IS_FALSE`, `STARTS_WITH`, `ENDS_WITH`, `REGEX`, `CONTAINS`. Depending on the `type`, you will need to also set the `value` or `values` property."
        )
        private Type type;

        @PluginProperty
        @Schema(
            title = "The single value to filter the `field` on",
            description = "Must be set according to its `type`."
        )
        private String value;

        @PluginProperty
        @Schema(
            title = "The list of values to filter the `field` on",
            description = "Must be set for the following types: IN, NOT_IN."
        )
        private List<String> values;
    }

    public enum Field {
        FLOW_ID,
        NAMESPACE,
        STATE,
        EXPRESSION,
    }

    public enum Type {
        EQUAL_TO,
        NOT_EQUAL_TO,
        IN,
        NOT_IN,
        IS_TRUE,
        IS_FALSE,
        IS_NULL,
        IS_NOT_NULL,
        STARTS_WITH,
        ENDS_WITH,
        REGEX,
        CONTAINS,
    }

    @Hidden
    public static class FilterCondition extends io.kestra.core.models.conditions.Condition {
        private final ExecutionFilter filter;

        private FilterCondition(ExecutionFilter filter) {
            this.filter = Objects.requireNonNull(filter);
        }

        @Override
        public boolean test(ConditionContext conditionContext) throws InternalException {
            // we need to only evaluate on namespace and flow for simulated executions
            boolean simulated = ListUtils.emptyOnNull(conditionContext.getExecution().getLabels()).contains(SIMULATED_EXECUTION);
            Stream<Filter> toEvaluate = simulated ? filter.filters.stream().filter(filter -> filter.field == Field.NAMESPACE || filter.field == Field.FLOW_ID) : filter.filters.stream();

            return switch (filter.operand) {
                case AND -> toEvaluate.allMatch(throwPredicate(filter -> evaluate(conditionContext, filter)));
                case OR -> toEvaluate.anyMatch(throwPredicate(filter -> evaluate(conditionContext, filter)));
                case null -> toEvaluate.allMatch(throwPredicate(filter -> evaluate(conditionContext, filter)));
            };
        }

        private boolean evaluate(ConditionContext conditionContext, Filter filter) throws IllegalVariableEvaluationException {
            String fieldValue = switch (filter.field) {
                case FLOW_ID -> conditionContext.getExecution().getFlowId();
                case NAMESPACE -> conditionContext.getExecution().getNamespace();
                case STATE -> conditionContext.getExecution().getState().getCurrent().toString();
                case EXPRESSION -> conditionContext.getRunContext().render(filter.value);
            };

            return switch (filter.type) {
                case EQUAL_TO -> filter.value.equals(fieldValue);
                case NOT_EQUAL_TO -> !filter.value.equals(fieldValue);
                case IN -> filter.values.contains(fieldValue);
                case NOT_IN -> !filter.values.contains(fieldValue);
                case IS_TRUE -> TruthUtils.isTruthy(fieldValue);
                case IS_FALSE -> !TruthUtils.isTruthy(fieldValue);
                case IS_NULL -> fieldValue == null;
                case IS_NOT_NULL -> fieldValue != null;
                case STARTS_WITH -> fieldValue != null && fieldValue.startsWith(filter.value);
                case ENDS_WITH -> fieldValue != null && fieldValue.endsWith(filter.value);
                case REGEX -> fieldValue != null && fieldValue.matches(filter.value);
                case CONTAINS -> fieldValue != null && fieldValue.contains(filter.value);
            };
        }
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The execution ID that triggered the current flow")
        @NotNull
        private String executionId;

        @Schema(title = "The execution labels that triggered the current flow")
        @NotNull
        private Map<String, Object> executionLabels;

        @Schema(title = "The execution state")
        @NotNull
        private State.Type state;

        @Schema(title = "The namespace of the flow that triggered the current flow")
        @NotNull
        private String namespace;

        @Schema(title = "The flow ID whose execution triggered the current flow")
        @NotNull
        private String flowId;

        @Schema(title = "The flow revision that triggered the current flow")
        @NotNull
        private Integer flowRevision;

        @Schema(title = "The extracted outputs from the flow that triggered the current flow")
        private Map<String, Object> outputs;
    }
}
