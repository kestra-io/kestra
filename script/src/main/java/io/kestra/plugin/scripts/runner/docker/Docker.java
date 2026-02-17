package io.kestra.plugin.scripts.runner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.sun.jna.LastErrorException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.models.tasks.runners.*;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.RetryUtils;
import io.kestra.core.utils.UnixModeToPosixFilePermissions;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.WindowsUtils.windowsToUnixPath;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a task in a Docker container.",
    description = """
        This task runner executes tasks in a container-based Docker-compatible engine.
        Use the `containerImage` property to configure the image for the task.

        To access the task's working directory, use the `{{workingDir}}` Pebble expression
        or the `WORKING_DIR` environment variable.
        Input files and namespace files added to the task will be accessible from that directory.

        To generate output files, we recommend using the `outputFiles` task's property.
        This allows you to explicitly define which files from the task's working directory
        should be saved as output files.

        Alternatively, when writing files in your task, you can leverage
        the `{{outputDir}}` Pebble expression or the `OUTPUT_DIR` environment variable.
        All files written to that directory will be saved as output files automatically."""
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a Shell command.",
            code = """
                id: simple_shell_example
                namespace: company.team

                tasks:
                  - id: shell
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                    commands:
                    - echo "Hello World\"""",
            full = true
        ),
        @Example(
            title = "Pass input files to the task, execute a Shell command, then retrieve output files.",
            code = """
                id: shell_example_with_files
                namespace: company.team

                inputs:
                  - id: file
                    type: FILE

                tasks:
                  - id: shell
                    type: io.kestra.plugin.scripts.shell.Commands
                    inputFiles:
                      data.txt: "{{ inputs.file }}"
                    outputFiles:
                      - "*.txt"
                    containerImage: centos
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                    commands:
                    - cp {{ workingDir }}/data.txt {{ workingDir }}/out.txt""",
            full = true
        ),
        @Example(
            title = "Run a Python script in Docker and allocate a specific amount of memory.",
            code = """
                id: allocate_memory_to_python_script
                namespace: company.team

                tasks:
                  - id: script
                    type: io.kestra.plugin.scripts.python.Script
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                      pullPolicy: IF_NOT_PRESENT
                      cpu:
                        cpus: 1
                      memory:\s
                        memory: "512Mb"
                    containerImage: ghcr.io/kestra-io/kestrapy:latest
                    script: |
                      from kestra import Kestra
                     \s
                      data = dict(message="Hello from Kestra!")
                      Kestra.outputs(data)""",
            full = true
        ),
    }
)
public class Docker extends TaskRunner<Docker.DockerTaskRunnerDetailResult> {
    private static final ReadableBytesTypeConverter READABLE_BYTES_TYPE_CONVERTER = new ReadableBytesTypeConverter();
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("([^\\r\\n]+)[\\r\\n]+");

    private static final String LEGACY_VOLUME_ENABLED_CONFIG = "kestra.tasks.scripts.docker.volume-enabled";
    private static final String VOLUME_ENABLED_CONFIG = "volume-enabled";

    @Schema(
        title = "Docker API URI."
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "Docker configuration file.",
        description = "Docker configuration file that can set access credentials to private container registries. Usually located in `~/.docker/config.json`.",
        anyOf = {String.class, Map.class}
    )
    @PluginProperty(dynamic = true)
    private Object config;

    @Schema(
        title = "Credentials for a private container registry."
    )
    @PluginProperty(dynamic = true)
    private Credentials credentials;

    // used for backward compatibility with the old task runner facility
    @Schema(hidden = true)
    protected String image;

    @Schema(
        title = "User in the Docker container."
    )
    @PluginProperty(dynamic = true)
    protected String user;

