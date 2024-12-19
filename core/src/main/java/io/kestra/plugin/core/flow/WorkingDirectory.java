package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.InputFilesInterface;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.models.tasks.NamespaceFilesInterface;
import io.kestra.core.models.tasks.OutputFilesInterface;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Rethrow;
import io.kestra.core.validations.WorkingDirectoryTaskValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import jakarta.validation.constraints.NotNull;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run tasks sequentially in the same working directory.",
    description = "Tasks are stateless by default. Kestra will launch each task within a temporary working directory on a Worker. " +
        "The `WorkingDirectory` task allows reusing the same file system's working directory across multiple tasks " +
        "so that multiple sequential tasks can use output files from previous tasks without having to use the `outputs.taskId.outputName` syntax. " +
        "Note that the `WorkingDirectory` only works with runnable tasks because those tasks are executed directly on the Worker. " +
        "This means that using flowable tasks such as the `Parallel` task within the `WorkingDirectory` task will not work. "
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Clone a Git repository into the Working Directory and run a Python script in a Docker container.",
            code = """
                id: git_python
                namespace: company.team

                tasks:
                  - id: wdir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: clone_repository
                        type: io.kestra.plugin.git.Clone
                        url: https://github.com/kestra-io/examples
                        branch: main

                      - id: python
                        type: io.kestra.plugin.scripts.python.Commands
                        taskRunner:
                          type: io.kestra.plugin.scripts.runner.docker.Docker
                        containerImage: ghcr.io/kestra-io/pydata:latest
                        commands:
                          - python scripts/etl_script.py
                """
        ),
        @Example(
            full = true,
            title = "Add input and output files within a Working Directory to use them in a Python script.",
            code = """
                id: api_json_to_mongodb
                namespace: company.team

                tasks:
                  - id: wdir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    outputFiles:
                      - output.json
                    inputFiles:
                      query.sql: |
                        SELECT sum(total) as total, avg(quantity) as avg_quantity
                        FROM sales;
                    tasks:
                      - id: inline_script
                        type: io.kestra.plugin.scripts.python.Script
                        taskRunner:
                          type: io.kestra.plugin.scripts.runner.docker.Docker
                        containerImage: python:3.11-slim
                        beforeCommands:
                          - pip install requests kestra > /dev/null
                        warningOnStdErr: false
                        script: |
                          import requests
                          import json
                          from kestra import Kestra

                          with open('query.sql', 'r') as input_file:
                              sql = input_file.read()

                          response = requests.get('https://api.github.com')
                          data = response.json()

                          with open('output.json', 'w') as output_file:
                              json.dump(data, output_file)

                          Kestra.outputs({'receivedSQL': sql, 'status': response.status_code})

                  - id: load_to_mongodb
                    type: io.kestra.plugin.mongodb.Load
                    connection:
                      uri: mongodb://host.docker.internal:27017/
                    database: local
                    collection: github
                    from: "{{ outputs.wdir.uris['output.json'] }}"
            """
        ),
        @Example(
            full = true,
            code = """
                id: working_directory
                namespace: company.team

                tasks:
                  - id: working_directory
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: first
                        type: io.kestra.plugin.scripts.shell.Commands
                        commands:
                        - 'echo "{{ taskrun.id }}" > {{ workingDir }}/stay.txt'
                      - id: second
                        type: io.kestra.plugin.scripts.shell.Commands
                        commands:
                        - |
                          echo '::{"outputs": {"stay":"'$(cat {{ workingDir }}/stay.txt)'"}}::''
                """
        ),
        @Example(
            full = true,
            title = "A working directory with a cache of the node_modules directory.",
            code = """
                id: node_with_cache
                namespace: company.team

                tasks:
                  - id: working_dir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    cache:
                      patterns:
                        - node_modules/**
                      ttl: PT1H
                    tasks:
                      - id: script
                        type: io.kestra.plugin.scripts.node.Script
                        beforeCommands:
                          - npm install colors
                        script: |
                          const colors = require("colors");
                          console.log(colors.red("Hello"));
                """
        )
    },
    aliases = {"io.kestra.core.tasks.flows.WorkingDirectory", "io.kestra.core.tasks.flows.Worker"}
)
@WorkingDirectoryTaskValidation
public class WorkingDirectory extends Sequential implements NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {

    private static final String OUTPUTS_FILE = "outputs.ion";

    @Schema(
        title = "Cache configuration.",
        description = """
            When a cache is configured, an archive of the files denoted by the cache configuration is created at the end of the execution of the task and saved in Kestra's internal storage.
            Then at the beginning of the next execution of the task, the archive of the files is retrieved and the working directory initialized with it.
            """
    )
    @PluginProperty
    private Cache cache;

    private NamespaceFiles namespaceFiles;

    @Getter(AccessLevel.PRIVATE)
    @Builder.Default
    private transient long cacheDownloadedTime = 0L;

    private Object inputFiles;

    private Property<List<String>> outputFiles;

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        if (execution.hasFailed(childTasks, parentTaskRun)) {
            return super.resolveNexts(runContext, execution, parentTaskRun);
        }

