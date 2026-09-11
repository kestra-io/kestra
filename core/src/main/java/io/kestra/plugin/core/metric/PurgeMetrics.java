package io.kestra.plugin.core.metric;

import java.time.ZonedDateTime;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TypeConverter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge execution metrics.",
    description = """
        Deletes metrics in bulk by namespace/flow filters and date ranges. Requires namespace authorization when targeting other namespaces.

        For performance, use this instead of per-execution metric deletions."""
)
@Plugin(
    examples = {
        @Example(
            title = "Purge all metrics that were created more than one month ago.",
            full = true,
            code = """
                id: purge_metrics
                namespace: system

                tasks:
                  - id: purge
                    type: io.kestra.plugin.core.metric.PurgeMetrics
                    endDate: "{{ now() | dateAdd(-1, 'MONTHS') }}"
                """
        ),
        @Example(
            title = "Purge all metrics for a specific namespace that were created more than one month ago.",
            full = true,
            code = """
                id: purge_metrics
                namespace: system

                tasks:
                  - id: purge
                    type: io.kestra.plugin.core.metric.PurgeMetrics
                    namespace: company.team
                    endDate: "{{ now() | dateAdd(-1, 'MONTHS') }}"
                """
        )
    }
)
public class PurgeMetrics extends Task implements RunnableTask<PurgeMetrics.Output>, SystemTask {
    @Schema(
        title = "Namespace whose metrics need to be purged, or namespace of the flow that needs to be purged",
        description = "If `flowId` isn't provided, this is a namespace prefix, else the namespace of the flow."
    )
    private Property<String> namespace;

    @Schema(
        title = "The flow ID of the metrics to be purged",
        description = "You need to provide the `namespace` property if you want to purge flow metrics."
    )
    private Property<String> flowId;

    @Schema(
        title = "The minimum date to be purged",
        description = "All metrics after this date will be purged."
    )
    private Property<String> startDate;

    @Schema(
        title = "The maximum date to be purged",
        description = "All metrics before this date will be purged."
    )
    @NotNull
    private Property<String> endDate;

    @Override
    public Output run(RunContext runContext) throws Exception {
        MetricRepositoryInterface metricRepository = ((DefaultRunContext) runContext).services().additionalService(MetricRepositoryInterface.class);

        var flowInfo = runContext.flowInfo();
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElse(null);
        String renderedFlowId = runContext.render(this.flowId).as(String.class).orElse(null);

        if (renderedNamespace == null && renderedFlowId != null) {
            throw new IllegalArgumentException("Property `namespace` is required when `flowId` is set.");
        }

        if (renderedNamespace == null) {
            runContext.acl().allowAllNamespaces().check();
        } else if (!renderedNamespace.equals(flowInfo.namespace())) {
            runContext.acl().allowNamespace(renderedNamespace).check();
        }

        String renderedStartDate = runContext.render(this.startDate).as(String.class).orElse(null);
        ZonedDateTime rStartDate = renderedStartDate != null ? TypeConverter.toZonedDateTime(renderedStartDate) : null;
        ZonedDateTime rEndDate = TypeConverter.toZonedDateTime(runContext.render(this.endDate).as(String.class).orElseThrow());

        int count = metricRepository.purge(
            flowInfo.tenantId(),
            renderedNamespace,
            renderedFlowId,
            rStartDate,
            rEndDate
        );

        return Output.builder()
            .count(count)
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The total count of deleted metrics"
        )
        private int count;
    }
}
