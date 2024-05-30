package io.kestra.plugin.core.namespace;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static io.kestra.core.runners.NamespaceFilesService.toNamespacedStorageUri;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Upload one or multiple files to your namespace files.",
    description = "Files can be upload using a regex or a specific path and can also be renamed."
)
@Plugin(
    examples = {
        @Example(
            title = "Upload namespace file using a Map as input files",
            full = true,
            code = {
                "id: namespace-file-upload",
                "namespace: io.kestra.tests",
                "",
                "inputs:",
                "    - id: file",
                "    type: FILE",
                "",
                "tasks:",
                    "    - id: upload-map",
                    "    type: io.kestra.plugin.core.namespace.UploadFiles",
                    "    files:",
                    "    \"/upload_map/input.txt\": \"{{ inputs.file }}\"",
                    "    namespace: io.kestra.tests"
            }
        ),
        @Example(
            title = "Upload namespace file using a List of String as input files",
            full = true,
            code = {
                "id: namespace-file-upload",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "    - id: upload-list",
                "    type: io.kestra.plugin.core.namespace.UploadFiles",
                "    destination: \"/upload_list\"",
                "    files:",
                "    - \"{{ inputs.file }}\"",
                "        - hell",
                "    namespace: tutorial"
            }
        )
    }
)
public class UploadFiles extends Task implements RunnableTask<UploadFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace where the file will be upload."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @Schema(
        title = "A list of files from the given namespace.",
        description = "Can be a list of String, containing both a regex or a URI. Providing a list required to specify a `destination`.\n" +
                    "Can also be a Map where you can give a specific destination path for a specific URI, useful if you need to rename a file.",
        anyOf = {List.class, Map.class}
    )
    @PluginProperty(dynamic = true)
    private Object files;

    @Schema(
        title = "The destination folder.",
        description = "Required when providing a list of files."
    )
    @PluginProperty(dynamic = true)
    private String destination;

    @Builder.Default

    @Schema(
        title = "Action to execute when uploading a file that already exists.",
        description = "Can be OVERWRITE, ERROR or SKIP."
    )
    private ConflictAction conflict = ConflictAction.OVERWRITE;

    @Override
    public UploadFiles.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(namespace);
        String renderedDestination = runContext.render(destination);
        // Check if namespace is allowed
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        FlowService flowService = runContext.getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(flowInfo.tenantId(), renderedNamespace, flowInfo.tenantId(), flowInfo.namespace());

        StorageInterface storageInterface = runContext.getApplicationContext().getBean(StorageInterface.class);

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
            List<Pattern> patterns = regexs.stream().map(Pattern::compile).toList();
            for (File file : Objects.requireNonNull(runContext.tempDir().toFile().listFiles())) {
                if (patterns.stream().anyMatch(p -> p.matcher(file.toURI().getPath()).find())) {
                    String newFilePath = buildPath(renderedDestination, file.getName());
                    storeNewFile(logger, runContext, storageInterface, flowInfo.tenantId(), newFilePath, new FileInputStream(file));
                }
            }
        } else if (files instanceof Map map) {
            // Using a Map for the `files` property, there must be only URI
            Map<String, Object> renderedMap = runContext.render((Map<String, Object>) map);
            renderedMap.forEach((key, value) -> {
                if (key instanceof String keyString && value instanceof String valueString) {
                    URI toUpload = URI.create(valueString);
                    if (storageInterface.exists(flowInfo.tenantId(), toUpload)) {
                        try {
                            storeNewFile(logger, runContext, storageInterface, flowInfo.tenantId(), keyString, storageInterface.get(flowInfo.tenantId(), toUpload));
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
        if (!destination.startsWith("/")) {
            return "/" + String.join("/", destination, filename);
        }
        return String.join("/", destination, filename);
    }

    private void storeNewFile(Logger logger, RunContext runContext, StorageInterface storageInterface, String tenantId, String filePath, InputStream fileContent) throws IOException, IllegalVariableEvaluationException {
        String renderedNamespace = runContext.render(namespace);
        URI newFileURI = toNamespacedStorageUri(
            renderedNamespace,
            URI.create(filePath)
        );
        if (!conflict.equals(ConflictAction.OVERWRITE) && storageInterface.exists(tenantId, newFileURI)) {
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
        logger.debug(String.format("File %s created", filePath));
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
