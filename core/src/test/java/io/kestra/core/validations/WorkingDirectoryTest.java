package io.kestra.core.validations;

import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.plugin.core.log.Log;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
public class WorkingDirectoryTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void workingDirectoryValid()  {
        var workingDirectory = WorkingDirectory.builder()
            .id("workingDir")
            .type(WorkingDirectory.class.getName())
            .tasks(
                List.of(Log.builder()
                    .id("log")
                    .type(Log.class.getName())
                    .message("Hello World")
                    .build()
                )
            )
            .build();

        assertThat(modelValidator.isValid(workingDirectory).isPresent(), is(false));
    }

    @Test
    void workingDirectoryInvalid()  {
        // empty list of tasks
        var workingDirectory = WorkingDirectory.builder()
            .id("workingDir")
            .type(WorkingDirectory.class.getName())
            .build();

        assertThat(modelValidator.isValid(workingDirectory).isPresent(), is(true));
        assertThat(modelValidator.isValid(workingDirectory).get().getMessage(), containsString("The 'tasks' property cannot be empty"));

        // flowable task
        workingDirectory = WorkingDirectory.builder()
            .id("workingDir")
            .type(WorkingDirectory.class.getName())
            .tasks(
                List.of(Pause.builder()
                    .id("pause")
                    .type(Pause.class.getName())
                    .delay(Duration.ofSeconds(1L))
                    .build()
                )
            )
            .build();

        assertThat(modelValidator.isValid(workingDirectory).isPresent(), is(true));
        assertThat(modelValidator.isValid(workingDirectory).get().getMessage(), containsString("Only runnable tasks are allowed as children of a WorkingDirectory task"));

        // worker group at the subtasks level
        workingDirectory = WorkingDirectory.builder()
            .id("workingDir")
            .type(WorkingDirectory.class.getName())
            .tasks(
                List.of(Log.builder()
                    .id("log")
                    .type(Log.class.getName())
                    .message("Hello World")
                    .workerGroup(new WorkerGroup("toto"))
                    .build()
                )
            )
            .build();

        assertThat(modelValidator.isValid(workingDirectory).isPresent(), is(true));
        assertThat(modelValidator.isValid(workingDirectory).get().getMessage(), containsString("Cannot set a Worker Group in any WorkingDirectory sub-tasks, it is only supported at the WorkingDirectory level"));

    }
}
