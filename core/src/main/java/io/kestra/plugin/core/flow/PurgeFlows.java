package io.kestra.plugin.core.flow;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;

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
        Deletes old flow revisions using a purge `behavior` (default keeps 1 latest revision), with optional namespace prefix and flow ID filters.

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
                    namespace: company
                    behavior:
                      keepAmount: 2
                """
        )
    }
)
public class PurgeFlows extends Task implements RunnableTask<PurgeFlows.Output>, SystemTask {
    @Schema(
        title = "Namespace whose flows need to be purged, or namespace of the flow that needs to be purged",
        description = "If `flowId` isn't provided, this is a namespace prefix, else the namespace of the flow."
    )
    private Property<String> namespace;

    @Schema(
        title = "The flow ID whose old revisions should be purged",
        description = "You need to provide the `namespace` property if you want to purge a flow."
    )
    private Property<String> flowId;

    @Schema(
        title = "Purge behavior",
        description = "Defines how old flow revisions are purged."
    )
    @Builder.Default
    @Valid
    @NotNull
    private Property<Version> behavior = Property.ofValue(Version.builder().keepAmount(1).build());

    @Override
    public Output run(RunContext runContext) throws Exception {
        AtomicLong count = new AtomicLong();
        Version renderedBehavior = runContext.render(behavior).as(Version.class).orElseThrow();
        String tenantId = runContext.flowInfo().tenantId();
        FlowRepositoryInterface flowRepository = ((DefaultRunContext) runContext).services().additionalService(FlowRepositoryInterface.class);
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElse(null);
        String renderedFlowId = runContext.render(this.flowId).as(String.class).orElse(null);
        if (renderedNamespace == null && renderedFlowId != null) {
            throw new IllegalArgumentException("Property `namespace` is required when `flowId` is set.");
        }

        List<FlowWithSource> flows = findFlows(runContext, flowRepository, tenantId, renderedNamespace, renderedFlowId);
        runContext.logger().info("purging old revisions from {} flows", flows.size());

        for (FlowWithSource flow : flows) {
            List<Integer> revisions = renderedBehavior.revisionsToPurge(tenantId, flow.getNamespace(), flow.getId(), flowRepository).stream()
                .map(FlowWithSource::getRevision)
                .toList();
            if (!revisions.isEmpty()) {
                flowRepository.deleteRevisions(tenantId, flow.getNamespace(), flow.getId(), revisions);
                count.addAndGet(revisions.size());
            }
        }

        runContext.logger().info("purged {} flow revisions", count.get());

        return Output.builder()
            .size(count.get())
            .build();
    }

    private List<FlowWithSource> findFlows(
        RunContext runContext,
        FlowRepositoryInterface flowRepository,
        String tenantId,
        String renderedNamespace,
        String renderedFlowId
    ) {
        if (renderedNamespace == null) {
            runContext.acl().allowAllNamespaces().check();
            return flowRepository.findAllWithSource(tenantId);
        }

        runContext.acl().allowNamespace(renderedNamespace).check();
        if (renderedFlowId != null) {
            return flowRepository.findByIdWithSource(tenantId, renderedNamespace, renderedFlowId)
                .map(List::of)
                .orElseGet(List::of);
        }

        return flowRepository.findByNamespacePrefixWithSource(tenantId, renderedNamespace);
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
