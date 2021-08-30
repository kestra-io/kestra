package io.kestra.core.tasks.scripts.runners;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient.Builder;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.scripts.AbstractBash;
import io.kestra.core.tasks.scripts.AbstractLogThread;
import io.kestra.core.tasks.scripts.RunResult;
import io.kestra.core.utils.Slugify;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

public class DockerScriptRunner implements ScriptRunnerInterface {
    private DockerClient getDockerClient(AbstractBash abstractBash, RunContext runContext, Path workingDirectory) throws IllegalVariableEvaluationException, IOException {
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

        if (abstractBash.getDockerOptions() != null) {
            if (abstractBash.getDockerOptions().getDockerHost() != null) {
                dockerClientConfigBuilder.withDockerHost(runContext.render(abstractBash.getDockerOptions().getDockerHost()));
            }

            if (abstractBash.getDockerOptions().getDockerConfig() != null) {
                Path docker = Files.createTempDirectory(workingDirectory, "docker");
                Path file = Files.createFile(docker.resolve("config.json"));

                Files.write(file, runContext.render(abstractBash.getDockerOptions().getDockerConfig()).getBytes());

                dockerClientConfigBuilder.withDockerConfig(docker.toFile().getAbsolutePath());
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

        String name = Slugify.of(String.join(
            "-",
            taskrun.get("id"),
            flow.get("id"),
            task.get("id")
        ));

        if (name.length() > 63) {
            name = name.substring(0, 63);
        }

        name = StringUtils.stripEnd(name, "-");

        container.withName(name);
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
        DockerClient dockerClient = getDockerClient(abstractBash, runContext, workingDirectory);

        if (abstractBash.getDockerOptions() == null) {
            throw new IllegalArgumentException("Missing required dockerOptions properties");
        }

        String image = runContext.render(abstractBash.getDockerOptions().getImage(), additionalVars);
        NameParser.ReposTag imageParse = NameParser.parseRepositoryTag(image);

        try (
            CreateContainerCmd container = dockerClient.createContainerCmd(image);
            PullImageCmd pull = dockerClient.pullImageCmd(image);
            PipedInputStream stdOutInputStream = new PipedInputStream();
            PipedOutputStream stdOutOutputStream = new PipedOutputStream(stdOutInputStream);
            PipedInputStream stdErrInputStream = new PipedInputStream();
            PipedOutputStream stdErrOutputStream = new PipedOutputStream(stdErrInputStream);
        ) {
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

            if (workingDirectory != null) {
                hostConfig
                    .withBinds(new Bind(
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

            container
                .withHostConfig(hostConfig)
                .withCmd(commandsWithInterpreter)
                .withAttachStderr(true)
                .withAttachStdout(true);

            // pull image
            pull
                .withTag(!imageParse.tag.equals("") ? imageParse.tag : "latest")
                .exec(new PullImageResultCallback())
                .awaitCompletion();
            logger.debug("Image pulled [{}:{}]", pull.getRepository(), pull.getTag());

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

                dockerClient.removeContainerCmd(exec.getId()).exec();

                if (exitCode != 0) {
                    throw new AbstractBash.BashException(exitCode, stdOut.getLogsCount(), stdErr.getLogsCount());
                } else {
                    logger.debug("Command succeed with code " + exitCode);
                }

                return new RunResult(exitCode, stdOut, stdErr);
            } catch (InterruptedException e) {
                logger.warn("Killing process {} for InterruptedException", exec.getId());

                dockerClient.killContainerCmd(exec.getId()).exec();
                dockerClient.removeContainerCmd(exec.getId()).exec();
                throw e;
            }
        }
    }

}