    @Schema(
        title = "Docker entrypoint to use."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    protected List<String> entryPoint = List.of("");

    @Schema(
        title = "Extra hostname mappings to the container network interface configuration."
    )
    @PluginProperty(dynamic = true)
    protected List<String> extraHosts;

    @Schema(
        title = "Docker network mode to use e.g. `host`, `none`, etc."
    )
    @PluginProperty(dynamic = true)
    protected String networkMode;

    @Schema(
        title = "List of port bindings.",
        description = "Corresponds to the `--publish` (`-p`) option of the docker run CLI command using the format `ip:dockerHostPort:containerPort/protocol`.\n" +
            "Possible example :\n" +
            "- `8080:80/udp`" +
            "- `127.0.0.1:8080:80`" +
            "- `127.0.0.1:8080:80/udp`"
    )
    @PluginProperty(dynamic = true)
    protected List<String> portBindings;

    @Schema(
        title = "List of volumes to mount.",
        description = """
            Make sure to provide a map of a local path to a container path in the format: `/home/local/path:/app/container/path`.
            Volume mounts are disabled by default for security reasons — if you are sure you want to use them,
            enable that feature in the [plugin configuration](https://kestra.io/docs/configuration-guide/plugins)
            by setting `volume-enabled` to `true`.

            Here is how you can add that setting to your kestra configuration:
            ```yaml
            kestra:
              plugins:
                configurations:
                  - type: io.kestra.plugin.scripts.runner.docker.Docker
                    values:
                      volume-enabled: true
            ```"""
    )
    @PluginProperty(dynamic = true)
    protected List<String> volumes;

    @Schema(
        title = "The pull policy for a container image.",
        description = """
        Use the `IF_NOT_PRESENT` pull policy to avoid pulling already existing images.
        Use the `ALWAYS` pull policy to pull the latest version of an image
        even if an image with the same tag already exists."""
    )
    @Builder.Default
    protected Property<PullPolicy> pullPolicy = Property.ofValue(PullPolicy.IF_NOT_PRESENT);

    @Schema(
        title = "A list of device requests to be sent to device drivers."
    )
    @PluginProperty
    protected List<DeviceRequest> deviceRequests;

    @Schema(
        title = "Limits the CPU usage to a given maximum threshold value.",
        description = "By default, each container’s access to the host machine’s CPU cycles is unlimited. " +
            "You can set various constraints to limit a given container’s access to the host machine’s CPU cycles."
    )
    @PluginProperty
    protected Cpu cpu;

    @Schema(
        title = "Limits memory usage to a given maximum threshold value.",
        description = "Docker can enforce hard memory limits, which allow the container to use no more than a " +
            "given amount of user or system memory, or soft limits, which allow the container to use as much " +
            "memory as it needs unless certain conditions are met, such as when the kernel detects low memory " +
            "or contention on the host machine. Some of these options have different effects when used alone or " +
            "when more than one option is set."
    )
    @PluginProperty
    protected Memory memory;

    @Schema(
        title = "Size of `/dev/shm` in bytes.",
        description = "The size must be greater than 0. If omitted, the system uses 64MB."
    )
    @PluginProperty(dynamic = true)
    private String shmSize;

    @Schema(
        title = "Give extended privileges to this container."
    )
    private Property<Boolean> privileged;

    @Schema(
        title = "File handling strategy.",
        description = """
            How to handle local files (input files, output files, namespace files, ...).
            By default, we create a volume and copy the file into the volume bind path.
            Configuring it to `MOUNT` will mount the working directory instead."""
    )
    @NotNull
    @Builder.Default
    private Property<FileHandlingStrategy> fileHandlingStrategy = Property.ofValue(FileHandlingStrategy.VOLUME);

    @Schema(
        title = "Whether the container should be deleted upon completion."
    )
    @NotNull
    @Builder.Default
    private Property<Boolean> delete = Property.ofValue(true);

    @Builder.Default
    @Schema(
        title = "Whether to wait for the container to exit."
    )
    @NotNull
    private Property<Boolean> wait = Property.ofValue(true);

    @Builder.Default
    @NotNull
    @Schema(
        title = "When a task is killed, this property sets the grace period before killing the container.",
        description = "By default, we kill the container immediately when a task is killed. Optionally, you can configure a grace period so the container is stopped with a grace period instead."
    )
    private Duration killGracePeriod = Duration.ZERO;

    @Builder.Default
    @Schema(
        title = "Whether to resume an existing matching container on restart.",
        description = "If enabled, the runner will search for an existing container labeled with the current execution/task identifiers and reattach to it instead of creating a new container."
    )
    @PluginProperty
    private Property<Boolean> resume = Property.ofValue(true);

    /**
     * Convenient default instance to be used as task default value for a 'taskRunner' property.
     **/
    public static Docker instance() {
        return Docker.builder().type(Docker.class.getName()).build();
    }

    public static Docker from(DockerOptions dockerOptions) {
        if (dockerOptions == null) {
            return Docker.builder().build();
        }

        return Docker.builder()
            .type(Docker.class.getName())
            .host(dockerOptions.getHost())
            .config(dockerOptions.getConfig())
            .credentials(dockerOptions.getCredentials())
            .image(dockerOptions.getImage())
            .user(dockerOptions.getUser())
            .entryPoint(dockerOptions.getEntryPoint())
            .extraHosts(dockerOptions.getExtraHosts())
            .networkMode(dockerOptions.getNetworkMode())
            .volumes(dockerOptions.getVolumes())
            .pullPolicy(dockerOptions.getPullPolicy())
            .deviceRequests(dockerOptions.getDeviceRequests())
            .cpu(dockerOptions.getCpu())
            .memory(dockerOptions.getMemory())
            .shmSize(dockerOptions.getShmSize())
            .privileged(dockerOptions.getPrivileged())
            .build();
    }

    @Override
    public TaskRunnerResult<DockerTaskRunnerDetailResult> run(RunContext runContext, TaskCommands taskCommands, List<String> filesToDownload) throws Exception {
        Boolean renderedDelete = runContext.render(delete).as(Boolean.class).orElseThrow();

        if (taskCommands.getContainerImage() == null && this.image == null) {
            throw new IllegalArgumentException("This task runner needs the `containerImage` property to be set");
        }
        if (this.image == null) {
            this.image = taskCommands.getContainerImage();
        }

        Logger logger = runContext.logger();
        AbstractLogConsumer defaultLogConsumer = taskCommands.getLogConsumer();

        Map<String, Object> additionalVars = this.additionalVars(runContext, taskCommands);

        String image = runContext.render(this.image, additionalVars);

        String resolvedHost = DockerService.findHost(runContext, this.host);
        Map<String, String> labels = ScriptService.labels(runContext, "kestra.io/");

        try (DockerClient dockerClient = dockerClient(runContext, image, resolvedHost)) {
            // evaluate resume (task property overrides plugin configuration if set)
            Boolean resumeProp = runContext.render(this.resume).as(Boolean.class).orElse(Boolean.FALSE);
            boolean resumeEnabled = Boolean.TRUE.equals(resumeProp);

            String containerId = null;

            if (resumeEnabled) {
                List<Container> existing = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(labels)
                    .exec();

                if (!existing.isEmpty()) {
                    containerId = existing.get(0).getId();
                    logger.debug("Resuming existing container: {}", containerId);
                }
            }

            List<Path> relativeWorkingDirectoryFilesPaths = taskCommands.relativeWorkingDirectoryFilesPaths(true);
            boolean hasFilesToUpload = !ListUtils.isEmpty(relativeWorkingDirectoryFilesPaths);
            boolean hasFilesToDownload = !ListUtils.isEmpty(filesToDownload);
            boolean outputDirectoryEnabled = taskCommands.outputDirectoryEnabled();
            boolean needVolume = hasFilesToDownload || hasFilesToUpload || outputDirectoryEnabled;
            String filesVolumeName = null;
            var strategy = runContext.render(this.fileHandlingStrategy).as(FileHandlingStrategy.class).orElse(null);

            // pull image only if we will create a new container
            if (containerId == null) {
                var renderedPolicy = runContext.render(this.getPullPolicy()).as(PullPolicy.class).orElseThrow();
                if (!PullPolicy.NEVER.equals(renderedPolicy)) {
                    pullImage(dockerClient, image, renderedPolicy, logger);
                }

                // create container
                CreateContainerCmd container = configure(taskCommands, dockerClient, runContext, additionalVars);
                CreateContainerResponse exec = container.exec();
                containerId = exec.getId();
                if (logger.isTraceEnabled()) {
                    logger.trace("Container created: {}", containerId);
                }

                // create a volume if we need to handle files
                if (needVolume && FileHandlingStrategy.VOLUME.equals(strategy)) {
                    CreateVolumeCmd files = dockerClient.createVolumeCmd()
                        .withLabels(labels);
                    filesVolumeName = files.exec().getName();
                    if (logger.isTraceEnabled()) {
                        logger.trace("Volume created: {}", filesVolumeName);
                    }

                    String remotePath = windowsToUnixPath(taskCommands.getWorkingDirectory().toString());

                    // first, create an archive
                    Path fileArchive = runContext.workingDir().createFile("inputFiles.tar");
                    try (FileOutputStream fos = new FileOutputStream(fileArchive.toString());
                         TarArchiveOutputStream out = new TarArchiveOutputStream(fos)) {
                        out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX); // allow long file name
                        out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX); // allow large archive name

                        for (Path file: relativeWorkingDirectoryFilesPaths) {
                            Path resolvedFile = runContext.workingDir().resolve(file);
                            TarArchiveEntry entry = out.createArchiveEntry(resolvedFile.toFile(), file.toString());
                            // Preserve POSIX permissions if supported
                            try {
                                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(resolvedFile);
                                entry.setMode(UnixModeToPosixFilePermissions.fromPosixFilePermissions(perms));
                            } catch (UnsupportedOperationException | IOException ignore) {
                                // Skipping unix file permission
                            }
                            out.putArchiveEntry(entry);
                            if (!Files.isDirectory(resolvedFile)) {
                                try (InputStream fis = Files.newInputStream(resolvedFile)) {
                                    IOUtils.copy(fis, out);
                                }
                            }
                            out.closeArchiveEntry();
                        }
                        out.finish();
                    }

                    // then send it to the container
                    try (InputStream is = new FileInputStream(fileArchive.toString())) {
                        CopyArchiveToContainerCmd copyArchiveToContainerCmd = dockerClient.copyArchiveToContainerCmd(containerId)
                            .withTarInputStream(is)
                            .withRemotePath(remotePath);
                        copyArchiveToContainerCmd.exec();
                    }

                    Files.delete(fileArchive);

                    // create the outputDir if needed
                    if (taskCommands.outputDirectoryEnabled()) {
                        CopyArchiveToContainerCmd copyArchiveToContainerCmd = dockerClient.copyArchiveToContainerCmd(containerId)
                            .withHostResource(taskCommands.getOutputDirectory().toString())
                            .withRemotePath(remotePath);
                        copyArchiveToContainerCmd.exec();
                    }
                }

                // start container
                dockerClient.startContainerCmd(containerId).exec();

                List<String> renderedCommands = runContext.render(taskCommands.getCommands()).asList(String.class);

                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "Starting command with container id {} [{}]",
                        containerId,
                        String.join(" ", renderedCommands)
                    );
                }
            } else {
                // resumed path: do not re-create or start the container, just attach and wait
                if (logger.isDebugEnabled()) {
                    logger.debug("Attaching to logs of container {}", containerId);
                }
                if (needVolume && FileHandlingStrategy.VOLUME.equals(strategy)) {
                    List<String> labelsList = labels.entrySet()
                        .stream()
                        .map(entry -> String.join("=", entry.getKey(), entry.getValue()))
                        .toList();
                    var volumes = dockerClient.listVolumesCmd()
                        .withFilter("label", labelsList).exec();
                    if (volumes.getVolumes() == null || volumes.getVolumes().isEmpty()) {
                        logger.error("No volume found for resumed container {}", containerId);
                        throw new TaskException(1, defaultLogConsumer);
                    } else {
                        var volume = volumes.getVolumes().get(0);
                        filesVolumeName = volume.getName();
                        logger.debug("Volume found with name {} for resumed container {}", filesVolumeName, containerId);
                    }
                }

            }

            final String runContainerId = containerId;

            if (!Boolean.TRUE.equals(runContext.render(wait).as(Boolean.class).orElseThrow())) {
                return TaskRunnerResult.<DockerTaskRunnerDetailResult>builder()
                    .exitCode(0)
                    .logConsumer(defaultLogConsumer)
                    .details(DockerTaskRunnerDetailResult.builder().containerId(runContainerId).build())
                    .build();
            }

            // register the runnable to be used for killing the container.
            onKill(() -> kill(dockerClient, runContainerId, logger));

            AtomicBoolean ended = new AtomicBoolean(false);

            try {
                dockerClient.logContainerCmd(runContainerId)
                    .withFollowStream(true)
                    .withStdErr(true)
                    .withStdOut(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        private final Map<StreamType, StringBuilder> logBuffers = new HashMap<>();

                        @SneakyThrows
                        @Override
                        public void onNext(Frame frame) {
                            String frameStr = new String(frame.getPayload());

                            Matcher newLineMatcher = NEWLINE_PATTERN.matcher(frameStr);
                            logBuffers.computeIfAbsent(frame.getStreamType(), streamType -> new StringBuilder());

                            int lastIndex = 0;
                            while (newLineMatcher.find()) {
                                String fragment = newLineMatcher.group(0);
                                logBuffers.get(frame.getStreamType())
                                    .append(fragment);

                                StringBuilder logBuffer = logBuffers.get(frame.getStreamType());
                                this.send(logBuffer.toString(), frame.getStreamType() == StreamType.STDERR);
                                logBuffer.setLength(0);

                                lastIndex = newLineMatcher.end();
                            }

                            if (lastIndex < frameStr.length()) {
                                logBuffers.get(frame.getStreamType())
                                    .append(frameStr.substring(lastIndex));
                            }
                        }

                        private void send(String logBuffer, Boolean isStdErr) {
                            List.of(logBuffer.split("\n"))
                                .forEach(s -> defaultLogConsumer.accept(s, isStdErr));
                        }

                        @Override
                        public void onComplete() {
                            // Still flush last line even if there is no newline at the end
                            try {
                                logBuffers.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).forEach(throwConsumer(entry -> {
                                    String log = entry.getValue().toString();
                                    this.send(log, entry.getKey() == StreamType.STDERR);
                                }));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            ended.set(true);
                            super.onComplete();
                        }
                    });

                WaitContainerResultCallback result = dockerClient.waitContainerCmd(runContainerId).start();

                Integer exitCode = result.awaitStatusCode();
                Await.until(ended::get);

                if (exitCode != 0) {
                    if (needVolume && FileHandlingStrategy.VOLUME.equals(strategy) && filesVolumeName != null) {
                        // On failure, still attempt to download outputs if VOLUME strategy is used
                        downloadOutputFiles(runContainerId, dockerClient, runContext, taskCommands);
                    }

                    throw new TaskException(exitCode, defaultLogConsumer);
                } else if (logger.isDebugEnabled()) {
                    logger.debug("Command succeed with exit code {}", exitCode);
                }

                if (needVolume && FileHandlingStrategy.VOLUME.equals(strategy) && filesVolumeName != null) {
                    downloadOutputFiles(runContainerId, dockerClient, runContext, taskCommands);
                }

                return TaskRunnerResult.<DockerTaskRunnerDetailResult>builder()
                    .exitCode(exitCode)
                    .logConsumer(defaultLogConsumer)
                    .details(DockerTaskRunnerDetailResult.builder().containerId(runContainerId).build())
                    .build();
            } finally {
                try {
                    // kill container if it's still running, this means there was an exception and the container didn't
                    // come to a normal end.
                    kill();

                    if (Boolean.TRUE.equals(renderedDelete)) {
                        dockerClient.removeContainerCmd(runContainerId).exec();
                        if (logger.isTraceEnabled()) {
                            logger.trace("Container deleted: {}", runContainerId);
                        }

                        if (needVolume && FileHandlingStrategy.VOLUME.equals(strategy) && filesVolumeName != null) {
                            dockerClient.removeVolumeCmd(filesVolumeName).exec();

                            if (logger.isTraceEnabled()) {
                                logger.trace("Volume deleted: {}", filesVolumeName);
                            }
                        }
                    }
                } catch (Exception ignored) {

                }
            }
        } catch (RuntimeException e) {
            try {
                if (e.getCause() instanceof IOException io &&
                    io.getCause() instanceof LastErrorException socketException &&
                    socketException.getMessage().contains("No such file or directory") &&
                    Socket.class.isAssignableFrom(Class.forName(io.getStackTrace()[0].getClassName()))) {
                    throw new IllegalStateException("Docker socket is not accessible or not found. " +
                        "Please make sure you properly mounted the Docker socket into your Kestra container (`-v /var/run/docker.sock:/var/run/docker.sock`) and that your user or group has at least the read and write privilege. " +
                        "Tried socket: " + resolvedHost, e);
                }
            } catch (ClassNotFoundException ignored) {
                // If we can't check if the stacktrace class is a Socket, we just ignore the exception
                throw e;
            }
            throw e;
        }
    }

    private void downloadOutputFiles(String execId, DockerClient dockerClient, RunContext runContext, TaskCommands taskCommands) throws IOException {
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = dockerClient.copyArchiveFromContainerCmd(execId, windowsToUnixPath(taskCommands.getWorkingDirectory().toString()));
        try (InputStream is = copyArchiveFromContainerCmd.exec();
             TarArchiveInputStream tar = new TarArchiveInputStream(is)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                // each entry contains the working directory as the first part, we need to remove it
                Path extractTo = runContext.workingDir().resolve(Path.of(entry.getName().substring(runContext.workingDir().id().length() + 1)));
                if (entry.isDirectory()) {
                    if (!Files.exists(extractTo)) {
                        Files.createDirectories(extractTo);
                    }
                } else {
                    Files.copy(tar, extractTo, StandardCopyOption.REPLACE_EXISTING);
                    try {
                        Files.setPosixFilePermissions(extractTo, UnixModeToPosixFilePermissions.toPosixPermissions(entry.getMode()));
                    } catch (UnsupportedOperationException | IOException e) {
                        // File system does not support POSIX permissions (e.g., Windows)
                    }
                }
            }
        }
    }

    /**
     * Kill the container immediately or attempts to gracefully stop the specified Docker container if a `killGracePeriod` is set.
     * See <a href="https://docs.docker.com/reference/cli/docker/container/stop/">{@code docker container stop}</a>.
     *
     * @param dockerClient client for the Docker Engine API
     * @param containerId  container to kill
     * @param logger       standard logger
     */
    private void kill(final DockerClient dockerClient, final String containerId, final Logger logger) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            if (Boolean.TRUE.equals(inspect.getState().getRunning())) {
                if (killGracePeriod.isPositive()) {
                    dockerClient.stopContainerCmd(containerId).withTimeout((int) killGracePeriod.toSeconds()).exec();
                } else {
                    dockerClient.killContainerCmd(containerId).exec();
                }

                if (logger.isTraceEnabled()) {
                    logger.trace("Container was killed.");
                }
            }
        } catch (NotFoundException ignore) {
            // silently ignore - container does not exist anymore
        } catch (Exception e) {
            logger.error("Failed to kill running container.", e);
        }
    }

    @Override
    public Map<String, Object> runnerAdditionalVars(RunContext runContext, TaskCommands taskCommands) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(ScriptService.VAR_WORKING_DIR, taskCommands.getWorkingDirectory());

        if (taskCommands.outputDirectoryEnabled()) {
            vars.put(ScriptService.VAR_OUTPUT_DIR, taskCommands.getOutputDirectory());
        }

        return vars;
    }

    private DockerClient dockerClient(RunContext runContext, String image, String host) throws IOException, IllegalVariableEvaluationException {
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(host);

        if (this.getConfig() != null || this.getCredentials() != null) {
            Path config = DockerService.createConfig(
                runContext,
                this.getConfig(),
                this.getCredentials() != null ? List.of(this.getCredentials()) : null,
                image
            );

            dockerClientConfigBuilder.withDockerConfig(config.toFile().getAbsolutePath());
        }

        DockerClientConfig dockerClientConfig = dockerClientConfigBuilder.build();

        return DockerService.client(dockerClientConfig);
    }

    private CreateContainerCmd configure(TaskCommands taskCommands, DockerClient dockerClient, RunContext runContext, Map<String, Object> additionalVars) throws IllegalVariableEvaluationException, IOException {
        Optional<Boolean> volumeEnabledConfig = runContext.pluginConfiguration(VOLUME_ENABLED_CONFIG);
        if (volumeEnabledConfig.isEmpty()) {
            // check the legacy property and emit a warning if used
            Optional<Boolean> property = ((DefaultRunContext)runContext).getApplicationContext().getProperty(
                LEGACY_VOLUME_ENABLED_CONFIG,
                Boolean.class
            );
            if (property.isPresent()) {
                runContext.logger().warn(
                    "`{}` is deprecated, please use the plugin configuration `{}` instead",
                    LEGACY_VOLUME_ENABLED_CONFIG,
                    VOLUME_ENABLED_CONFIG
                );
                volumeEnabledConfig = property;
            }
        }
        boolean volumesEnabled = volumeEnabledConfig.orElse(Boolean.FALSE);

        Path workingDirectory = taskCommands.getWorkingDirectory();
        String image = runContext.render(this.image, additionalVars);

        CreateContainerCmd container = dockerClient.createContainerCmd(image)
            .withLabels(ScriptService.labels(runContext, "kestra.io/"));

        HostConfig hostConfig = new HostConfig();

        container.withEnv(this.env(runContext, taskCommands)
            .entrySet()
            .stream()
            .map(r -> r.getKey() + "=" + r.getValue())
            .toList()
        );

        if (workingDirectory != null) {
            container.withWorkingDir(windowsToUnixPath(workingDirectory.toAbsolutePath().toString()));
        }


        if (this.getUser() != null) {
            container.withUser(runContext.render(this.getUser(), additionalVars));
        }

        if (this.getEntryPoint() != null) {
            container.withEntrypoint(runContext.render(this.getEntryPoint(), additionalVars));
        }

        if (this.getExtraHosts() != null) {
            hostConfig.withExtraHosts(runContext.render(this.getExtraHosts(), additionalVars)
                .toArray(String[]::new));
        }

        List<Bind> binds = new ArrayList<>();
        if (FileHandlingStrategy.MOUNT.equals(runContext.render(this.fileHandlingStrategy).as(FileHandlingStrategy.class).orElse(null)) && workingDirectory != null) {
            String bindPath = windowsToUnixPath(workingDirectory.toString());
            binds.add(new Bind(
                bindPath,
                new Volume(bindPath),
                AccessMode.rw
            ));
        }
        if (volumesEnabled && this.getVolumes() != null) {
            binds.addAll(runContext.render(this.getVolumes())
                .stream()
                .map(Bind::parse)
                .toList());
        }
        if (!binds.isEmpty()) {
            hostConfig.withBinds(binds);
        }

        if (this.getDeviceRequests() != null) {
            hostConfig.withDeviceRequests(this
                .getDeviceRequests()
                .stream()
                .map(throwFunction(deviceRequest -> new com.github.dockerjava.api.model.DeviceRequest()
                    .withDriver(runContext.render(deviceRequest.getDriver()).as(String.class).orElse(null))
                    .withCount(runContext.render(deviceRequest.getCount()).as(Integer.class).orElse(null))
                    .withDeviceIds(runContext.render(deviceRequest.getDeviceIds()).asList(String.class))
                    .withCapabilities(runContext.render(deviceRequest.getCapabilities()).asList(List.class))
                    .withOptions(runContext.render(deviceRequest.getOptions()).asMap(String.class, String.class))
                ))
                .toList()
            );
        }

        if (this.getCpu() != null && this.getCpu().getCpus() != null) {
            Double cpuValue = runContext.render(this.getCpu().getCpus()).as(Double.class).orElseThrow();
            hostConfig.withNanoCPUs((long)(cpuValue * 1_000_000_000L));
        }

        if (this.getMemory() != null) {
            if (this.getMemory().getMemory() != null) {
                hostConfig.withMemory(convertBytes(runContext.render(this.getMemory().getMemory()).as(String.class).orElse(null)));
            }

            if (this.getMemory().getMemorySwap() != null) {
                hostConfig.withMemorySwap(convertBytes(runContext.render(this.getMemory().getMemorySwap()).as(String.class).orElse(null)));
            }

            if (this.getMemory().getMemorySwappiness() != null) {
                hostConfig.withMemorySwappiness(convertBytes(runContext.render(this.getMemory().getMemorySwappiness()).as(String.class).orElse(null)));
            }

            if (this.getMemory().getMemoryReservation() != null) {
                hostConfig.withMemoryReservation(convertBytes(runContext.render(this.getMemory().getMemoryReservation()).as(String.class).orElse(null)));
            }

            if (this.getMemory().getKernelMemory() != null) {
                hostConfig.withKernelMemory(convertBytes(runContext.render(this.getMemory().getKernelMemory()).as(String.class).orElse(null)));
            }

            if (this.getMemory().getOomKillDisable() != null) {
                hostConfig.withOomKillDisable(runContext.render(this.getMemory().getOomKillDisable()).as(Boolean.class).orElse(null));
            }
        }

        if (this.getShmSize() != null) {
            hostConfig.withShmSize(convertBytes(runContext.render(this.getShmSize())));
        }

        if (this.getPrivileged() != null) {
            hostConfig.withPrivileged(runContext.render(this.getPrivileged()).as(Boolean.class).orElseThrow());
        }

        if (this.getNetworkMode() != null) {
            hostConfig.withNetworkMode(runContext.render(this.getNetworkMode(), additionalVars));
        }

        if (this.getPortBindings() != null) {
            hostConfig.withPortBindings(runContext.render(this.getPortBindings(), additionalVars)
                .stream()
                .map(PortBinding::parse)
                .toList()
            );
        }

        return container
            .withHostConfig(hostConfig)
            .withCmd(runContext.render(taskCommands.getCommands()).asList(String.class))
            .withAttachStderr(true)
            .withAttachStdout(true);
    }

    private static Long convertBytes(String bytes) {
        return READABLE_BYTES_TYPE_CONVERTER.convert(bytes, Number.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + bytes + "'"))
            .longValue();
    }

    private String getImageNameWithoutTag(String fullImageName) {
        if (fullImageName == null || fullImageName.isEmpty()) {
            return fullImageName;
        }

        int lastColonIndex = fullImageName.lastIndexOf(':');
        int firstSlashIndex = fullImageName.indexOf('/');
        if (lastColonIndex > -1 && (firstSlashIndex == -1 || lastColonIndex > firstSlashIndex)) {
            return fullImageName.substring(0, lastColonIndex);
        } else {
            return fullImageName; // No tag found or the colon is part of the registry host
        }
    }

    private void pullImage(DockerClient dockerClient, String image, PullPolicy policy, Logger logger) {
        var imageNameWithoutTag = getImageNameWithoutTag(image);
        var parsedTagFromImage = NameParser.parseRepositoryTag(image);

        if (policy.equals(PullPolicy.IF_NOT_PRESENT)) {
            try {
                dockerClient.inspectImageCmd(image).exec();
                return;
            } catch (NotFoundException ignored) {

            }
        }

        // pullImageCmd without the tag (= repository) to avoid being redundant with withTag below
        // and prevent errors with Podman trying to pull "image:tag:tag"
        try (var pull = dockerClient.pullImageCmd(imageNameWithoutTag)) {
            RetryUtils.<Boolean, InternalServerErrorException>of(
                Exponential.builder()
                    .delayFactor(2.0)
                    .interval(Duration.ofSeconds(5))
                    .maxInterval(Duration.ofSeconds(120))
                    .maxAttempts(5)
                    .build()
            ).run(
                (bool, throwable) -> throwable instanceof InternalServerErrorException ||
                    throwable.getCause() instanceof ConnectionClosedException,
                () -> {
                    var tag = !parsedTagFromImage.tag.isEmpty() ? parsedTagFromImage.tag : "latest";
                    var repository = pull.getRepository().contains(":") ? pull.getRepository().split(":")[0] : pull.getRepository();
                    pull
                        .withTag(tag)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion();

                    if (logger.isTraceEnabled()) {
                        logger.trace("Image pulled [{}:{}]", repository, tag);
                    }

                    return true;
                }
            );
        }
    }

    @SuperBuilder
    @Getter
    public static class DockerTaskRunnerDetailResult extends TaskRunnerDetailResult {
        private String containerId;
    }

    public enum FileHandlingStrategy {
        MOUNT,
        VOLUME
    }
}
