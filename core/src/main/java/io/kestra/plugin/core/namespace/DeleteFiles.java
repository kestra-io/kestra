package io.kestra.plugin.core.namespace;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.NamespaceFilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Rethrow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.codehaus.commons.nullanalysis.NotNull;
import org.slf4j.Logger;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.kestra.core.utils.PathUtil.checkLeadingSlash;

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
                """
                id: namespace-file-delete
                namespace: io.kestra.tests
                tasks:
                    - id: delete
                      type: io.kestra.plugin.core.namespace.DeleteFiles
                      namespace: tutorial
                      files:
                      - **.upl
                """
            }
        ),
        @Example(
            title = "Delete all namespace files.",
            full = true,
            code = {
                """
                id: namespace-file-delete
                namespace: io.kestra.tests
                tasks:
                    - id: delete
                      type: io.kestra.plugin.core.namespace.DeleteFiles
                      namespace: tutorial
                      files:
                      - **"
                """
            }
        )
    }
)
public class DeleteFiles extends Task implements RunnableTask<DeleteFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace where you want to apply the action."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @NotEmpty
    @Schema(
        title = "A file or list of files from the given namespace.",
        description = "String or List of String, each string can either be a regex using glob pattern or a URI.",
        anyOf = {List.class, String.class}
    )
    @PluginProperty(dynamic = true)
    private Object files;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(namespace);
        // Check if namespace is allowed
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        FlowService flowService = runContext.getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(flowInfo.tenantId(), renderedNamespace, flowInfo.tenantId(), flowInfo.namespace());

        // Access to files
        NamespaceFilesService namespaceFilesService = runContext.getApplicationContext().getBean(NamespaceFilesService.class);

        List<String> renderedFiles;
        if (files instanceof String filesString) {
            renderedFiles = List.of(runContext.render(filesString));
        } else if (files instanceof List<?> filesList) {
            renderedFiles = runContext.render((List<String>) filesList);
        } else {
            throw new IllegalArgumentException("Files must be a String or a list of String");
        }

        List<PathMatcher> patterns = renderedFiles.stream().map(reg -> FileSystems.getDefault().getPathMatcher("glob:" + checkLeadingSlash(reg))).toList();
        AtomicInteger count = new AtomicInteger();

        namespaceFilesService.recursiveList(flowInfo.tenantId(), renderedNamespace, null).forEach(Rethrow.throwConsumer(uri -> {
            if (patterns.stream().anyMatch(p -> p.matches(Path.of(uri.getPath())))) {
                namespaceFilesService.delete(flowInfo.tenantId(), renderedNamespace, uri);
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
