package io.kestra.plugin.core.flow;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.InputFilesInterface;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.models.tasks.NamespaceFilesInterface;
import io.kestra.core.models.tasks.OutputFilesInterface;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.*;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.NamespaceFilesUtils;
import io.kestra.core.validations.WorkingDirectoryTaskValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.time.DurationMin;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Reuse a single working directory across tasks.",
    description = """
        Runs child tasks sequentially on the same worker filesystem so files persist between steps without wiring outputs. Flowable tasks (e.g., Parallel) are supported inside, and all the runnable tasks they hold run on that worker.

        Supports input/output files and optional caching of directory patterns to speed repeated runs."""
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
        ),
        @Example(
            full = true,
            title = "A working directory with a cache of the Python virtual environment.",
            code = """
                id: python_cached_dependencies
                namespace: company.team

                tasks:
                  - id: working_dir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    cache:
                      patterns:
                        - venv/**
                      ttl: PT24H
                    tasks:
                      - id: python_script
                        type: io.kestra.plugin.scripts.python.Commands
                        taskRunner:
                          type: io.kestra.plugin.core.runner.Process
                        warningOnStdErr: false
                        beforeCommands:
                          - python -m venv venv
                          - ./venv/bin/pip install pandas
                        commands:
                          - ./venv/bin/python -c "import pandas as pd; print(pd.__version__)"
                """
        )
    }
)
@WorkingDirectoryTaskValidation
public class WorkingDirectory extends Sequential implements NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {

    private static final String OUTPUTS_FILE = "outputs.ion";

    @Schema(
        title = "Cache configuration",
        description = """
            When a cache is configured, an archive of the files denoted by the cache configuration is created at the end of the task run and saved in Kestra's internal storage.
            Then, at the beginning of the next task execution, the file archive is retrieved and the working directory is initialized with it.

            **Best Practice for Python Virtual Environments**: When caching Python virtual environments, use the full path to executables (e.g., `./venv/bin/pip` and `./venv/bin/python`) instead of attempting to activate the environment with `source` or `.` commands. This ensures compatibility across different shell environments and properly isolates dependencies within the cached venv.
            """
    )
    @PluginProperty
    private Cache cache;

    private NamespaceFiles namespaceFiles;


    private Object inputFiles;

    private Property<List<String>> outputFiles;

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (!isOpen(parentTaskRun)) {
            return Collections.emptyList();
        }
        return super.resolveNexts(runContext, execution, parentTaskRun);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (!isOpen(parentTaskRun)) {
            return Optional.empty();
        }
        return super.resolveState(runContext, execution, parentTaskRun);
    }

    /**
     * The worker that opened the folder of this WorkingDirectory, so the one all its tasks must run on.
     */
    public static Optional<String> boundWorkerId(TaskRun taskRun) {
        return ListUtils.emptyOnNull(taskRun.getAttempts()).reversed().stream()
            .map(TaskRunAttempt::getWorkerId)
            .filter(Objects::nonNull)
            .findFirst();
    }

    /**
     * A WorkingDirectory is closing once its children have ended: its last attempt holds their final state while the
     * worker saves the output files and deletes the folder.
     */
    public static boolean isClosing(TaskRun taskRun) {
        TaskRunAttempt lastAttempt = taskRun.lastAttempt();
        return taskRun.getState().isRunning() && lastAttempt != null && lastAttempt.getState().isTerminated();
    }

    private static boolean isOpen(TaskRun taskRun) {
        return boundWorkerId(taskRun).isPresent() && !isClosing(taskRun);
    }

    public void preExecuteTasks(RunContext runContext, TaskRun taskRun) throws Exception {
        if (cache != null) {
            // May download cached file if it exists and is not expired, and extract its content
            var maybeCacheFile = runContext.storage().getCacheFile(getId(), taskRun.getValue(), runContext.render(cache.ttl).as(Duration.class).orElse(null));
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

                // Record when the cache was downloaded so that the close job can check if files have been updated since
                Files.writeString(cacheDownloadedMarker(runContext), String.valueOf(System.currentTimeMillis()));
            }
        }

        if (this.namespaceFiles != null && !Boolean.FALSE.equals(runContext.render(this.namespaceFiles.getEnabled()).as(Boolean.class).orElse(true))) {
            NamespaceFilesUtils.loadNamespaceFiles(runContext, this.namespaceFiles);
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
            Path marker = cacheDownloadedMarker(runContext);
            long cacheDownloadedTime = Files.exists(marker) ? Long.parseLong(Files.readString(marker).trim()) : 0L;

            // This is monolithic, maybe a cache entry by pattern would be better.
            List<Path> matchesList = runContext.workingDir().findAllFilesMatching(runContext.render(cache.getPatterns()).asList(String.class));

            // Check that some files has been updated since the start of the task
            // TODO we may need to allow excluding files as some files always changed for dependencies (for ex .package-log.json)
            boolean cacheFilesAreUpdated = matchesList.stream()
                .anyMatch(path ->
                {
                    try {
                        return Files.getLastModifiedTime(path).toMillis() > cacheDownloadedTime;
                    } catch (IOException e) {
                        runContext.logger().warn("Unable to retrieve files last modified time, will update the cache anyway", e);
                        return true;
                    }
                });

            if (cacheFilesAreUpdated) {
                runContext.logger().debug("Cache files changed, we update the cache");
                try (
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ZipOutputStream archive = new ZipOutputStream(bos)
                ) {

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
                    Path archiveFile = runContext.workingDir().createTempFile(".zip");
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

    /**
     * Deletes the folder of this WorkingDirectory on the worker, with what this task keeps next to it.
     */
    public void cleanup(RunContext runContext) throws IOException {
        Files.deleteIfExists(cacheDownloadedMarker(runContext));
        runContext.workingDir().cleanup();
    }

    /**
     * The open and close jobs of a WorkingDirectory run on distinct task instances, so the time the cache was
     * downloaded is kept in a file next to the folder, where neither the cache patterns nor the output files match it.
     */
    private static Path cacheDownloadedMarker(RunContext runContext) {
        Path folder = runContext.workingDir().path();
        return folder.resolveSibling(folder.getFileName() + ".cache-downloaded-at");
    }

    @Override
    public Outputs outputs(final RunContext runContext) throws IOException {
        URI uri = URI.create("kestra://" + runContext.storage().getContextBaseURI() + "/").resolve(OUTPUTS_FILE);

        if (!runContext.storage().isFileExist(uri)) {
            // no outputs files was captured for that tasks
            return null;
        }

        try (InputStream is = new BufferedInputStream(runContext.storage().getFile(uri), FileSerde.BUFFER_SIZE)) {
            Map<String, URI> outputs = FileSerde
                .readAll(is, new TypeReference<Map<String, URI>>() {
                })
                .blockFirst();
            return new Outputs(outputs);
        }
    }

    @Getter
    public static class Outputs extends VoidOutput {
        @Schema(
            title = "The URIs for output files"
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
        private Property<@DurationMin(millis = 1, message = "must be a positive duration") Duration> ttl;

        @Schema(
            title = "List of file [glob](https://en.wikipedia.org/wiki/Glob_(programming)) patterns to include in the cache",
            description = "For example, 'node_modules/**' will include all files of the node_modules directory including sub-directories."
        )
        @NotNull
        private Property<List<String>> patterns;
    }
}
