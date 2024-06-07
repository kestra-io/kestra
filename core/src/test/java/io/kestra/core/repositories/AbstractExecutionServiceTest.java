package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public abstract class AbstractExecutionServiceTest {
    @Inject
    ExecutionService executionService;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    LogRepositoryInterface logRepository;

    @Inject
    StorageInterface storageInterface;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void purge() throws Exception {
        URL resource = AbstractExecutionServiceTest.class.getClassLoader().getResource("application-test.yml");
        File tempFile = File.createTempFile("test", "");
        Files.copy(new FileInputStream(Objects.requireNonNull(resource).getFile()), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        State state = new State().withState(State.Type.RUNNING).withState(State.Type.SUCCESS);

        Flow flow = Flow.builder()
            .namespace("io.kestra.test")
            .id("abc")
            .revision(1)
            .build();

        Execution execution = Execution
            .builder()
            .id(IdUtils.create())
            .state(state)
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .flowRevision(flow.getRevision())
            .build();

        Return task = Return.builder().id(IdUtils.create()).type(Return.class.getName()).build();

        TaskRun taskRun = TaskRun
            .builder()
            .namespace(flow.getNamespace())
            .id(IdUtils.create())
            .executionId(execution.getId())
            .flowId(flow.getId())
            .taskId(task.getId())
            .state(state)
            .build();

        RunContext runContext = runContextFactory.of(
            flow,
            task,
            execution,
            taskRun
        );

        execution.withInputs(Map.of("test", runContext.storage().putFile(tempFile)));

        executionRepository.save(execution);

        for (int i = 0; i < 10; i++) {
            logRepository.save(LogEntry.builder()
                .executionId(execution.getId())
                .timestamp(Instant.now())
                .message("Message " + i)
                .flowId(flow.getId())
                .level(Level.INFO)
                .namespace(flow.getNamespace())
                .build()
            );
        }

        ExecutionService.PurgeResult purge = executionService.purge(
            true,
            true,
            true,
            true,
            null,
            flow.getNamespace(),
            flow.getId(),
            ZonedDateTime.now(),
            null
        );

        assertThat(purge.getExecutionsCount(), is(1));
        assertThat(purge.getLogsCount(), is(10));
        assertThat(purge.getStoragesCount(), is(5));


        purge = executionService.purge(
            true,
            true,
            true,
            true,
            null,
            flow.getNamespace(),
            flow.getId(),
            ZonedDateTime.now(),
            null
        );

        assertThat(purge.getExecutionsCount(), is(0));
    }
}