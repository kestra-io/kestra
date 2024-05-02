package io.kestra.plugin.scripts.runner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.MapUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerService {
    public static DockerClient client(DockerClientConfig dockerClientConfig) {
        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(dockerClientConfig.getDockerHost())
            .build();

        return DockerClientBuilder
            .getInstance(dockerClientConfig)
            .withDockerHttpClient(dockerHttpClient)
            .build();
    }

    public static String findHost(RunContext runContext, String host) throws IllegalVariableEvaluationException {
        if (host != null) {
            return runContext.render(host);
        }

        if (Files.exists(Path.of("/var/run/docker.sock"))) {
            return "unix:///var/run/docker.sock";
        }

        return "unix:///dind/docker.sock";
    }

    public static Path createConfig(RunContext runContext, @Nullable Object config, @Nullable List<Credentials> credentials, @Nullable String image) throws IllegalVariableEvaluationException, IOException {
        Map<String, Object> finalConfig = new HashMap<>();

        if (config != null) {
            if (config instanceof String) {
                finalConfig = JacksonMapper.toMap(runContext.render(config.toString()));
            } else {
                //noinspection unchecked
                finalConfig = runContext.render((Map<String, Object>) config);
            }
        }

        if (credentials != null) {
            Map<String, Object> auths = new HashMap<>();
            String registry = "https://index.docker.io/v1/";

            for (Credentials c : credentials) {
                if (c.getUsername() != null) {
                    auths.put("username", runContext.render(c.getUsername()));
                }

                if (c.getPassword() != null) {
                    auths.put("password", runContext.render(c.getPassword()));
                }

                if (c.getRegistryToken() != null) {
                    auths.put("registrytoken", runContext.render(c.getRegistryToken()));
                }

                if (c.getIdentityToken() != null) {
                    auths.put("identitytoken", runContext.render(c.getIdentityToken()));
                }

                if (c.getAuth() != null) {
                    auths.put("auth", runContext.render(c.getAuth()));
                }

                if (c.getRegistry() != null) {
                    registry = runContext.render(c.getRegistry());
                } else if (image != null) {
                    String renderedImage = runContext.render(image);
                    String detectedRegistry = registryUrlFromImage(renderedImage);

                    if (!detectedRegistry.startsWith(renderedImage)) {
                        registry = detectedRegistry;
                    }
                }
            }

            finalConfig = MapUtils.merge(finalConfig, Map.of("auths", Map.of(registry, auths)));
        }

        File docker = runContext.tempDir(true).resolve("config.json").toFile();

        if (docker.exists()) {
            //noinspection ResultOfMethodCallIgnored
            docker.delete();
        } else {
            Files.createFile(docker.toPath());
        }

        Files.write(
            docker.toPath(),
            runContext.render(JacksonMapper.ofJson().writeValueAsString(finalConfig)).getBytes()
        );

        return docker.toPath().getParent();
    }

    public static String registryUrlFromImage(String image) {
        NameParser.ReposTag imageParse = NameParser.parseRepositoryTag(image);
        return URI.create(imageParse.repos.startsWith("http") ? imageParse.repos : "https://" + imageParse.repos)
            .getHost();
    }
}
