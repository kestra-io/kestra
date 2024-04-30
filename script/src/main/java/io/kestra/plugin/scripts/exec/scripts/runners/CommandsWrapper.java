package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.runners.DefaultLogConsumer;
import io.kestra.core.models.tasks.runners.*;
import io.kestra.core.models.tasks.runners.types.ProcessTaskRunner;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.NamespaceFilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.DockerTaskRunner;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Getter
public class CommandsWrapper implements TaskCommands {
    private RunContext runContext;

    private Path workingDirectory;

    private Path outputDirectory;

    private Map<String, Object> additionalVars;

    @With
    private List<String> commands;

    private Map<String, String> env;

    @With
    private io.kestra.core.models.tasks.runners.AbstractLogConsumer logConsumer;

    @With
    private RunnerType runnerType;

    @With
    private String containerImage;

    @With
    private TaskRunner taskRunner;

    @With
    private DockerOptions dockerOptions;

    @With
    private Boolean warningOnStdErr;

    @With
    private NamespaceFiles namespaceFiles;

    @With
    private Object inputFiles;

    @With
    private List<String> outputFiles;

    @With
    private Boolean enableOutputDirectory;

    @With
    private Duration timeout;

    public CommandsWrapper(RunContext runContext) {
        this.runContext = runContext;
        this.workingDirectory = runContext.tempDir();
        this.logConsumer = new DefaultLogConsumer(runContext);
        this.additionalVars = new HashMap<>();
        this.env = new HashMap<>();
    }

    public CommandsWrapper withEnv(Map<String, String> envs) {
        return new CommandsWrapper(
            runContext,
            workingDirectory,
            getOutputDirectory(),
            additionalVars,
            commands,
            envs,
            logConsumer,
            runnerType,
            containerImage,
            taskRunner,
            dockerOptions,
            warningOnStdErr,
            namespaceFiles,
            inputFiles,
            outputFiles,
            enableOutputDirectory,
            timeout
        );
    }

    public CommandsWrapper addAdditionalVars(Map<String, Object> additionalVars) {
        if (this.additionalVars == null) {
            this.additionalVars = new HashMap<>();
        }
        this.additionalVars.putAll(additionalVars);

        return this;
    }

    public CommandsWrapper addEnv(Map<String, String> envs) {
        if (this.env == null) {
            this.env = new HashMap<>();
        }
        this.env.putAll(envs);

        return this;
    }

    @SuppressWarnings("unchecked")
    public ScriptOutput run() throws Exception {
        List<String> filesToUpload = new ArrayList<>();
        if (this.namespaceFiles != null) {
            String tenantId = ((Map<String, String>) runContext.getVariables().get("flow")).get("tenantId");
            String namespace = ((Map<String, String>) runContext.getVariables().get("flow")).get("namespace");

            NamespaceFilesService namespaceFilesService = runContext.getApplicationContext().getBean(NamespaceFilesService.class);
            List<URI> injectedFiles = namespaceFilesService.inject(
                runContext,
                tenantId,
                namespace,
                this.workingDirectory,
                this.namespaceFiles
            );
            injectedFiles.forEach(uri -> filesToUpload.add(uri.toString().substring(1))); // we need to remove the leading '/'
        }

        TaskRunner realTaskRunner = this.getTaskRunner();
        if (this.inputFiles != null) {
            Map<String, String> finalInputFiles = FilesService.inputFiles(runContext, realTaskRunner.additionalVars(runContext, this), this.inputFiles);
            filesToUpload.addAll(finalInputFiles.keySet());
        }

        RunContext taskRunnerRunContext = runContext.forTaskRunner(realTaskRunner);

        this.commands = this.render(runContext, commands, filesToUpload);

        RunnerResult runnerResult = realTaskRunner.run(taskRunnerRunContext, this, filesToUpload, this.outputFiles);

        Map<String, URI> outputFiles = new HashMap<>();
        if (this.outputDirectoryEnabled()) {
            outputFiles.putAll(ScriptService.uploadOutputFiles(taskRunnerRunContext, this.getOutputDirectory()));
        }

        if (this.outputFiles != null) {
            outputFiles.putAll(FilesService.outputFiles(taskRunnerRunContext, this.outputFiles));
        }

        return ScriptOutput.builder()
            .exitCode(runnerResult.getExitCode())
            .stdOutLineCount(runnerResult.getLogConsumer().getStdOutCount())
            .stdErrLineCount(runnerResult.getLogConsumer().getStdErrCount())
            .warningOnStdErr(this.warningOnStdErr)
            .vars(runnerResult.getLogConsumer().getOutputs())
            .outputFiles(outputFiles)
            .build();
    }

    public TaskRunner getTaskRunner() {
        if (taskRunner == null) {
            taskRunner = switch (runnerType) {
                case DOCKER -> DockerTaskRunner.from(this.dockerOptions);
                case PROCESS -> new ProcessTaskRunner();
            };
        }

        return taskRunner;
    }

    public Boolean getEnableOutputDirectory() {
        if (this.enableOutputDirectory == null) {
            // For compatibility reasons, if legacy runnerType property is used, we enable the output directory
            return this.runnerType != null;
        }

        return this.enableOutputDirectory;
    }

    public Path getOutputDirectory() {
        if (this.outputDirectory == null) {
            this.outputDirectory = this.workingDirectory.resolve(IdUtils.create());
            if (!this.outputDirectory.toFile().mkdirs()) {
                throw new RuntimeException("Unable to create the output directory " + this.outputDirectory);
            }
        }

        return this.outputDirectory;
    }

    public String render(RunContext runContext, String command, List<String> internalStorageLocalFiles) throws IllegalVariableEvaluationException, IOException {
        TaskRunner taskRunner = this.getTaskRunner();
        return ScriptService.replaceInternalStorage(
            this.runContext,
            taskRunner.additionalVars(runContext, this),
            command,
            (ignored, localFilePath) -> internalStorageLocalFiles.add(localFilePath),
            taskRunner instanceof RemoteRunnerInterface
        );
    }

    public List<String> render(RunContext runContext, List<String> commands, List<String> internalStorageLocalFiles) throws IllegalVariableEvaluationException, IOException {
        TaskRunner taskRunner = this.getTaskRunner();
        return ScriptService.replaceInternalStorage(
            this.runContext,
            taskRunner.additionalVars(runContext, this),
            commands,
            (ignored, localFilePath) -> internalStorageLocalFiles.add(localFilePath),
            taskRunner instanceof RemoteRunnerInterface
        );
    }
}