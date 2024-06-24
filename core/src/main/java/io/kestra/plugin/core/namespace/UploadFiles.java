package io.kestra.plugin.core.namespace;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.Namespace;
import io.kestra.core.utils.FileUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.codehaus.commons.nullanalysis.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.PathUtil.checkLeadingSlash;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Upload one or multiple files to a specific namespace.",
    description = "Use a regex glob pattern or a file path to upload files as Namespace Files. When using a map with the desired file name as key and file path as value, you can also rename or relocate files."
)
@Plugin(
    examples = {
        @Example(
            title = "Upload a custom Python script to the `dev` namespace and execute it.",
            full = true,
            code = """
id: upload_inputfile
namespace: company.team

inputs:
  - id: my_python_script
    type: FILE

tasks:
  - id: upload_and_rename
    type: io.kestra.plugin.core.namespace.UploadFiles
    files:
      /scripts/main.py: "{{ inputs.my_python_script }}"
    namespace: dev

  - id: python
    type: io.kestra.plugin.scripts.python.Commands
    namespace:
      enabled: true
    commands:
      - python scripts/main.py"""
        )
    }
)
public class UploadFiles extends Task implements RunnableTask<UploadFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace to which the files will be uploaded."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "A list of files.",
        description = "This can be a list of strings, where each string can be either a regex glob pattern or a file path. Providing a list requires specifying a `destination` where files will be stored.\n" +
                    "This can also be a map where you can provide a specific destination path for a URI, which can be useful if you need to rename a file or move it to a different folder.",
        anyOf = {List.class, Map.class}
    )
    @PluginProperty(dynamic = true)
    private Object files;

    @Schema(
        title = "The destination folder.",
        description = "Required when providing a list of files."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String destination = "/";

    @Builder.Default

    @Schema(
        title = "Which action to take when uploading a file that already exists.",
        description = "Can be one of the following OVERWRITE, ERROR or SKIP."
    )
    private Namespace.Conflicts conflict = Namespace.Conflicts.OVERWRITE;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public UploadFiles.Output run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(namespace);
        String renderedDestination = checkLeadingSlash(runContext.render(destination));

        final Namespace storageNamespace = runContext.storage().namespace(renderedNamespace);

        if (files instanceof List<?> filesList) {
            if (renderedDestination == null) {
                throw new RuntimeException("Destination must be set when providing a List for the `files` property.");
            }
            filesList = runContext.render((List<String>) filesList);

            final List<String> regexs = new ArrayList<>();

            for (Object file : filesList) {
                Optional<URI> uri = FileUtils.getURI(file.toString());
                // Immediately handle strings that are full URI
                if (uri.isPresent()) {
                    if (runContext.storage().isFileExist(uri.get())) {
                        Path targetFilePath = Path.of(renderedDestination, FileUtils.getFileName(uri.get()));
                        storageNamespace.putFile(targetFilePath, runContext.storage().getFile(uri.get()), conflict);
                    }
                    // else ignore
                } else {
                    regexs.add(file.toString());
                }
            }

            // Check for files in the current WORKING_DIR that match the expressions
            for (Path path : runContext.workingDir().findAllFilesMatching(regexs)) {
                File file = path.toFile();
                Path resolve = Paths.get("/").resolve(runContext.workingDir().path().relativize(file.toPath()));

                Path targetFilePath = Path.of(renderedDestination, resolve.toString());
                storageNamespace.putFile(targetFilePath, new FileInputStream(file), conflict);
            }
        } else if (files instanceof Map map) {
            // Using a Map for the `files` property, there must be only URI
            Map<String, Object> renderedMap = runContext.render((Map<String, Object>) map);
            for (Map.Entry<String, Object> entry : renderedMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key instanceof String targetFilePath && value instanceof String stringSourceFileURI) {
                    URI sourceFileURI = URI.create(stringSourceFileURI);
                    if (runContext.storage().isFileExist(sourceFileURI)) {
                        storageNamespace.putFile(Path.of(targetFilePath), runContext.storage().getFile(sourceFileURI), conflict);
                    }
                } else {
                    throw new IllegalArgumentException("files must be a List<String> or a Map<String, String>");
                }
            }
        }

        return Output.builder().build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final Map<String, URI> files;
    }

}
