package io.kestra.plugin.core.namespace;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Rethrow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.codehaus.commons.nullanalysis.NotNull;
import org.slf4j.Logger;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static io.kestra.core.runners.NamespaceFilesService.toNamespacedStorageUri;

@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete one or multiple files from your namespace files."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete namespace files.",
            full = true,
            code = {
                "id: namespace-file-delete",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                    "    - id: delete",
                    "    type: io.kestra.plugin.core.namespace.DeleteFiles",
                    "    namespace: tutorial",
                    "    files:",
                    "    - \\.upl"
            }
        )
    }
)
public class DeleteFiles extends Task implements RunnableTask<DeleteFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace where you want to apply the action."
    )
    private String namespace;

    @NotNull
    @Schema(
        title = "A list of files from the given namespace.",
        description = "Must be a specific uri for upload, but can be an uri or a regex for delete and download."
    )
    private List<String> files;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        // Check if namespace is allowed
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        FlowService flowService = runContext.getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(flowInfo.tenantId(), this.namespace, flowInfo.tenantId(), flowInfo.namespace());

        // Access to files
        StorageInterface storageInterface = runContext.getApplicationContext().getBean(StorageInterface.class);
        URI baseNamespaceFilesUri = toNamespacedStorageUri(namespace, null);

        List<Pattern> patterns = files.stream().map(Pattern::compile).toList();
        AtomicInteger count = new AtomicInteger();
        storageInterface.allByPrefix(flowInfo.tenantId(), baseNamespaceFilesUri, false).forEach(Rethrow.throwConsumer(uri -> {
            if (patterns.stream().anyMatch(p -> p.matcher(uri.getPath()).find())) {
                storageInterface.delete(flowInfo.tenantId(), uri);
                logger.debug(String.format("Deleted %s", uri));
                count.getAndIncrement();
            }
        }));

        runContext.metric(Counter.of("deleted", count.get()));
        return Output.builder().build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final Map<String, URI> files;
    }
}
