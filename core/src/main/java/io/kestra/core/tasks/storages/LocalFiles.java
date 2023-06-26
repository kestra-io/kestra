package io.kestra.core.tasks.storages;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Allow to create files in the local filesystem or to send files from the local filesystem to the internal storage.",
    description = "This task should be used with the WorkingDirectory task to be able to access the same local filesystem within multiple tasks."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a local file that will be accessible to a bash task",
        code = """
            id: "local-files"
            namespace: "io.kestra.tests"

            tasks:
              - id: workingDir
                type: io.kestra.core.tasks.flows.WorkingDirectory
                tasks:
                - id: inputFiles
                  type: io.kestra.core.tasks.storages.LocalFiles
                  inputs:
                    - name: hello.txt
                      content : "Hello World\\n"
                - id: bash
                  type: io.kestra.core.tasks.scripts.Bash
                  commands:
                    - cat hello.txt
            """
    ),
    @Example(
        full = true,
        title = "Send a local file to Kestra's internal storage",
        code = """
            id: "local-files"
            namespace: "io.kestra.tests"

            tasks:
              - id: workingDir
                type: io.kestra.core.tasks.flows.WorkingDirectory
                tasks:
                - id: bash
                  type: io.kestra.core.tasks.scripts.Bash
                  commands:
                    - echo "Hello from Bash" >> bash.txt
                - id: outputFiles
                  type: io.kestra.core.tasks.storages.LocalFiles
                  outputs:
                    - bash.txt
            """
    )
})
public class LocalFiles extends Task implements RunnableTask<LocalFiles.LocalFilesOutput> {

    @Schema(title = "The files to create on the local filesystem")
    @PluginProperty(dynamic = true)
    @Builder.Default
    private List<LocalFile> inputs = Collections.emptyList();

    @Schema(title = "The files from the local filesystem to send to the internal storage")
    @PluginProperty(dynamic = true)
    @Builder.Default
    private List<String> outputs =  Collections.emptyList();

    @Override
    public LocalFilesOutput run(RunContext runContext) throws Exception {
        for (var input: inputs) {
            var fileName = input.getName();
            var file = new File(runContext.tempDir().toString(), fileName);
            if (file.exists()) {
               throw new IllegalVariableEvaluationException("File '" + fileName + "' already exist!");
            }

            var fileContent = runContext.render(input.getContent());
            if (fileContent.startsWith("kestra://")) {
                try (var is = runContext.uriToInputStream(URI.create(fileContent));
                     var out = new FileOutputStream(file)) {
                    IOUtils.copyLarge(is, out);
                }
            } else {
                Files.write(file.toPath(), fileContent.getBytes());
            }

            if (input.getExecutable()) {
                if (!file.setExecutable(true)) {
                    runContext.logger().warn("Unable to set executable the file '" + input.getName() + "'");
                }
            }
        }

        List<URI> outputFiles = new ArrayList<>();
        for (var output: outputs) {
            var fileName = runContext.render(output);
            var file = new File(runContext.tempDir().toString(), fileName);
            if (!file.exists()) {
                throw new IllegalVariableEvaluationException("Output file '" + fileName + "' didn't exist");
            }
            var outputUri = runContext.putTempFile(file);
            outputFiles.add(outputUri);
        }
        return LocalFilesOutput.builder().outputFiles(outputFiles).build();
    }

    @Builder
    @Getter
    public static class LocalFilesOutput implements Output {
        @Schema(title = "The URI of the files that have been sent to the internal storage")
        private List<URI> outputFiles;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    public static class LocalFile {
        @Schema(title = "The name of the local file")
        @PluginProperty(dynamic = true)
        @NotNull
        private String name;

        @Schema(title = "The content of the local file")
        @PluginProperty(dynamic = true)
        @NotNull
        private String content;

        @Schema(title = "If the local file should be set as executable")
        @PluginProperty
        @Builder.Default
        private Boolean executable = false;
    }
}
