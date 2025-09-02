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
    title = "Delete every expired keys globally of for a specific namespace."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete every expired keys globally of for a specific namespace, with or without including child namespaces.",
            full = true,
            code = """
                id: purge_kv_store
                namespace: system

                tasks:
                  - id: purge_kv
                    type: io.kestra.plugin.core.kv.PurgeKV
                    description: it will remove the KV from the storage and from Kestra
                    expiredOnly: false # true by default
                    namespaces: # by default, it should purge all KV from all namespaces; otherwise only keys from an array of namespaces; can't be used with namespacePattern
                    namespacePattern: * # by default, it should purge all KV from all namespaces; otherwise lob-Pattern e.g. AI_*; can't be used with namespaces
                    includeChildNamespaces: true # default true
                    keyPattern: * # by default all, optionally specify Glob-Pattern e.g. AI_*
                """
        )
    }
)
public class PurgeKV extends Task implements RunnableTask<PurgeKV.Output> {

    @Schema(
        title = "Key pattern. Delete only key matching the blob pattern"
    )
    private Property<String> keyPattern;

    @Schema(
        title = "List of namespaces to delete key from"
    )
    private Property<List<String>> namespaces;

    @Schema(
        title = "Blob pattern fo the namespaces to delete key from"
    )
    private Property<String> namespacePattern;

    @Schema(
        title = "Delete only expired keys. Default true"
    )
    @Builder.Default
    private Property<Boolean> expiredOnly = Property.ofValue(true);

    @Schema(
        title = "Delete keys from child namespaces"
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
            throw new ValidationErrorException(List.of("Properties namespaces and namespaceRegex can't be used together."));
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
            title = "The number of keys purged."
        )
        private Long size;
    }
}
