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
import io.kestra.core.utils.PathMatcherPredicate;
import io.kestra.core.utils.Rethrow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(
    title = "Download one or multiple files from your namespace files.",
    description = "Use a regex glob pattern or a file path to download files from your namespace files. This can be useful to share code between projects and teams, which is located in different namespaces."
)
@Plugin(
    examples = {
        @Example(
            title = "Download a namespace file.",
            full = true,
            code = {
                """
                id: download_file
                namespace: company.team
                tasks:
                  - id: download
                    type: io.kestra.plugin.core.namespace.DownloadFiles
                    namespace: tutorial
                    files:
                      - "**input.txt"
                """
            }
        ),
        @Example(
            title = "Download all namespace files from a specific namespace.",
            full = true,
            code = {
                """
                id: download_all_files
                namespace: company.team
                tasks:
                  - id: download
                    type: io.kestra.plugin.core.namespace.DownloadFiles
                    namespace: tutorial
                    files:
                      - "**"
                """
            }
        )
    }
)
public class DownloadFiles extends Task implements RunnableTask<DownloadFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace from which you want to download files."
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
        title = "The folder where the downloaded files will be stored"
    )
    @Builder.Default
    private Property<String> destination = Property.of("");


    @Override
    @SuppressWarnings("unchecked")
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElseThrow();
        String renderedDestination = runContext.render(destination).as(String.class).orElseThrow();

        final Namespace namespace = runContext.storage().namespace(renderedNamespace);

        List<String> renderedFiles;
        if (files instanceof String filesString) {
            renderedFiles = List.of(runContext.render(filesString));
        } else if (files instanceof List<?> filesList) {
            renderedFiles = runContext.render((List<String>) filesList);
        } else {
            throw new IllegalArgumentException("The files property must be a string or a list of strings");
        }

        Map<String, URI> downloaded = namespace.findAllFilesMatching(PathMatcherPredicate.matches(renderedFiles))
            .stream()
            .map(Rethrow.throwFunction(file -> {
                try (InputStream is = runContext.storage().getFile(file.uri())) {
                    URI uri = runContext.storage().putFile(is, renderedDestination + file.path());
                    logger.debug(String.format("Downloaded %s", uri));
                    return new AbstractMap.SimpleEntry<>(file.path(true).toString(), uri);
                }
            }))
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        runContext.metric(Counter.of("downloaded", downloaded.size()));
        return Output.builder().files(downloaded).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Downloaded files.",
            description = "The task returns a map containing the file path as a key and the file URI as a value."
        )
        private final Map<String, URI> files;
    }

}
