package io.kestra.plugin.core.storage;

import java.time.ZonedDateTime;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.StoragePurgeService;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Purge execution files from the internal storage by last-modified date.",
    description = """
        **Irreversible.** Always start with `dryRun: true` and review the counters before re-running with `dryRun: false`.

        When using per-namespace dedicated storage (EE), this task must be configured for each namespace to be able to \
        clean the dedicated storage.

        Deletes execution files whose last-modified timestamp falls between `startDate` and `endDate`. The primary use \
        case is reclaiming files left behind by flows that no longer exist — files that `PurgeExecutions` cannot reach \
        because there is no execution record to delete.

        `namespace` matching is recursive: `namespace: io.kestra` also purges sub-namespaces such as \
        `io.kestra.team`. Omit `namespace` to purge across every namespace under the tenant. A sub-namespace \
        configured with its own dedicated storage is not reached by recursion — target it explicitly.

        Set `endDate` to (or before) your `PurgeExecutions` retention window so that only files belonging to \
        already-purged executions are deleted."""
)
@Plugin(
    examples = {
        @Example(
            title = "Delete execution storage files older than 7 days for a namespace.",
            full = true,
            code = """
                id: purge_storage
                namespace: system

                tasks:
                  - id: purge
                    type: io.kestra.plugin.core.storage.PurgeStorage
                    namespace: company.team
                    endDate: "{{ now() | dateAdd(-7, 'DAYS') }}"
                    dryRun: false
                """
        )
    },
    metrics = {
        @Metric(name = "scanned", type = Counter.TYPE, description = "Number of execution storage subtrees scanned."),
        @Metric(name = "purged", type = Counter.TYPE, description = "Number of execution storage subtrees matched (preview when `dryRun` is true)."),
        @Metric(name = "deleted.files", type = Counter.TYPE, description = "Number of storage objects deleted (always 0 when `dryRun` is true).")
    }
)
public class PurgeStorage extends Task implements RunnableTask<PurgeStorage.Output>, SystemTask {
    @Schema(
        title = "Namespace whose execution storage files should be purged.",
        description = "Recursive: sub-namespaces are also purged (e.g. `io.kestra` reaches `io.kestra.team`), except sub-namespaces with their own dedicated storage, which must be targeted explicitly. Omit to purge across every namespace under the tenant."
    )
    @PluginProperty(group = "main")
    private Property<String> namespace;

    @Schema(
        title = "The flow ID to be purged.",
        description = "Requires `namespace` to also be set."
    )
    @PluginProperty(group = "main")
    private Property<String> flowId;

    @Schema(
        title = "Only purge files last modified after this date."
    )
    @PluginProperty(group = "main")
    private Property<String> startDate;

    @Schema(
        title = "Only purge files last modified before this date.",
        description = "Set this to (or before) your `PurgeExecutions` retention window so only files of "
            + "already-purged executions are deleted."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> endDate;

    @Schema(
        title = "If true, only report what would be deleted without deleting anything."
    )
    @Builder.Default
    @PluginProperty(group = "reliability")
    private Property<Boolean> dryRun = Property.ofValue(true);

    @Override
    public PurgeStorage.Output run(RunContext runContext) throws Exception {
        StoragePurgeService storagePurgeService = ((DefaultRunContext) runContext).services().additionalService(StoragePurgeService.class);

        var flowInfo = runContext.flowInfo();
        String rNamespace = runContext.render(this.namespace).as(String.class).orElse(null);
        String rFlowId = runContext.render(this.flowId).as(String.class).orElse(null);
        ZonedDateTime rStartDate = runContext.render(this.startDate).as(String.class).map(ZonedDateTime::parse).orElse(null);
        ZonedDateTime rEndDate = ZonedDateTime.parse(runContext.render(this.endDate).as(String.class).orElseThrow());
        boolean rDryRun = runContext.render(this.dryRun).as(Boolean.class).orElseThrow();

        // Reject before the ACL check so the user sees the actual mistake, not an "all namespaces" denial.
        if (rFlowId != null && rNamespace == null) {
            throw new IllegalArgumentException("'namespace' is required when 'flowId' is set.");
        }

        if (rNamespace == null) {
            runContext.acl().allowAllNamespaces().check();
        } else if (!rNamespace.equals(flowInfo.namespace())) {
            runContext.acl().allowNamespace(rNamespace).check();
        }

        runContext.logger().info(
            "Starting PurgeStorage (dryRun={}) scope=[namespace={}, flowId={}] window=[{}, {}]",
            rDryRun, rNamespace, rFlowId, rStartDate, rEndDate
        );

        StoragePurgeService.StoragePurgeResult result = storagePurgeService.purgeByLastModified(
            flowInfo.tenantId(), rNamespace, rFlowId, rStartDate, rEndDate, rDryRun
        );

        runContext.logger().info(
            "PurgeStorage complete (dryRun={}): scanned={}, purged={}, deletedFiles={}",
            rDryRun, result.scannedCount(), result.purgedCount(), result.deletedFilesCount()
        );

        runContext.metric(Counter.of("scanned", result.scannedCount()));
        runContext.metric(Counter.of("purged", result.purgedCount()));
        runContext.metric(Counter.of("deleted.files", result.deletedFilesCount()));

        return Output.builder()
            .scannedCount(result.scannedCount())
            .purgedCount(result.purgedCount())
            .deletedFilesCount(result.deletedFilesCount())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of execution storage subtrees scanned.")
        private final int scannedCount;

        @Schema(title = "Number of executions with at least one file in the date window (preserved when `dryRun` is true).")
        private final int purgedCount;

        @Schema(title = "Number of storage objects deleted (always 0 when `dryRun` is true).")
        private final int deletedFilesCount;
    }
}
