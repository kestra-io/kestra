package io.kestra.plugin.core.flow;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.*;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageContext;
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
    private static final String OPEN_TASK_SUFFIX = "_open";
    private static final String CLOSE_TASK_SUFFIX = "_close";

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
        return FlowableUtils.resolveSequentialNexts(
            execution,
            ListUtils.concat(List.of(resolveInternalTask(openTask(), parentTaskRun)), this.childTasks(runContext, parentTaskRun)),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            ListUtils.concat(FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun), List.of(resolveInternalTask(closeTask(), parentTaskRun))),
            parentTaskRun
        );
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        ResolvedTask close = resolveInternalTask(closeTask(), parentTaskRun);
        return FlowableUtils.resolveSequentialState(
            execution,
            ListUtils.concat(List.of(resolveInternalTask(openTask(), parentTaskRun)), this.childTasks(runContext, parentTaskRun)),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            ListUtils.concat(FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun), List.of(close)),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        )
            // a finally task does not count towards the state, but a failed _close means the output files or the cache were not saved
            .map(state -> execution.computeFinalState(List.of(close), parentTaskRun, this.isAllowFailure(), this.isAllowWarning(), state));
    }

    // the internal tasks are children so the duplicate task id check of the flow also covers their ids
    @Override
    public List<Task> allChildTasks() {
        return ListUtils.concat(super.allChildTasks(), List.of(openTask(), closeTask()));
    }

    /**
     * The worker that ran the {@link Open} of a WorkingDirectory task run, so the one all its other
     * tasks must run on.
     */
    public static Optional<String> boundWorkerId(Execution execution, String workingDirectoryTaskRunId) {
        return execution.findTaskRunByTaskRunIdIfPresent(workingDirectoryTaskRunId)
            .flatMap(workingDirectory -> ListUtils.emptyOnNull(execution.getTaskRunList()).stream()
                .filter(taskRun -> workingDirectoryTaskRunId.equals(taskRun.getParentTaskRunId()))
                .filter(taskRun -> taskRun.getTaskId().equals(workingDirectory.getTaskId() + OPEN_TASK_SUFFIX))
                .findFirst())
            .map(TaskRun::lastAttempt)
            .map(TaskRunAttempt::getWorkerId);
    }

    // carries the value of the WorkingDirectory task run, which keys its cache and its outputs, for instance inside a ForEach
    private static ResolvedTask resolveInternalTask(Task task, TaskRun parentTaskRun) {
        return ResolvedTask.builder()
            .task(task)
            .parentId(parentTaskRun.getId())
            .value(parentTaskRun.getValue())
            .build();
    }

    private Open openTask() {
        return Open.builder()
            .id(getId() + OPEN_TASK_SUFFIX)
            .type(Open.class.getName())
            .workingDirectory(this)
            .build();
    }

    private Close closeTask() {
        return Close.builder()
            .id(getId() + CLOSE_TASK_SUFFIX)
            .type(Close.class.getName())
            .workingDirectory(this)
            .build();
    }

    public void preExecuteTasks(RunContext runContext) throws Exception {
        if (cache != null) {
            // May download cached file if it exists and is not expired, and extract its content
            var maybeCacheFile = runContext.storage().getCacheFile(getId(), cacheObjectId(runContext), runContext.render(cache.ttl).as(Duration.class).orElse(null));
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

                // Record when the cache was downloaded so that the close task can check if files have been updated since
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

    public void postExecuteTasks(RunContext runContext) throws Exception {
        if (this.outputFiles != null) {
            try {
                Map<String, URI> outputFilesURIs = FilesService.outputFiles(runContext, runContext.render(this.outputFiles).asList(String.class));
                if (!outputFilesURIs.isEmpty()) {
                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try (os) {
                        FileSerde.write(os, outputFilesURIs);
                    }
                    runContext.storage().putFile(new ByteArrayInputStream(os.toByteArray()), outputsFile(workingDirectoryContextBaseURI(runContext)));
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
                    URI uri = runContext.storage().putCacheFile(archiveFile.toFile(), getId(), cacheObjectId(runContext));
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

    private static String cacheObjectId(RunContext runContext) {
        return Objects.toString(runContext.taskRunInfo().value(), null);
    }

    /**
     * The output files are saved by the {@link Close} task run, but read as outputs of its WorkingDirectory.
     */
    private URI workingDirectoryContextBaseURI(RunContext runContext) throws IllegalVariableEvaluationException {
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        return StorageContext.forTask(
            flowInfo.tenantId(),
            flowInfo.namespace(),
            flowInfo.id(),
            runContext.taskRunInfo().executionId(),
            getId(),
            runContext.render("{{ taskrun.parentId }}"),
            cacheObjectId(runContext)
        ).getContextStorageURI();
    }

    private static URI outputsFile(URI contextBaseURI) {
        return URI.create("kestra://" + contextBaseURI + "/").resolve(OUTPUTS_FILE);
    }

    /**
     * The open and close tasks of a WorkingDirectory run on distinct task instances, so the time the cache was
     * downloaded is kept in a file next to the folder, where neither the cache patterns nor the output files match it.
     */
    private static Path cacheDownloadedMarker(RunContext runContext) {
        Path folder = runContext.workingDir().path();
        return folder.resolveSibling(folder.getFileName() + ".cache-downloaded-at");
    }

    @Override
    public Outputs outputs(final RunContext runContext) throws IOException {
        URI uri = outputsFile(runContext.storage().getContextBaseURI());

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

    /**
     * First task of a {@link WorkingDirectory}: prepares its folder on the worker its other tasks are then bound to.
     */
    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin(internal = true)
    public static class Open extends Task implements RunnableTask<VoidOutput> {
        @NotNull
        private WorkingDirectory workingDirectory;

        @Override
        public VoidOutput run(RunContext runContext) throws Exception {
            workingDirectory.cleanup(runContext);
            runContext.workingDir().path(true);
            try {
                workingDirectory.preExecuteTasks(runContext);
            } catch (Exception e) {
                workingDirectory.cleanup(runContext);
                throw e;
            }
            return null;
        }
    }

    /**
     * Last task of a {@link WorkingDirectory}, run even when one of its tasks failed: saves the output files and the
     * cache, then deletes the folder.
     */
    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin(internal = true)
    public static class Close extends Task implements RunnableTask<VoidOutput> {
        @NotNull
        private WorkingDirectory workingDirectory;

        @Override
        public VoidOutput run(RunContext runContext) throws Exception {
            // an Open that failed already deleted the folder
            if (!Files.exists(runContext.workingDir().path(false))) {
                return null;
            }
            try {
                workingDirectory.postExecuteTasks(runContext);
            } finally {
                workingDirectory.cleanup(runContext);
            }
            return null;
        }
    }
}
