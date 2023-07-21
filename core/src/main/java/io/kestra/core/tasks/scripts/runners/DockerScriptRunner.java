package io.kestra.core.tasks.scripts.runners;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient.Builder;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.scripts.AbstractBash;
import io.kestra.core.tasks.scripts.AbstractLogThread;
import io.kestra.core.tasks.scripts.RunResult;
import io.kestra.core.utils.RetryUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import lombok.SneakyThrows;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Deprecated
public class DockerScriptRunner implements ScriptRunnerInterface {
    private static final ReadableBytesTypeConverter READABLE_BYTES_TYPE_CONVERTER = new ReadableBytesTypeConverter();

    private final RetryUtils retryUtils;

    private final Boolean volumesEnabled;

    public DockerScriptRunner(ApplicationContext applicationContext) {
        this.retryUtils = applicationContext.getBean(RetryUtils.class);
        this.volumesEnabled = applicationContext.getProperty("kestra.tasks.scripts.docker.volume-enabled", Boolean.class).orElse(false);
    }

    private DockerClient getDockerClient(AbstractBash abstractBash, RunContext runContext, Path workingDirectory) throws IllegalVariableEvaluationException, IOException {
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

        String dockerHost = null;
        if (abstractBash.getDockerOptions() != null) {
            if (abstractBash.getDockerOptions().getDockerHost() != null) {
                dockerHost = runContext.render(abstractBash.getDockerOptions().getDockerHost());
            }

            if (abstractBash.getDockerOptions().getDockerConfig() != null) {
                Path docker = Files.createTempDirectory(workingDirectory, "docker");
                Path file = Files.createFile(docker.resolve("config.json"));

                Files.write(file, runContext.render(abstractBash.getDockerOptions().getDockerConfig()).getBytes());

                dockerClientConfigBuilder.withDockerConfig(docker.toFile().getAbsolutePath());
            }
        }

        if (dockerHost != null) {
            dockerClientConfigBuilder.withDockerHost(dockerHost);
        } else {
            if (Files.exists(Path.of("/var/run/docker.sock"))) {
                dockerClientConfigBuilder.withDockerHost("unix:///var/run/docker.sock");
            } else if (Files.exists(Path.of("/dind/docker.sock"))) {
                dockerClientConfigBuilder.withDockerHost("unix:///dind/docker.sock");
            }
        }

        DockerClientConfig dockerClientConfig = dockerClientConfigBuilder.build();

        ApacheDockerHttpClient dockerHttpClient = new Builder()
            .dockerHost(dockerClientConfig.getDockerHost())
            .build();

        return DockerClientBuilder
            .getInstance(dockerClientConfig)
            .withDockerHttpClient(dockerHttpClient)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static void metadata(RunContext runContext, CreateContainerCmd container) {
        Map<String, String> flow = (Map<String, String>) runContext.getVariables().get("flow");
        Map<String, String> task = (Map<String, String>) runContext.getVariables().get("task");
        Map<String, String> execution = (Map<String, String>) runContext.getVariables().get("execution");
        Map<String, String> taskrun = (Map<String, String>) runContext.getVariables().get("taskrun");

        container.withLabels(ImmutableMap.of(
            "flow.kestra.io/id", flow.get("id"),
            "flow.kestra.io/namespace", flow.get("namespace"),
            "task.kestra.io/id", task.get("id"),
            "execution.kestra.io/id", execution.get("id"),
            "taskrun.kestra.io/id", taskrun.get("id")
        ));
    }

    public RunResult run(
        AbstractBash abstractBash,
        RunContext runContext,
        Logger logger,
        Path workingDirectory,
        List<String> commandsWithInterpreter,
        Map<String, String> env,
        AbstractBash.LogSupplier logSupplier,
        Map<String, Object> additionalVars
    ) throws Exception {
        if (abstractBash.getDockerOptions() == null) {
            throw new IllegalArgumentException("Missing required dockerOptions properties");
        }

        String image = runContext.render(abstractBash.getDockerOptions().getImage(), additionalVars);
        NameParser.ReposTag imageParse = NameParser.parseRepositoryTag(image);

        try (
            DockerClient dockerClient = getDockerClient(abstractBash, runContext, workingDirectory);
            PipedInputStream stdOutInputStream = new PipedInputStream();
            PipedOutputStream stdOutOutputStream = new PipedOutputStream(stdOutInputStream);
            PipedInputStream stdErrInputStream = new PipedInputStream();
            PipedOutputStream stdErrOutputStream = new PipedOutputStream(stdErrInputStream);
        ) {
            CreateContainerCmd container = dockerClient.createContainerCmd(image);
            // properties
            metadata(runContext, container);
            HostConfig hostConfig = new HostConfig();

            if (env != null && env.size() > 0) {
                container.withEnv(env
                    .entrySet()
                    .stream()
                    .map(throwFunction(r -> runContext.render(r.getKey(), additionalVars) + "=" +
                            runContext.render(r.getValue(), additionalVars)
                    ))
                    .collect(Collectors.toList())
                );
            }

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

            if (abstractBash.getDockerOptions().getUser() != null) {
                container.withUser(runContext.render(abstractBash.getDockerOptions().getUser(), additionalVars));
            }

            if (abstractBash.getDockerOptions().getEntryPoint() != null) {
                container.withEntrypoint(runContext.render(abstractBash.getDockerOptions().getEntryPoint(), additionalVars));
            }

            if (abstractBash.getDockerOptions().getExtraHosts() != null) {
                hostConfig.withExtraHosts(runContext.render(abstractBash.getDockerOptions().getExtraHosts(), additionalVars).toArray(String[]::new));
            }

            if (this.volumesEnabled && abstractBash.getDockerOptions().getVolumes() != null) {
                binds.addAll(runContext.render(abstractBash.getDockerOptions().getVolumes())
                    .stream()
                    .map(Bind::parse)
                    .collect(Collectors.toList())
                );
            }

            if (binds.size() > 0) {
                hostConfig.withBinds(binds);
            }

            if (abstractBash.getDockerOptions().getDeviceRequests() != null) {
                hostConfig.withDeviceRequests(abstractBash.getDockerOptions()
                    .getDeviceRequests()
                    .stream()
                    .map(throwFunction(deviceRequest -> new DeviceRequest()
                        .withDriver(runContext.render(deviceRequest.getDriver()))
                        .withCount(deviceRequest.getCount())
                        .withDeviceIds(deviceRequest.getDeviceIds())
                        .withCapabilities(deviceRequest.getCapabilities())
                        .withOptions(deviceRequest.getOptions())
                    ))
                    .collect(Collectors.toList())
                );
            }

            if (abstractBash.getDockerOptions().getCpu() != null) {
                if (abstractBash.getDockerOptions().getCpu().getCpus() != null) {
                    hostConfig.withCpuQuota(abstractBash.getDockerOptions().getCpu().getCpus() * 10000L);
                }
            }

            if (abstractBash.getDockerOptions().getMemory() != null) {
                if (abstractBash.getDockerOptions().getMemory().getMemory() != null) {
                    hostConfig.withMemory(convertBytes(runContext.render(abstractBash.getDockerOptions().getMemory().getMemory())));
                }

                if (abstractBash.getDockerOptions().getMemory().getMemorySwap() != null) {
                    hostConfig.withMemorySwap(convertBytes(runContext.render(abstractBash.getDockerOptions().getMemory().getMemorySwap())));
                }

                if (abstractBash.getDockerOptions().getMemory().getMemorySwappiness() != null) {
                    hostConfig.withMemorySwappiness(convertBytes(runContext.render(abstractBash.getDockerOptions().getMemory().getMemorySwappiness())));
                }

                if (abstractBash.getDockerOptions().getMemory().getMemoryReservation() != null) {
                    hostConfig.withMemoryReservation(convertBytes(runContext.render(abstractBash.getDockerOptions().getMemory().getMemoryReservation())));
                }

                if (abstractBash.getDockerOptions().getMemory().getKernelMemory() != null) {
                    hostConfig.withKernelMemory(convertBytes(runContext.render(abstractBash.getDockerOptions().getMemory().getKernelMemory())));
                }

                if (abstractBash.getDockerOptions().getMemory().getOomKillDisable() != null) {
                    hostConfig.withOomKillDisable(abstractBash.getDockerOptions().getMemory().getOomKillDisable());
                }
            }

            if (abstractBash.getDockerOptions().getNetworkMode() != null) {
                hostConfig.withNetworkMode(runContext.render(abstractBash.getDockerOptions().getNetworkMode(), additionalVars));
            }

            container
                .withHostConfig(hostConfig)
                .withCmd(commandsWithInterpreter)
                .withAttachStderr(true)
                .withAttachStdout(true);

            // pull image
            if (abstractBash.getDockerOptions().getPullImage()) {
                try (PullImageCmd pull = dockerClient.pullImageCmd(image)) {
                    retryUtils.<Boolean, InternalServerErrorException>of(
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
                            String repository = pull.getRepository().contains(":")
                                ? pull.getRepository().split(":")[0] : pull.getRepository();
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

            // start container
            CreateContainerResponse exec = container.exec();
            dockerClient.startContainerCmd(exec.getId()).exec();
            logger.debug("Starting command with container id {} [{}]", exec.getId(), String.join(" ", commandsWithInterpreter));

            try {
                // logs
                AbstractLogThread stdOut = logSupplier.call(stdOutInputStream, false);
                AbstractLogThread stdErr = logSupplier.call(stdErrInputStream, true);

                dockerClient.logContainerCmd(exec.getId())
                    .withFollowStream(true)
                    .withStdErr(true)
                    .withStdOut(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @SneakyThrows
                        @Override
                        public void onNext(Frame item) {
                            if (item.getStreamType() == StreamType.STDOUT) {
                                stdOutOutputStream.write(item.getPayload());
                            } else {
                                stdErrOutputStream.write(item.getPayload());
                            }
                        }
                    });

                WaitContainerResultCallback result = dockerClient.waitContainerCmd(exec.getId()).start();

                Integer exitCode = result.awaitStatusCode();

                stdOutOutputStream.flush();
                stdOutOutputStream.close();
                stdErrOutputStream.flush();
                stdErrOutputStream.close();

                stdOut.join();
                stdErr.join();

                if (exitCode != 0) {
                    throw new AbstractBash.BashException(exitCode, stdOut.getLogsCount(), stdErr.getLogsCount());
                } else {
                    logger.debug("Command succeed with code " + exitCode);
                }

                return new RunResult(exitCode, stdOut, stdErr);
            } catch (InterruptedException e) {
                logger.warn("Killing process {} for InterruptedException", exec.getId());

                throw e;
            } finally {
                try  {
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

    private static Long convertBytes(String bytes) {
        return READABLE_BYTES_TYPE_CONVERTER.convert(bytes, Number.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + bytes + "'"))
            .longValue();
    }
}
