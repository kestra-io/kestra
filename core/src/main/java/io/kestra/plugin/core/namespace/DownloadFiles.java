package io.kestra.plugin.core.namespace;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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
import lombok.extern.slf4j.Slf4j;
import org.codehaus.commons.nullanalysis.NotNull;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.kestra.core.runners.NamespaceFilesService.toNamespacedStorageUri;

@Slf4j
@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(
    title = "Download one or multiple files from your namespace files."
)
@Plugin(
    examples = {
        @Example(
            title = "Download namespace files.",
            full = true,
            code = {
                "id: namespace-file-download",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "    - id: download",
                "    type: io.kestra.plugin.core.namespace.DownloadFiles",
                "    namespace: tutorial",
                "    files:",
                "    - input.txt"
            }
        )
    }
)
public class DownloadFiles extends Task implements RunnableTask<DownloadFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace where you want to apply the action."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "A list of files from the given namespace.",
        description = "Must be a specific uri for upload, but can be an uri or a regex for delete and download."
    )
    @PluginProperty(dynamic = true)
    private List<String> files;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(namespace);
        // Check if namespace is allowed
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        FlowService flowService = runContext.getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(flowInfo.tenantId(), renderedNamespace, flowInfo.tenantId(), flowInfo.namespace());

        StorageInterface storageInterface = runContext.getApplicationContext().getBean(StorageInterface.class);
        URI baseNamespaceFilesUri = toNamespacedStorageUri(renderedNamespace, null);

        List<Pattern> patterns = files.stream().map(Pattern::compile).toList();

        Map<String, URI> downloaded = new HashMap<>();

        storageInterface.allByPrefix(flowInfo.tenantId(), baseNamespaceFilesUri, false).forEach(Rethrow.throwConsumer(uri -> {
            if (patterns.stream().anyMatch(p -> p.matcher(uri.getPath()).find())) {
                try (InputStream inputStream = storageInterface.get(flowInfo.tenantId(), uri)) {
                    String filename = uri.toString().split("/_files/")[1];
                    downloaded.put(filename, runContext.storage().putFile(inputStream, filename));
                    logger.debug(String.format("Downloaded %s", filename));
                }
            }
        }));
        runContext.metric(Counter.of("downloaded", downloaded.size()));
        return Output.builder().files(downloaded).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final Map<String, URI> files;
    }

}
