package io.kestra.plugin.core.flow;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.purge.PurgeTask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge old flow revisions.",
    description = """
        Deletes old flow revisions using a purge `behavior` (default keeps 1 latest revision), optional flow ID glob, and Namespace filters (`namespaces` list or `namespacePattern`). Child Namespaces are included by default.

        The latest revision of each flow is always kept."""
)
@Plugin(
    examples = {
        @Example(
            title = "Purge old flow revisions for a namespace tree.",
            full = true,
            code = """
                id: purge_flow_revisions
                namespace: system

                tasks:
                  - id: purge_flows
                    type: io.kestra.plugin.core.flow.PurgeFlows
                    namespaces:
                      - company
                    includeChildNamespaces: true
                    flowPattern: "*_deprecated"
                    behavior:
                      type: version
                      keepAmount: 2
                """
        )
    }
)
public class PurgeFlows extends Task implements PurgeTask<FlowWithSource>, RunnableTask<PurgeFlows.Output> {
    @Schema(
        title = "Flow ID pattern, e.g. '*'",
        description = "Delete only old revisions for flows whose ID matches the glob pattern."
    )
    private Property<String> flowPattern;

    @Schema(
        title = "List of namespaces to delete old flow revisions from",
        description = "If not set, all namespaces will be considered. Can't be used with `namespacePattern` - use one or the other."
    )
    private Property<List<String>> namespaces;

    @Schema(
        title = "Glob pattern for the namespaces to delete old flow revisions from",
        description = "If not set, all namespaces will be considered. Example: `company.*`. Can't be used with `namespaces` - use one or the other."
    )
    private Property<String> namespacePattern;

    @Schema(
        title = "Purge behavior",
        description = "Defines how old flow revisions are purged."
    )
    @Builder.Default
    @Valid
    @NotNull
    private Property<Version> behavior = Property.ofValue(Version.builder().keepAmount(1).build());

    @Schema(
        title = "Delete old flow revisions from child namespaces",
        description = "Defaults to true. This means that if you set `namespaces` to `company`, it will also delete old flow revisions from `company.team`, `company.data`, etc."
    )
    @Builder.Default
    private Property<Boolean> includeChildNamespaces = Property.ofValue(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> flowNamespaces = findNamespaces(runContext);
        runContext.logger().info("purging old flow revisions from {} namespaces: {}", flowNamespaces.size(), flowNamespaces);

        AtomicLong count = new AtomicLong();
        Version renderedBehavior = runContext.render(behavior).as(Version.class).orElseThrow();
        String tenantId = runContext.flowInfo().tenantId();
        FlowRepositoryInterface flowRepository = ((DefaultRunContext) runContext).services().additionalService(FlowRepositoryInterface.class);

        for (String namespace : flowNamespaces) {
            List<FlowWithSource> latestFlows = filterItems(runContext, flowRepository.findByNamespaceWithSource(tenantId, namespace));
            for (FlowWithSource flow : latestFlows) {
                List<Integer> revisions = renderedBehavior.revisionsToPurge(tenantId, namespace, flow.getId(), flowRepository).stream()
                    .map(FlowWithSource::getRevision)
                    .toList();
                if (!revisions.isEmpty()) {
                    flowRepository.deleteRevisions(tenantId, namespace, flow.getId(), revisions);
                    count.addAndGet(revisions.size());
                }
            }
        }

        runContext.logger().info("purged {} flow revisions", count.get());

        return Output.builder()
            .size(count.get())
            .build();
    }

    @Override
    public Property<String> filterPattern() {
        return flowPattern;
    }

    @Override
    public String filterTargetExtractor(FlowWithSource item) {
        return item.getId();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The number of purged flow revisions"
        )
        private Long size;
    }
}