        // resolve to no next tasks as the worker will execute all tasks
        return Collections.emptyList();
    }

    public WorkerTask workerTask(TaskRun parent, Task task, RunContext runContext) {
        return WorkerTask.builder()
            .task(task)
            .taskRun(TaskRun.builder()
                .id(IdUtils.create())
                .tenantId(parent.getTenantId())
                .executionId(parent.getExecutionId())
                .namespace(parent.getNamespace())
                .flowId(parent.getFlowId())
                .taskId(task.getId())
                .parentTaskRunId(parent.getId())
                .state(new State())
                .build()
            )
            .runContext(runContext)
            .build();
    }

    public void preExecuteTasks(RunContext runContext, TaskRun taskRun) throws Exception {
        if (cache != null) {
            // May download cached file if it exists and is not expired, and extract its content
            var maybeCacheFile = runContext.storage().getCacheFile(getId(), taskRun.getValue(), cache.ttl);
            if (maybeCacheFile.isPresent()) {
                runContext.logger().debug("Cache exist, downloading it");
                // download the cache if exist and unzip all entries
                try (ZipInputStream archive = new ZipInputStream(maybeCacheFile.get())) {
                    ZipEntry entry;
                    while ((entry = archive.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            try {
                                Path file = runContext.workingDir().path().resolve(entry.getName());
                                Files.createDirectories(file.getParent());
                                Files.createFile(file);
                                Files.write(file, archive.readAllBytes());
                            } catch (IOException e) {
                                runContext.logger().error("Unable to create the file {}", entry.getName(), e);
                            }
                        }
                    }
                }

                // Set the cacheDownloadedTime so that we can check if files has been updated later
                cacheDownloadedTime = System.currentTimeMillis();
            }
        }

        if (this.namespaceFiles != null && !Boolean.FALSE.equals(runContext.render(this.namespaceFiles.getEnabled()).as(Boolean.class).orElse(true))) {
            runContext.storage()
                .namespace()
                .findAllFilesMatching(
                    runContext.render(this.namespaceFiles.getInclude()).asList(String.class),
                    runContext.render(this.namespaceFiles.getExclude()).asList(String.class)
                )
                .forEach(Rethrow.throwConsumer(namespaceFile -> {
                    InputStream content = runContext.storage().getFile(namespaceFile.uri());
                    runContext.workingDir().putFile(Path.of(namespaceFile.path()), content);
                }));
        }

        if (this.inputFiles != null) {
           FilesService.inputFiles(runContext, Map.of(), this.inputFiles);
        }
    }

    public void postExecuteTasks(RunContext runContext, TaskRun taskRun) throws Exception {
        if (this.outputFiles != null) {
            try {
                Map<String, URI> outputFilesURIs = FilesService.outputFiles(runContext, runContext.render(this.outputFiles).asList(String.class));
                if (!outputFilesURIs.isEmpty()) {
                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try (os) {
                        FileSerde.write(os, outputFilesURIs);
                    }
                    runContext.storage().putFile(new ByteArrayInputStream(os.toByteArray()), OUTPUTS_FILE);
                }
            } catch (Exception e) {
                runContext.logger().error("Unable to capture WorkingDirectory output files", e);
                throw e;
            }
        }

        if (cache == null) {
            return;
        }
        try {
            // This is monolithic, maybe a cache entry by pattern would be better.
            List<Path> matchesList = runContext.workingDir().findAllFilesMatching(cache.getPatterns());

            // Check that some files has been updated since the start of the task
            // TODO we may need to allow excluding files as some files always changed for dependencies (for ex .package-log.json)
            boolean cacheFilesAreUpdated = matchesList.stream()
                .anyMatch(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis() > cacheDownloadedTime;
                    } catch (IOException e) {
                        runContext.logger().warn("Unable to retrieve files last modified time,  will update the cache anyway", e);
                        return true;
                    }
                });

            if (cacheFilesAreUpdated) {
                runContext.logger().debug("Cache files changed, we update the cache");
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                     ZipOutputStream archive = new ZipOutputStream(bos)) {

                    for (var path : matchesList) {
                        File file = path.toFile();
                        if (file.isDirectory() || !file.canRead()) {
                            continue;
                        }

                        var relativeFileName = file.getPath().substring(runContext.workingDir().path().toString().length() + 1);
                        var zipEntry = new ZipEntry(relativeFileName);
                        archive.putNextEntry(zipEntry);
                        archive.write(Files.readAllBytes(path));
                        archive.closeEntry();
                    }

                    archive.finish();
                    Path archiveFile = runContext.workingDir().createTempFile( ".zip");
                    Files.write(archiveFile, bos.toByteArray());
                    URI uri = runContext.storage().putCacheFile(archiveFile.toFile(), getId(), taskRun.getValue());
                    runContext.logger().debug("Caching in {}", uri);
                }
            } else {
                runContext.logger().debug("Cache files didn't change, skip updating it");
            }
        } catch (IOException e) {
            runContext.logger().error("Unable to execute WorkingDirectory post actions", e);
        }
    }

    @Override
    public Outputs outputs(final RunContext runContext) throws IOException {
        URI uri = URI.create("kestra://" + runContext.storage().getContextBaseURI() + "/").resolve(OUTPUTS_FILE);

        if (!runContext.storage().isFileExist(uri)) {
            // no outputs files was captured for that tasks
            return null;
        }

        try(Reader is = new BufferedReader(new InputStreamReader(runContext.storage().getFile(uri)))) {
            Map<String, URI> outputs = FileSerde
                .readAll(is, new TypeReference<Map<String, URI>>() {})
                .blockFirst();
            return new Outputs(outputs);
        }
    }

    @Getter
    public static class Outputs extends VoidOutput {
        @Schema(
            title = "The URIs for output files."
        )
        private final Map<String, URI> outputFiles;

        public Outputs(final Map<String, URI> outputsFiles) {
            this.outputFiles = outputsFiles;
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class Cache {
        @Schema(title = "Cache TTL (Time To Live), after this duration the cache will be deleted.")
        @PluginProperty
        private Duration ttl;

        @Schema(
            title = "List of file [glob](https://en.wikipedia.org/wiki/Glob_(programming)) patterns to include in the cache.",
            description = "For example, 'node_modules/**' will include all files of the node_modules directory including sub-directories."
        )
        @PluginProperty
        @NotNull
        private List<String> patterns;
    }
}
