package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.utils.IdUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
public abstract class AbstractLogRepositoryTest {
    @Inject
    protected LogRepositoryInterface logRepository;

    private static LogEntry.LogEntryBuilder logEntry(Level level) {
        return LogEntry.builder()
            .id(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .taskId("taskId")
            .executionId(IdUtils.create())
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .timestamp(Instant.now())
            .level(level)
            .thread("")
            .message("john doe");
    }

    @Test
    void all() {
        LogEntry.LogEntryBuilder builder = logEntry(Level.INFO);

        ArrayListTotal<LogEntry> find = logRepository.find(Pageable.UNPAGED, null, null, null, null, null, null, null);
        assertThat(find.size(), is(0));

        LogEntry save = logRepository.save(builder.build());

        find = logRepository.find(Pageable.UNPAGED, "doe", null, null, null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.get(0).getExecutionId(), is(save.getExecutionId()));

        find = logRepository.find(Pageable.UNPAGED,  "doe", null, null, null, Level.WARN,null,  null);
        assertThat(find.size(), is(0));

        find = logRepository.find(Pageable.UNPAGED, null, null, null, null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.get(0).getExecutionId(), is(save.getExecutionId()));

        logRepository.find(Pageable.UNPAGED, "kestra-io/kestra", null, null, null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.get(0).getExecutionId(), is(save.getExecutionId()));

        List<LogEntry> list = logRepository.findByExecutionId(null, save.getExecutionId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.get(0).getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskId(null, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.get(0).getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskRunId(null, save.getExecutionId(), save.getTaskRunId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.get(0).getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(null, save.getExecutionId(), save.getTaskRunId(), null, 0);
        assertThat(list.size(), is(1));
        assertThat(list.get(0).getExecutionId(), is(save.getExecutionId()));

        Integer countDeleted = logRepository.purge(Execution.builder().id(save.getExecutionId()).build());
        assertThat(countDeleted, is(1));

        list = logRepository.findByExecutionIdAndTaskId(null, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size(), is(0));
    }

    @Test
    void Pageable() {
        String executionId = "123";
        LogEntry.LogEntryBuilder builder = logEntry(Level.INFO);
        builder.executionId(executionId);

        for (int i = 0; i < 80; i++) {
            logRepository.save(builder.id(IdUtils.create()).build());
        }

        builder = logEntry(Level.INFO).executionId(executionId).taskId("taskId2").taskRunId("taskRunId2");
        LogEntry logEntry2 = logRepository.save(builder.build());
        for (int i = 0; i < 20; i++) {
            logRepository.save(builder.id(IdUtils.create()).build());
        }

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(null, executionId, null, Pageable.from(1, 50));

        assertThat(find.size(), is(50));
        assertThat(find.getTotal(), is(101L));

        find = logRepository.findByExecutionId(null, executionId, null, Pageable.from(3, 50));

        assertThat(find.size(), is(1));
        assertThat(find.getTotal(), is(101L));

        find = logRepository.findByExecutionIdAndTaskId(null, executionId, logEntry2.getTaskId(), null, Pageable.from(1, 50));

        assertThat(find.size(), is(21));
        assertThat(find.getTotal(), is(21L));

        find = logRepository.findByExecutionIdAndTaskRunId(null, executionId, logEntry2.getTaskRunId(), null, Pageable.from(1, 10));

        assertThat(find.size(), is(10));
        assertThat(find.getTotal(), is(21L));

        find = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(null, executionId, logEntry2.getTaskRunId(), null, 0, Pageable.from(1, 10));

        assertThat(find.size(), is(10));
        assertThat(find.getTotal(), is(21L));

        find = logRepository.findByExecutionIdAndTaskRunId(null, executionId, logEntry2.getTaskRunId(), null, Pageable.from(10, 10));

        assertThat(find.size(), is(0));
    }

    @Test
    void delete() {
        LogEntry log1 = logEntry(Level.INFO).build();
        logRepository.save(log1);

        logRepository.delete(log1);

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(null, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size(), is(0));
    }
}
