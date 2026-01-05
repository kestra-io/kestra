package io.kestra.plugin.core.kv;

import com.cronutils.utils.VisibleForTesting;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.purge.PurgeTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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
public class PurgeKV extends Task implements PurgeTask<KVEntry>, RunnableTask<PurgeKV.Output> {
    @Schema(
        title = "Key pattern, e.g. 'AI_*'",
        description = "Delete only keys matching the glob pattern."
    )
    private Property<String> keyPattern;

    @Schema(
        title = "List of namespaces to delete keys from",
        description = "If not set, all namespaces will be considered. Can't be used with `namespacePattern` - use one or the other."
    )
    private Property<List<String>> namespaces;

    @Schema(
        title = "Glob pattern for the namespaces to delete keys from",
        description = "If not set (e.g., AI_*), all namespaces will be considered. Can't be used with `namespaces` - use one or the other."
    )
    private Property<String> namespacePattern;

    @Schema(
        title = "Purge behavior",
        description = "Defines how keys are purged."
    )
    @Builder.Default
    @Valid
    private Property<KvPurgeBehavior> behavior = Property.ofValue(Key.builder().expiredOnly(true).build());

    @Schema(
        title = "Delete keys from child namespaces",
        description = "Defaults to true. This means that if you set `namespaces` to `company`, it will also delete keys from `company.team`, `company.data`, etc."
    )
    @Builder.Default
    private Property<Boolean> includeChildNamespaces = Property.ofValue(true);

    /**
     * @deprecated use behavior.type: key + behavior.expiredOnly instead. Setting this property will override the `behavior` property.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    private Property<Boolean> expiredOnly;

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> kvNamespaces = findNamespaces(runContext);
        String renderedKeyPattern = runContext.render(keyPattern).as(String.class).orElse(null);
        boolean keyFiltering = StringUtils.isNotBlank(renderedKeyPattern);
        runContext.logger().info("purging {} namespaces: {}", kvNamespaces.size(), kvNamespaces);
        AtomicLong count = new AtomicLong();
        KvPurgeBehavior renderedBehavior;
        if (expiredOnly != null) {
            renderedBehavior = Key.builder()
                .expiredOnly(runContext.render(expiredOnly).as(Boolean.class).orElse(true))
                .build();
        } else {
            renderedBehavior = runContext.render(behavior).as(KvPurgeBehavior.class).orElseThrow();
        }
        for (String ns : kvNamespaces) {
            KVStore kvStore = runContext.namespaceKv(ns);
            List<KVEntry> toPurge = filterItems(runContext, renderedBehavior.entriesToPurge(kvStore));
            count.addAndGet(kvStore.purge(toPurge));
        }
        runContext.logger().info("purged {} keys", count.get());

        return Output.builder()
            .size(count.get())
            .build();
    }

    @Override
    public Property<String> filterPattern() {
        return keyPattern;
    }

    @Override
    public String filterTargetExtractor(KVEntry item) {
        return item.key();
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
