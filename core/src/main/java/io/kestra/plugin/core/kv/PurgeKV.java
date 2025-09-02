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
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.utils.ListUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

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
public class PurgeKV extends Task implements RunnableTask<PurgeKV.Output> {

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
        title = "Delete only expired keys",
        description = "Defaults to true."
    )
    @Builder.Default
    private Property<Boolean> expiredOnly = Property.ofValue(true);

    @Schema(
        title = "Delete keys from child namespaces",
        description = "Defaults to true. This means that if you set `namespaces` to `company`, it will also delete keys from `company.team`, `company.data`, etc."
    )
    @Builder.Default
    private Property<Boolean> includeChildNamespaces = Property.ofValue(true);


    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> kvNamespaces = findNamespaces(runContext);
        boolean expired = runContext.render(expiredOnly).as(Boolean.class).orElse(true);
        String renderedKeyPattern = runContext.render(keyPattern).as(String.class).orElse(null);
        boolean keyFiltering = StringUtils.isNotBlank(renderedKeyPattern);
        AtomicLong count = new AtomicLong();
        for (String ns : kvNamespaces) {
            KVStore kvStore = runContext.namespaceKv(ns);
            List<KVEntry> kvEntries = new ArrayList<>();
            List<KVEntry> allKvEntries = kvStore.listAll();
            if (expired){
                Instant now = Instant.now();
                kvEntries.addAll(allKvEntries.stream()
                    .filter(kv -> kv.expirationDate().isBefore(now))
                    .toList());
            } else {
                kvEntries.addAll(allKvEntries);
            }
            List<String> keys = kvEntries.stream()
                .map(KVEntry::key)
                .filter(key -> {
                    if (keyFiltering) {
                        return FilenameUtils.wildcardMatch(key, renderedKeyPattern);
                    }
                    return true;
                })
                .toList();
            for (String key : keys) {
                kvStore.delete(key);
            }
            count.addAndGet(keys.size());
        }

        return Output.builder()
            .size(count.get())
            .build();
    }

    @VisibleForTesting
    protected List<String> findNamespaces(RunContext runContext) throws IllegalVariableEvaluationException {
        String tenantId = runContext.flowInfo().tenantId();
        String currentNamespace = runContext.flowInfo().namespace();
        FlowRepositoryInterface flowRepositoryInterface = ((DefaultRunContext) runContext)
            .getApplicationContext().getBean(FlowRepositoryInterface.class);
        List<String> distinctNamespaces = flowRepositoryInterface.findDistinctNamespace(tenantId);
        List<String> renderedNamespaces = runContext.render(namespaces).asList(String.class);
        String renderedNamespacePattern = runContext.render(namespacePattern).as(String.class).orElse(null);

        if (!ListUtils.isEmpty(renderedNamespaces) && StringUtils.isNotBlank(renderedNamespacePattern)) {
            throw new ValidationErrorException(List.of("Properties `namespaces` and `namespacePattern` can't be used at the same time — use one or the other."));
        }

        List<String> kvNamespaces = new ArrayList<>();
        if (StringUtils.isNotBlank(renderedNamespacePattern)) {
            kvNamespaces.addAll(distinctNamespaces.stream()
                .filter(ns -> FilenameUtils.wildcardMatch(ns, renderedNamespacePattern))
                .toList());
        } else if (!renderedNamespaces.isEmpty()) {
            if (runContext.render(includeChildNamespaces).as(Boolean.class).orElse(true)){
                kvNamespaces.addAll(distinctNamespaces.stream()
                    .filter(ns -> {
                        for (String renderedNamespace : renderedNamespaces) {
                            if (ns.startsWith(renderedNamespace)){
                                return true;
                            }
                        }
                        return false;
                    }).toList());
            } else {
                kvNamespaces.addAll(distinctNamespaces.stream()
                    .filter(ns -> {
                        for (String renderedNamespace : renderedNamespaces) {
                            if (ns.equals(renderedNamespace)){
                                return true;
                            }
                        }
                        return false;
                    }).toList());
            }
        } else {
            kvNamespaces.addAll(distinctNamespaces);
        }

        FlowService flowService = ((DefaultRunContext) runContext).getApplicationContext().getBean(FlowService.class);
        for (String ns : kvNamespaces) {
            flowService.checkAllowedNamespace(tenantId, ns, tenantId, currentNamespace);
        }
        return kvNamespaces;
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
