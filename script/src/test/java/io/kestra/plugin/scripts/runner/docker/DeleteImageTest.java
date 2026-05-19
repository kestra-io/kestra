package io.kestra.plugin.scripts.runner.docker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.exception.NotFoundException;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DeleteImageTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @BeforeEach
    void assumeDockerAvailable() {
        String dockerHost = Optional.ofNullable(System.getenv("DOCKER_HOST"))
            .filter(host -> !host.isBlank())
            .orElse("unix:///var/run/docker.sock");

        boolean dockerAvailable = !dockerHost.startsWith("unix://") ||
            Files.exists(Path.of(dockerHost.substring("unix://".length())));

        Assumptions.assumeTrue(
            dockerAvailable,
            "Skipping Docker tests: Docker host not available: " + dockerHost
        );
    }

    private RunContext runContext() {
        Task task = new Task() {
            @Override
            public String getId() {
                return "task";
            }

            @Override
            public String getType() {
                return "Task";
            }
        };
        TaskRun taskRun = TaskRun.builder().id(IdUtils.create()).taskId("task").flowId("flow").namespace("namespace").executionId("execution")
            .state(new State().withState(State.Type.RUNNING))
            .build();
        Flow flow = Flow.builder().id("flow").namespace("namespace").revision(1)
            .tasks(List.of(task))
            .build();
        Execution execution = Execution.builder().flowId("flow").namespace("namespace").id("execution")
            .taskRunList(List.of(taskRun))
            .state(new State().withState(State.Type.RUNNING))
            .build();

        return runContextFactory.of(flow, task, execution, taskRun);
    }

    @Test
    void shouldDeleteImage() throws Exception {
        // Given
        var runContext = runContext();
        String testImage = "alpine:3.19.0";

        try (var client = DockerService.client(runContext, null, null, null, testImage)) {
            client.pullImageCmd(testImage).start().awaitCompletion();
            // Verify the image exists via inspect
            var inspectBefore = client.inspectImageCmd(testImage).exec();
            assertThat(inspectBefore).isNotNull();
        }

        var deleteImage = DeleteImage.builder()
            .id("delete-image")
            .type(DeleteImage.class.getName())
            .image(Property.ofValue(testImage))
            .force(Property.ofValue(true))
            .build();

        // When
        deleteImage.run(runContext);

        // Then — inspecting a deleted image should throw NotFoundException
        try (var client = DockerService.client(runContext, null, null, null, testImage)) {
            assertThrows(NotFoundException.class, () -> client.inspectImageCmd(testImage).exec());
        }
    }

    @Test
    void shouldNotFailWhenImageDoesNotExist() throws Exception {
        // Given
        var runContext = runContext();
        String testImage = "nonexistent-image:nonexistent-tag-12345";

        var deleteImage = DeleteImage.builder()
            .id("delete-image")
            .type(DeleteImage.class.getName())
            .image(Property.ofValue(testImage))
            .build();

        // When / Then — should not throw
        deleteImage.run(runContext);
    }
}
