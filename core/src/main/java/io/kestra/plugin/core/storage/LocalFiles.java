package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "This task is deprecated and replaced by `inputFiles` property available in all script tasks and in the [WorkingDirectory](https://kestra.io/plugins/core/tasks/io.kestra.plugin.core.flow.workingdirectory) task. Check the [migration guide](https://kestra.io/docs/migration-guide/0.17.0/local-files) for more details. ",
    description = "This task was intended to be used along with the `WorkingDirectory` task to create temporary files. This task suffers from multiple limitations e.g. it cannot be skipped, so setting `disabled: true` will have no effect. Overall, the WorkingDirectory task is more flexible and should be used instead of this task. This task will be removed in a future version of Kestra."
)
@Deprecated
@Plugin(examples = {
    @Example(
        full = true,
        title = "Output local files created in a Python task and load them to S3.",
        code = """
                id: outputs_from_python_task
                namespace: company.team

                tasks:
                  - id: wdir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: clone_repository
                        type: io.kestra.plugin.git.Clone
                        url: https://github.com/kestra-io/examples
                        branch: main

                      - id: git_python_scripts
                        type: io.kestra.plugin.scripts.python.Commands
                        warningOnStdErr: false
                        runner: DOCKER
                        docker:
                          image: ghcr.io/kestra-io/pydata:latest
                        beforeCommands:
                          - pip install faker > /dev/null
                        commands:
                          - python examples/scripts/etl_script.py
                          - python examples/scripts/generate_orders.py

                      - id: export_files
                        type: io.kestra.plugin.core.storage.LocalFiles
                        outputs:
                          - orders.csv
                          - "*.parquet"

                  - id: load_csv_to_s3
                    type: io.kestra.plugin.aws.s3.Upload
                    accessKeyId: "{{ secret('AWS_ACCESS_KEY_ID') }}"
                    secretKeyId: "{{ secret('AWS_SECRET_ACCESS_KEY') }}"
                    region: eu-central-1
                    bucket: kestraio
                    key: stage/orders.csv
                    from: "{{ outputs.export_files.outputFiles['orders.csv'] }}"
                    disabled: true
            """
    ),
    @Example(
        full = true,
        title = "Create a local file that will be accessible to a bash task.",
        code = """
            id: "local_files"
            namespace: "io.kestra.tests"

            tasks:
              - id: working_dir
                type: io.kestra.plugin.core.flow.WorkingDirectory
                tasks:
                - id: input_files
                  type: io.kestra.plugin.core.storage.LocalFiles
                  inputs:
                    hello.txt: "Hello World\\n"
                    address.json: "{{ outputs.my_task_id.uri }}"
                - id: bash
                  type: io.kestra.plugin.scripts.shell.Commands
                  commands:
                    - cat hello.txt
            """
    ),
    @Example(
        full = true,
        title = "Send local files to Kestra's internal storage.",
        code = """
            id: "local_files"
            namespace: "io.kestra.tests"

            tasks:
              - id: working_dir
                type: io.kestra.plugin.core.flow.WorkingDirectory
                tasks:
                - id: bash
                  type: io.kestra.plugin.scripts.shell.Commands
                  commands:
                    - mkdir -p sub/dir
                    - echo "Hello from Bash" >> sub/dir/bash1.txt
                    - echo "Hello from Bash" >> sub/dir/bash2.txt
                - id: output_files
                  type: io.kestra.plugin.core.storage.LocalFiles
                  outputs:
                    - sub/**
            """
    )
},
    aliases = "io.kestra.core.tasks.storages.LocalFiles"
)
public class LocalFiles extends Task implements RunnableTask<LocalFiles.LocalFilesOutput> {
    @Schema(
        title = "The files to be created on the local filesystem. It can be a map or a JSON object.",
        oneOf = { Map.class, String.class }
    )
    @PluginProperty(dynamic = true)
    private Object inputs;

    @Schema(
        title = "The files from the local filesystem to be sent to the Kestra's internal storage.",
        description = "Must be a list of [glob](https://en.wikipedia.org/wiki/Glob_(programming)) expressions relative to the current working directory, some examples: `my-dir/**`, `my-dir/*/**` or `my-dir/my-file.txt`."
    )
    private Property<List<String>> outputs;

    @Override
    public LocalFilesOutput run(RunContext runContext) throws Exception {
        FilesService.inputFiles(runContext, this.inputs);
        Map<String, URI> outputFiles = FilesService.outputFiles(runContext, runContext.render(this.outputs).asList(String.class));

        return LocalFilesOutput.builder()
            .uris(outputFiles)
            .build();
    }

    @Builder
    @Getter
    public static class LocalFilesOutput implements Output {
        @Schema(title = "The URI of the files that have been sent to the Kestra's internal storage.")
        private Map<String, URI> uris;
    }
}
