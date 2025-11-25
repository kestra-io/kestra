package io.kestra.plugin.core.namespace;


import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.plugin.core.purge.PurgeTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete expired keys globally for a specific namespace.",
    description = "This task will delete expired keys from the Kestra KV store. By default, it will only delete expired keys, but you can choose to delete all keys by setting `expiredOnly` to false. You can also filter keys by a specific pattern and choose to include child namespaces."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete expired keys globally for a specific namespace, with or without including child namespaces.",
            full = true,
            code = """
                id: purge_kv_store
                namespace: system
                
                tasks:
                  - id: purge_kv
                    type: io.kestra.plugin.core.kv.PurgeKV
                    expiredOnly: true
                    namespaces:
                      - company
                    includeChildNamespaces: true
                """
        )
    }
)
public class PurgeFiles extends Task implements PurgeTask<NamespaceFile>, RunnableTask<PurgeFiles.Output> {
    @Schema(
        title = "File pattern, e.g. 'AI_*'",
        description = "Delete only files whose path is matching the glob pattern."
    )
    private Property<String> filePattern;

    @Schema(
        title = "List of namespaces to delete files from",
        description = "If not set, all namespaces will be considered. Can't be used with `namespacePattern` - use one or the other."
    )
    private Property<List<String>> namespaces;

    @Schema(
        title = "Glob pattern for the namespaces to delete files from",
        description = "If not set (e.g., AI_*), all namespaces will be considered. Can't be used with `namespaces` - use one or the other."
    )
    private Property<String> namespacePattern;

    @Schema(
        title = "Purge behavior",
        description = "Defines how files are purged."
    )
    @Builder.Default
    @Valid
    private Property<FilesPurgeBehavior> behavior = Property.ofValue(Version.builder().keepAmount(1).build());

    @Schema(
        title = "Delete files from child namespaces",
        description = "Defaults to true. This means that if you set `namespaces` to `company`, it will also delete files from `company.team`, `company.data`, etc."
    )
    @Builder.Default
    private Property<Boolean> includeChildNamespaces = Property.ofValue(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> filesNamespaces = findNamespaces(runContext);
        runContext.logger().info("purging {} namespaces: {}", filesNamespaces.size(), filesNamespaces);
        AtomicLong count = new AtomicLong();
        FilesPurgeBehavior renderedBehavior = runContext.render(behavior).as(FilesPurgeBehavior.class).orElseThrow();
        for (String ns : filesNamespaces) {
            Namespace namespaceStorage = runContext.storage().namespace(ns);
            List<NamespaceFile> toPurge = filterItems(runContext, renderedBehavior.entriesToPurge(runContext.flowInfo().tenantId(), namespaceStorage));
            count.addAndGet(namespaceStorage.purge(toPurge));
        }
        runContext.logger().info("purged {} files", count.get());

        return Output.builder()
            .size(count.get())
            .build();
    }

    @Override
    public Property<String> filterPattern() {
        return filePattern;
    }

    @Override
    public String filterTargetExtractor(NamespaceFile item) {
        return item.path();
    }


    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The number of purged KV pairs"
        )
        private Long size;
    }
}
