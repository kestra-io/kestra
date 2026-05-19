package io.kestra.plugin.scripts.runner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a Docker image from the Docker daemon.",
    description = """
        This task removes a Docker image from the local Docker daemon.
        This is useful when images are built dynamically (e.g. via `docker.Build`) and should not
        accumulate on the Docker daemon between executions."""
)
@Plugin(
    examples = {
        @Example(
            title = "Build and use a Docker image, then delete it.",
            code = """
                id: build_and_cleanup
                namespace: company.team

                tasks:
                  - id: build
                    type: io.kestra.plugin.docker.Build
                    dockerfile: |
                      FROM python:3.11
                      RUN pip install requests
                    tags:
                      - my-image:latest

                  - id: use
                    type: io.kestra.plugin.scripts.python.Commands
                    containerImage: my-image:latest
                    commands:
                      - python -c "import requests; print('OK')"

                  - id: cleanup
                    type: io.kestra.plugin.scripts.runner.docker.DeleteImage
                    image: my-image:latest""",
            full = true
        ),
    }
)
public class DeleteImage extends Task implements RunnableTask<VoidOutput> {
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

    @Schema(
        title = "The image to delete.",
        description = "The full image name including tag (e.g. `my-image:latest`)."
    )
    @NotNull
    private Property<String> image;

    @Schema(
        title = "Whether to force the image removal.",
        description = "When true, the image is removed even if it is being used by stopped containers or has other tags."
    )
    @NotNull
    @Builder.Default
    private Property<Boolean> force = Property.ofValue(false);

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        String renderedImage = runContext.render(image).as(String.class).orElseThrow();
        boolean renderedForce = runContext.render(force).as(Boolean.class).orElseThrow();

        try (DockerClient client = DockerService.client(runContext, host, config, credentials, renderedImage)) {
            client.removeImageCmd(renderedImage).withForce(renderedForce).exec();
            logger.info("Image deleted: {}", renderedImage);
        } catch (NotFoundException e) {
            logger.warn("Image not found: {}", renderedImage);
        }

        return null;
    }
}
