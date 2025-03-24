package io.kestra.plugin.core.execution;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.ZonedDateTime;
import java.util.List;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List execution counts for a list of flow.",
    description = "This can be used to send an alert if a condition is met about execution counts."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a slack notification if there is no execution for a flow for the last 24 hours.",
            full = true,
            code = """
                id: executions_count
                namespace: company.team

                tasks:
                  - id: counts
                    type: io.kestra.plugin.core.execution.Count
                    expression: "{{ count == 0 }}"
                    flows:
                      - namespace: company.team
                        flowId: logs
                    startDate: "{{ now() | dateAdd(-1, 'DAYS') }}"

                  - id: for_each
                    type: io.kestra.plugin.core.flow.ForEach
                    concurrencyLimit: 0
                    values: "{{ jq outputs.counts.results '. | select(. != null) | .[]' }}"
                    tasks:
                      - id: slack_incoming_webhook
                        type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                        payload: |
                          {
                            "channel": "#run-channel",
                            "text": ":warning: Flow `{{ jq taskrun.value '.namespace' true }}`.`{{ jq taskrun.value '.flowId' true }}` has no execution for last 24h!"
                          }
                        url: "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    backfill: {}
                    cron: "0 4 * * * "
                """
        )
    },
    aliases = "io.kestra.core.tasks.executions.Counts"
)
public class Count extends Task implements RunnableTask<Count.Output> {
    @Schema(
        title = "A list of flows to be filtered.",
        description = "If not provided, namespaces must be set."
    )
    @PluginProperty
    protected List<Flow> flows;

    @Schema(
        title = "A list of states to be filtered."
    )
    protected Property<List<State.Type>> states;

    @NotNull
    @Schema(
        title = "The start date."
    )
    protected Property<String> startDate;

    @Schema(
        title = "The end date."
    )
    protected Property<String> endDate;

    @NotNull
    @Schema(
        title = "The expression to look at against each flow.",
        description = "The expression is such that expression must return `true` in order to keep the current line.\n" +
            "Some examples: \n" +
            "- ```yaml {{ eq count 0 }} ```: no execution found\n" +
            "- ```yaml {{ gte count 5 }} ```: more than 5 executions\n"
    )
    @PluginProperty(dynamic = true) // we cannot use `Property` as we render it multiple time with different variables, which is an issue for the property cache
    protected String expression;

    protected Property<List<String>> namespaces;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        ExecutionRepositoryInterface executionRepository = ((DefaultRunContext)runContext)
            .getApplicationContext()
            .getBean(ExecutionRepositoryInterface.class);

        if (flows == null && namespaces == null) {
            throw new IllegalArgumentException("You must provide a list of flows or namespaces");
        }

        var flowInfo = runContext.flowInfo();

        // check that all flows are allowed
        FlowService flowService = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowService.class);
        if (flows != null) {
            flows.forEach(flow -> flowService.checkAllowedNamespace(flowInfo.tenantId(), flow.getNamespace(), flowInfo.tenantId(), flowInfo.namespace()));
        }

        if (namespaces != null) {
            var renderedNamespaces = runContext.render(this.namespaces).asList(String.class);
            renderedNamespaces.forEach(namespace -> flowService.checkAllowedNamespace(flowInfo.tenantId(), namespace, flowInfo.tenantId(), flowInfo.namespace()));
        }

        List<ExecutionCount> executionCounts = executionRepository.executionCounts(
            flowInfo.tenantId(),
            flows,
            runContext.render(this.states).asList(State.Type.class),
            runContext.render(this.startDate).as(String.class).map(ZonedDateTime::parse).orElse(null),
            runContext.render(this.endDate).as(String.class).map(ZonedDateTime::parse).orElse(null),
            runContext.render(this.namespaces).asList(String.class)
        );

        logger.trace("{} flows matching filters", executionCounts.size());

        List<Result> count = executionCounts
            .stream()
            .filter(throwPredicate(item -> runContext
                .render(this.expression, ImmutableMap.of("count", item.getCount().intValue()))
                .equals("true")
            ))
            .map(item -> Result.builder()
                .namespace(item.getNamespace())
                .flowId(item.getFlowId())
                .count(item.getCount())
                .build()
            )
            .toList();

        logger.debug("{} flows matching the expression", count.size());

        return Output.builder()
            .results(count)
            .total(count.stream().mapToLong(value -> value.count).sum())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final List<Result> results;
        private final Long total;
    }

    @Builder
    @Getter
    public static class Result {
        private String namespace;
        private String flowId;
        private Long count;
    }
}
