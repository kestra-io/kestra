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
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ConnectionClosedException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.models.tasks.runners.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.RetryUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task runner that executes a task inside a container in a Docker compatible engine.",
    description = """
        This task runner is container-based so the `containerImage` property must be set.
        
        To access the task's working directory, use the `{{workingDir}}` Pebble expression or the `WORKING_DIR` environment variable. Input files and namespace files will be available in this directory.

        To generate output files you can either use the `outputFiles` task's property and create a file with the same name in the task's working directory, or create any file in the output directory which can be accessed by the `{{outputDir}}` Pebble expression or the `OUTPUT_DIR` environment variables.
         
        Note that when the Kestra Worker running this task is terminated, the container will still run until completion, except if Kestra itself is run inside a container and Docker-In-Docker (dind) is used as the dind engine will also be terminated."""
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a Shell command.",
            code = """
                id: new-shell
                namespace: myteam
                
                tasks:
                  - id: shell
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.DockerTaskRunner
                    commands:
                    - echo "Hello World\"""",
            full = true
        ),
        @Example(
            title = "Pass input files to the task, execute a Shell command, then retrieve output files.",
            code = """
                id: new-shell-with-file
                namespace: myteam
                
                inputs:
                  - id: file
                    type: FILE
                
                tasks:
                  - id: shell
                    type: io.kestra.plugin.scripts.shell.Commands
                    inputFiles:
                      data.txt: "{{inputs.file}}"
                    outputFiles:
                      - out.txt
                    containerImage: centos
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.DockerTaskRunner
                    commands:
                    - cp {{workingDir}}/data.txt {{workingDir}}/out.txt""",
            full = true
        )
    },
    beta = true // all task runners are beta for now, but this one is stable as it was the one used before
)
public class DockerTaskRunner extends TaskRunner {
    private static final ReadableBytesTypeConverter READABLE_BYTES_TYPE_CONVERTER = new ReadableBytesTypeConverter();
    public static final Pattern NEWLINE_PATTERN = Pattern.compile("([^\\r\\n]+)[\\r\\n]+");

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
    protected List<String> entryPoint;

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
        title = "List of volumes to mount.",
        description = "Must be a valid mount expression as string, example : `/home/user:/app`.\n\n" +
            "Volumes mount are disabled by default for security reasons; you must enable them on [plugin configuration](https://kestra.io/docs/configuration-guide/plugins) by setting `volume-enabled` to `true`."
    )
    @PluginProperty(dynamic = true)
    protected List<String> volumes;

    @Schema(
        title = "The pull policy for an image.",
        description = "Pull policy can be used to prevent pulling of an already existing image `IF_NOT_PRESENT`, or can be set to `ALWAYS` to pull the latest version of the image even if an image with the same tag already exists."
    )
    @PluginProperty
    @Builder.Default
    protected PullPolicy pullPolicy = PullPolicy.ALWAYS;

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

    public static DockerTaskRunner from(DockerOptions dockerOptions) {
        if (dockerOptions == null) {
            return DockerTaskRunner.builder().build();
        }

        return DockerTaskRunner.builder()
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
            .build();
    }


    @Override
    public RunnerResult run(RunContext runContext, TaskCommands taskCommands, List<String> filesToUpload, List<String> filesToDownload) throws Exception {
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

        try (DockerClient dockerClient = dockerClient(runContext, image)) {
            // create container
            CreateContainerCmd container = configure(taskCommands, dockerClient, runContext, additionalVars);

            // pull image
            if (this.getPullPolicy() != PullPolicy.NEVER) {
                pullImage(dockerClient, image, this.getPullPolicy(), logger);
            }

            // start container
            CreateContainerResponse exec = container.exec();
            dockerClient.startContainerCmd(exec.getId()).exec();
            logger.debug(
                "Starting command with container id {} [{}]",
                exec.getId(),
                String.join(" ", taskCommands.getCommands())
            );

            AtomicBoolean ended = new AtomicBoolean(false);

            try {
                dockerClient.logContainerCmd(exec.getId())
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

                WaitContainerResultCallback result = dockerClient.waitContainerCmd(exec.getId()).start();

                Integer exitCode = result.awaitStatusCode();
                Await.until(ended::get);

                if (exitCode != 0) {
                    throw new TaskException(exitCode, defaultLogConsumer.getStdOutCount(), defaultLogConsumer.getStdErrCount());
                } else {
                    logger.debug("Command succeed with code " + exitCode);
                }

                return new RunnerResult(exitCode, defaultLogConsumer);
            } finally {
                try {
                    var inspect = dockerClient.inspectContainerCmd(exec.getId()).exec();
                    if (Boolean.TRUE.equals(inspect.getState().getRunning())) {
                        // kill container as it's still running, this means there was an exception and the container didn't
                        // come to a normal end.
                        try {
                            dockerClient.killContainerCmd(exec.getId()).exec();
                        } catch (Exception e) {
                            logger.error("Unable to kill a running container", e);
                        }
                    }
                    dockerClient.removeContainerCmd(exec.getId()).exec();
                } catch (Exception ignored) {

                }
            }
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

    private DockerClient dockerClient(RunContext runContext, String image) throws IOException, IllegalVariableEvaluationException {
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(DockerService.findHost(runContext, this.host));

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

    private CreateContainerCmd configure(TaskCommands taskCommands, DockerClient dockerClient, RunContext runContext, Map<String, Object> additionalVars) throws IllegalVariableEvaluationException {
        boolean volumesEnabled = runContext.<Boolean>pluginConfiguration("volume-enabled").orElse(Boolean.FALSE);
        if (!volumesEnabled) {
            // check the legacy property and emit a warning if used
            Optional<Boolean> property = runContext.getApplicationContext().getProperty(
                "kestra.tasks.scripts.docker.volume-enabled",
                Boolean.class
            );
            if (property.isPresent()) {
                runContext.logger().warn("`kestra.tasks.scripts.docker.volume-enabled` is deprecated, please use the plugin configuration `volume-enabled` instead");
                volumesEnabled = property.get();
            }
        }

        Path workingDirectory = taskCommands.getWorkingDirectory();
        String image = runContext.render(this.image, additionalVars);


        CreateContainerCmd container = dockerClient.createContainerCmd(image);
        addMetadata(runContext, container);

        HostConfig hostConfig = new HostConfig();

        container.withEnv(this.env(runContext, taskCommands)
            .entrySet()
            .stream()
            .map(r -> r.getKey() + "=" + r.getValue())
            .collect(Collectors.toList())
        );

        if (workingDirectory != null) {
            container.withWorkingDir(workingDirectory.toFile().getAbsolutePath());
        }

        List<Bind> binds = new ArrayList<>();

        if (workingDirectory != null) {
            binds.add(new Bind(
                workingDirectory.toAbsolutePath().toString(),
                new Volume(workingDirectory.toAbsolutePath().toString()),
                AccessMode.rw
            ));
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

        if (volumesEnabled && this.getVolumes() != null) {
            binds.addAll(runContext.render(this.getVolumes())
                .stream()
                .map(Bind::parse)
                .toList()
            );
        }

        if (!binds.isEmpty()) {
            hostConfig.withBinds(binds);
        }

        if (this.getDeviceRequests() != null) {
            hostConfig.withDeviceRequests(this
                .getDeviceRequests()
                .stream()
                .map(throwFunction(deviceRequest -> new com.github.dockerjava.api.model.DeviceRequest()
                    .withDriver(runContext.render(deviceRequest.getDriver()))
                    .withCount(deviceRequest.getCount())
                    .withDeviceIds(runContext.render(deviceRequest.getDeviceIds()))
                    .withCapabilities(deviceRequest.getCapabilities())
                    .withOptions(deviceRequest.getOptions())
                ))
                .collect(Collectors.toList())
            );
        }

        if (this.getCpu() != null) {
            if (this.getCpu().getCpus() != null) {
                hostConfig.withCpuQuota(this.getCpu().getCpus() * 10000L);
            }
        }

        if (this.getMemory() != null) {
            if (this.getMemory().getMemory() != null) {
                hostConfig.withMemory(convertBytes(runContext.render(this.getMemory().getMemory())));
            }

            if (this.getMemory().getMemorySwap() != null) {
                hostConfig.withMemorySwap(convertBytes(runContext.render(this.getMemory().getMemorySwap())));
            }

            if (this.getMemory().getMemorySwappiness() != null) {
                hostConfig.withMemorySwappiness(convertBytes(runContext.render(this.getMemory().getMemorySwappiness())));
            }

            if (this.getMemory().getMemoryReservation() != null) {
                hostConfig.withMemoryReservation(convertBytes(runContext.render(this.getMemory().getMemoryReservation())));
            }

            if (this.getMemory().getKernelMemory() != null) {
                hostConfig.withKernelMemory(convertBytes(runContext.render(this.getMemory().getKernelMemory())));
            }

            if (this.getMemory().getOomKillDisable() != null) {
                hostConfig.withOomKillDisable(this.getMemory().getOomKillDisable());
            }
        }

        if (this.getShmSize() != null) {
            hostConfig.withShmSize(convertBytes(runContext.render(this.getShmSize())));
        }

        if (this.getNetworkMode() != null) {
            hostConfig.withNetworkMode(runContext.render(this.getNetworkMode(), additionalVars));
        }

        return container
            .withHostConfig(hostConfig)
            .withCmd(taskCommands.getCommands())
            .withAttachStderr(true)
            .withAttachStdout(true);
    }

    private static void addMetadata(RunContext runContext, CreateContainerCmd container) {
        container.withLabels(ScriptService.labels(runContext, "kestra.io/"));
    }

    private static Long convertBytes(String bytes) {
        return READABLE_BYTES_TYPE_CONVERTER.convert(bytes, Number.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + bytes + "'"))
            .longValue();
    }

    private void pullImage(DockerClient dockerClient, String image, PullPolicy policy, Logger logger) {
        NameParser.ReposTag imageParse = NameParser.parseRepositoryTag(image);

        if (policy.equals(PullPolicy.IF_NOT_PRESENT)) {
            try {
                dockerClient.inspectImageCmd(image).exec();
                return;
            } catch (NotFoundException ignored) {

            }
        }

        try (PullImageCmd pull = dockerClient.pullImageCmd(image)) {
            new RetryUtils().<Boolean, InternalServerErrorException>of(
                Exponential.builder()
                    .delayFactor(2.0)
                    .interval(Duration.ofSeconds(5))
                    .maxInterval(Duration.ofSeconds(120))
                    .maxAttempt(5)
                    .build()
            ).run(
                (bool, throwable) -> throwable instanceof InternalServerErrorException ||
                    throwable.getCause() instanceof ConnectionClosedException,
                () -> {
                    String tag = !imageParse.tag.isEmpty() ? imageParse.tag : "latest";
                    String repository = pull.getRepository().contains(":") ? pull.getRepository().split(":")[0] : pull.getRepository();
                    pull
                        .withTag(tag)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion();

                    logger.debug("Image pulled [{}:{}]", repository, tag);

                    return true;
                }
            );
        }
    }
}
