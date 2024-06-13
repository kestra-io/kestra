package io.kestra.plugin.core.namespace;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.StorageInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.codehaus.commons.nullanalysis.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.kestra.core.runners.NamespaceFilesService.toNamespacedStorageUri;
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
            title = "Upload a custom Python script to the `dev` namespace and execute it",
            full = true,
            code = """
id: upload_inputfile
namespace: dev

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
    namespaceFiles:
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
    private ConflictAction conflict = ConflictAction.OVERWRITE;

    @Override
    public UploadFiles.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(namespace);
        String renderedDestination = runContext.render(destination);
        // Check if namespace is allowed
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        FlowService flowService = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(flowInfo.tenantId(), renderedNamespace, flowInfo.tenantId(), flowInfo.namespace());

        StorageInterface storageInterface = ((DefaultRunContext)runContext).getApplicationContext().getBean(StorageInterface.class);

        if (files instanceof List filesList) {
            if (renderedDestination == null) {
                throw new RuntimeException("Destination must be set when providing a List for the `files` property.");
            }
            filesList = runContext.render((List<String>) filesList);

            final List<String> regexs = new ArrayList<>();

            // First handle string that are full URI
            ((List<String>) filesList).forEach(path -> {
                if (storageInterface.exists(flowInfo.tenantId(), URI.create(path))) {
                    String newFilePath = null;
                    try {
                        newFilePath = buildPath(renderedDestination, storageInterface.getAttributes(flowInfo.tenantId(), URI.create(path)).getFileName());
                        storeNewFile(logger, runContext, storageInterface, flowInfo.tenantId(), newFilePath, storageInterface.get(flowInfo.tenantId(), URI.create(path)));
                    } catch (IOException | IllegalVariableEvaluationException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    regexs.add(path);
                }
            });

            // check for file in current tempDir that match regexs
            for (Path path : runContext.workingDir().findAllFilesMatching(regexs)) {
                File file = path.toFile();
                String newFilePath = buildPath(renderedDestination, file.getPath().replace(runContext.workingDir().path().toString(), ""));
                storeNewFile(logger, runContext, storageInterface, flowInfo.tenantId(), newFilePath, new FileInputStream(file));
            }
        } else if (files instanceof Map map) {
            // Using a Map for the `files` property, there must be only URI
            Map<String, Object> renderedMap = runContext.render((Map<String, Object>) map);
            renderedMap.forEach((key, value) -> {
                if (key instanceof String keyString && value instanceof String valueString) {
                    URI toUpload = URI.create(valueString);
                    if (storageInterface.exists(flowInfo.tenantId(), toUpload)) {
                        try {
                            storeNewFile(logger, runContext, storageInterface, flowInfo.tenantId(), checkLeadingSlash(keyString), storageInterface.get(flowInfo.tenantId(), toUpload));
                        } catch (IOException | IllegalVariableEvaluationException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    throw new IllegalArgumentException("files must be a List<String> or a Map<String, String>");
                }
            });
        }

        return Output.builder().build();
    }

    private String buildPath(String destination, String filename) {
        return checkLeadingSlash(String.join("/", destination, filename));
    }

    private void storeNewFile(Logger logger, RunContext runContext, StorageInterface storageInterface, String tenantId, String filePath, InputStream fileContent) throws IOException, IllegalVariableEvaluationException {
        String renderedNamespace = runContext.render(namespace);
        URI newFileURI = toNamespacedStorageUri(
            renderedNamespace,
            URI.create(filePath)
        );

        boolean fileExists = storageInterface.exists(tenantId, newFileURI);
        if (!conflict.equals(ConflictAction.OVERWRITE) && fileExists) {
            if (conflict.equals(ConflictAction.ERROR)) {
                throw new IOException(String.format("File %s already exists and conflict is set to %s", filePath, ConflictAction.ERROR));
            }
            logger.debug(String.format("File %s already exists and conflict is set to %s, skipping", filePath, ConflictAction.ERROR));
            return;
        }

        storageInterface.put(
            tenantId,
            newFileURI,
            fileContent
        );
        if (fileExists) {
            logger.debug(String.format("File %s overwritten", filePath));
        } else {
            logger.debug(String.format("File %s created", filePath));
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final Map<String, URI> files;
    }

    public enum ConflictAction {
        OVERWRITE,
        ERROR,
        SKIP
    }

}
