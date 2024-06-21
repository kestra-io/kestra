package io.kestra.plugin.core.namespace;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.utils.PathMatcherPredicate;
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

@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete one or multiple files from your namespace files."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete namespace files that match a specific regex glob pattern.",
            full = true,
            code = {
                """
                id: delete_files
                namespace: company.team
                tasks:
                  - id: delete
                    type: io.kestra.plugin.core.namespace.DeleteFiles
                    namespace: tutorial
                    files:
                      - "**.upl"
                """
            }
        ),
        @Example(
            title = "Delete all namespace files from a specific namespace.",
            full = true,
            code = {
                """
                id: delete_all_files
                namespace: company.team
                tasks:
                  - id: delete
                    type: io.kestra.plugin.core.namespace.DeleteFiles
                    namespace: tutorial
                    files:
                      - "**"
                """
            }
        )
    }
)
public class DeleteFiles extends Task implements RunnableTask<DeleteFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace from which the files should be deleted."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "A file or a list of files from the given namespace.",
        description = "String or a list of strings; each string can either be a regex glob pattern or a file path URI.",
        anyOf = {List.class, String.class}
    )
    @PluginProperty(dynamic = true)
    private Object files;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(this.namespace);

        final Namespace namespace = runContext.storage().namespace(renderedNamespace);

        List<String> renderedFiles;
        if (files instanceof String filesString) {
            renderedFiles = List.of(runContext.render(filesString));
        } else if (files instanceof List<?> filesList) {
            renderedFiles = runContext.render((List<String>) filesList);
        } else {
            throw new IllegalArgumentException("Files must be a String or a list of String");
        }

        List<NamespaceFile> matched = namespace.findAllFilesMatching(PathMatcherPredicate.matches(renderedFiles));
        long count = matched
            .stream()
            .map(Rethrow.throwFunction(file -> {
                if (namespace.delete(file)) {
                    logger.debug(String.format("Deleted %s", (file.path())));
                    return true;
                }
                return false;
            }))
            .filter(Boolean::booleanValue)
            .count();

        runContext.metric(Counter.of("deleted", count));
        return Output.builder().build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final Map<String, URI> files;
    }
}
