package io.kestra.plugin.core.namespace;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.utils.PathMatcherPredicate;
import io.kestra.core.utils.Rethrow;
import io.kestra.plugin.core.namespace.DeleteFiles.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
public class DeleteFiles extends Task implements RunnableTask<Output> {
    @NotNull
    @Schema(
        title = "The namespace from which the files should be deleted."
    )
    private Property<String> namespace;

    @NotNull
    @Schema(
        title = "A file or a list of files from the given namespace.",
        description = "String or a list of strings; each string can either be a regex glob pattern or a file path URI.",
        anyOf = {List.class, String.class}
    )
    @PluginProperty(dynamic = true)
    private Object files;

    @Schema(
        title = "Whether to delete empty parent folders after deleting files.",
        description = "If true, parent folders that become empty after file deletion will also be removed.",
        defaultValue = "false"
    )
    @Builder.Default
    private Property<Boolean> deleteParentFolder = Property.of(false);

    @SuppressWarnings("unchecked")
    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElseThrow();

        final Namespace namespace = runContext.storage().namespace(renderedNamespace);

        List<String> renderedFiles;
        if (files instanceof String filesString) {
            renderedFiles = List.of(runContext.render(filesString));
        } else if (files instanceof List<?> filesList) {
            renderedFiles = runContext.render((List<String>) filesList);
        } else {
            throw new IllegalArgumentException("Files must be a String or a list of String");
        }

        var deleteParent = runContext.render(this.deleteParentFolder).as(Boolean.class).orElseThrow();

        List<NamespaceFile> matched = namespace.findAllFilesMatching(PathMatcherPredicate.matches(renderedFiles));
        Set<String> parentFolders = Boolean.TRUE.equals(deleteParent) ? new TreeSet<>() : null;
        long count = matched
            .stream()
            .map(Rethrow.throwFunction(file -> {
                if (namespace.delete(NamespaceFile.of(renderedNamespace, Path.of(file.path().replace("\\","/"))).storagePath())) {
                    logger.debug(String.format("Deleted %s", (file.path())));

                    if (Boolean.TRUE.equals(deleteParent)) {
                        trackParentFolder(file, parentFolders);
                    }
                    return true;
                }
                return false;
            }))
            .filter(Boolean::booleanValue)
            .count();

        // Handle folder deletion if enabled
        if (parentFolders != null && !parentFolders.isEmpty()) {
            deleteEmptyFolders(namespace, parentFolders, logger);
        }

        runContext.metric(Counter.of("deleted", count));
        return Output.builder().build();
    }

    private void deleteEmptyFolders(Namespace namespace, Set<String> folders, Logger logger) {
        folders.stream()
            .sorted((a, b) -> b.split("/").length - a.split("/").length)
            .forEach(folderPath -> {
                try {
                    if (namespace.isDirectoryEmpty(folderPath)) {
                        // Create proper NamespaceFile for folder with trailing slash
                        NamespaceFile folder = NamespaceFile.of(
                            namespace.namespace(),
                            URI.create(folderPath + "/")
                        );

                        if (namespace.deleteDirectory(folder)) {
                            logger.debug("Deleted empty folder: {}", folderPath);
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Failed to delete folder: " + folderPath, e);
                }
            });
    }

    private void trackParentFolder(NamespaceFile file, Set<String> parentFolders) {
        String path = file.path();
        int lastSlash = path.lastIndexOf('/');
        while (lastSlash > 0) {
            path = path.substring(0, lastSlash);
            parentFolders.add(path);
            lastSlash = path.lastIndexOf('/');
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final Map<String, URI> files;
    }
}
