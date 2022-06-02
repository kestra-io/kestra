package io.kestra.core.tasks.executions;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List execution counts for a list of flow",
    description = "Mostly use for send an alert if a conditions is meet about execution counts."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a slack notification if no execution for a flow on last 24h",
            full = true,
            code = {
                "id: executions-count",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: counts",
                "    type: io.kestra.core.tasks.executions.Counts",
                "    expression: \"{{ count == 0 }}\"",
                "    flows:",
                "      - namespace: io.kestra.tests",
                "        flowId: logs",
                "    startDate: \"{{ now() | dateAdd(-1, 'DAYS') }}\"",
                "  - id: each_parallel",
                "    type: io.kestra.core.tasks.flows.EachParallel",
                "    tasks:",
                "      - id: slack_incoming_webhook",
                "        type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook",
                "        payload: |",
                "          {",
                "            \"channel\": \"#run-channel\",",
                "            \"text\": \":warning: Flow `{{ jq taskrun.value '.namespace' true }}`.`{{ jq taskrun.value '.flowId' true }}` has no execution on last 24h !\"",
                "          }",
                "        url: \"https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX\"",
                "    value: \"{{ jq outputs.counts.results '. | select(. != null) | .[]' }}\"",
                "",
                "triggers:",
                "  - id: schedule",
                "    type: io.kestra.core.models.triggers.types.Schedule",
                "    backfill: {}",
                "    cron: \"0 4 * * * \""
            }
        )
    }
)
public class Counts extends Task implements RunnableTask<Counts.Output> {
    @NotNull
    @NotEmpty
    @Schema(
        title = "A list of flows to be filtered"
    )
    @PluginProperty(dynamic = false)
    protected List<Flow> flows;

    @Schema(
        title = "A list of state to be filtered"
    )
    @PluginProperty(dynamic = false)
    protected List<State.Type> states;

    @NotNull
    @Schema(
        title = "The start date"
    )
    @PluginProperty(dynamic = true)
    protected String startDate;

    @Schema(
        title = "The end date"
    )
    @PluginProperty(dynamic = true)
    protected String endDate;

    @NotNull
    @Schema(
        title = "The expression against each flows to look at",
        description = "The expression is as handlebars expression must return `true` in order to keep the current line\n" +
            "some examples: \n" +
            "- `{{ eq count 0 }}`: no execution found\n" +
            "- `{{ gte count 5 }}`: more than 5 executions\n"
    )
    @PluginProperty(dynamic = true)
    protected String expression;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        ExecutionRepositoryInterface executionRepository = runContext
            .getApplicationContext()
            .getBean(ExecutionRepositoryInterface.class);

        List<ExecutionCount> executionCounts = executionRepository.executionCounts(
            flows,
            this.states,
            startDate != null ? ZonedDateTime.parse(runContext.render(startDate)) : null,
            endDate != null ? ZonedDateTime.parse(runContext.render(endDate)) : null
        );

        logger.trace("{} flows matching filters", executionCounts.size());

        List<Result> count = executionCounts
            .stream()
            .filter(throwPredicate(item -> runContext
                .render(
                    this.expression,
                    ImmutableMap.of("count", item.getCount().intValue())
                )
                .equals("true")
            ))
            .map(item -> Result.builder()
                .namespace(item.getNamespace())
                .flowId(item.getFlowId())
                .count(item.getCount())
                .build()
            )
            .collect(Collectors.toList());

        logger.debug("{} flows matching the expression", count.size());

        return Output.builder()
            .results(count)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final List<Result> results;
    }

    @Builder
    @Getter
    public static class Result {
        private String namespace;
        private String flowId;
        private Long count;
    }
}
