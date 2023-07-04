package io.kestra.core.tasks.storages;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.scripts.BashService;
import io.kestra.core.utils.ListUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

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
                    hello.txt: "Hello World\\n"
                    addresse.json: "{{ outputs.myTaskId.uri }}"
                - id: bash
                  type: io.kestra.core.tasks.scripts.Bash
                  commands:
                    - cat hello.txt
            """
    ),
    @Example(
        full = true,
        title = "Send local files to Kestra's internal storage",
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
                    - mkdir -p sub/dir
                    - echo "Hello from Bash" >> sub/dir/bash1.txt
                    - echo "Hello from Bash" >> sub/dir/bash2.txt
                - id: outputFiles
                  type: io.kestra.core.tasks.storages.LocalFiles
                  outputs:
                    - sub/**
            """
    )
})
public class LocalFiles extends Task implements RunnableTask<LocalFiles.LocalFilesOutput> {
    @Schema(title = "The files to create on the local filesystem")
    @PluginProperty(dynamic = true)
    private Object inputs;

    @Schema(
        title = "The files from the local filesystem to send to the internal storage",
        description = "must be a [Glob expression](https://en.wikipedia.org/wiki/Glob_(programming)) relative to current working directory, some examples: `my-dir/**`, `my-dir/*/**` or `my-dir/my-file.txt`"
    )
    @PluginProperty(dynamic = true)
    private List<String> outputs;

    @Override
    public LocalFilesOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        Map<String, String> inputFiles = this.inputs == null ? Map.of() : BashService.transformInputFiles(runContext, this.inputs);

        inputFiles
            .forEach(throwBiConsumer((fileName, input) -> {
                var file = new File(runContext.tempDir().toString(), fileName);
                if (file.exists()) {
                    throw new IllegalVariableEvaluationException("File '" + fileName + "' already exist!");
                }

                if (!file.getParentFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.getParentFile().mkdirs();
                }

                var fileContent = runContext.render(input);
                if (fileContent.startsWith("kestra://")) {
                    try (var is = runContext.uriToInputStream(URI.create(fileContent));
                         var out = new FileOutputStream(file)) {
                        IOUtils.copyLarge(is, out);
                    }
                } else {
                    Files.write(file.toPath(), fileContent.getBytes());
                }
            }));

        var outputFiles = ListUtils.emptyOnNull(outputs)
            .stream()
            .flatMap(throwFunction(output -> this.outputMatcher(runContext, output)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        logger.info("Provide {} input(s) and capture {} output(s).", inputFiles.size(), outputFiles.size());

        return LocalFilesOutput.builder()
            .uris(outputFiles)
            .build();
    }

    private Stream<AbstractMap.SimpleEntry<String, URI>> outputMatcher(RunContext runContext, String output) throws IllegalVariableEvaluationException, IOException {
        var glob = runContext.render(output);
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);

        try (Stream<Path> walk = Files.walk(runContext.tempDir())) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> pathMatcher.matches(runContext.tempDir().relativize(path)))
                .map(throwFunction(path -> new AbstractMap.SimpleEntry<>(
                    runContext.tempDir().relativize(path).toString(),
                    runContext.putTempFile(path.toFile())
                )))
                .toList()
                .stream();
        }
    }

    @Builder
    @Getter
    public static class LocalFilesOutput implements Output {
        @Schema(title = "The URI of the files that have been sent to the internal storage")
        private Map<String, URI> uris;
    }
}
