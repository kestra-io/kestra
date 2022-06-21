package io.kestra.core.repositories;

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

        ArrayListTotal<LogEntry> find = logRepository.find(Pageable.UNPAGED, null, null, null, null);
        assertThat(find.size(), is(0));

        LogEntry save = logRepository.save(builder.build());

        find = logRepository.find(Pageable.UNPAGED, "doe", null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.get(0).getExecutionId(), is(save.getExecutionId()));

        find = logRepository.find(Pageable.UNPAGED,  "doe", Level.WARN,null, null);
        assertThat(find.size(), is(0));

        find = logRepository.find(Pageable.UNPAGED, null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.get(0).getExecutionId(), is(save.getExecutionId()));

        List<LogEntry> list = logRepository.findByExecutionId(save.getExecutionId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.get(0).getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskId(save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.get(0).getExecutionId(), is(save.getExecutionId()));


        list = logRepository.findByExecutionIdAndTaskRunId(save.getExecutionId(), save.getTaskRunId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.get(0).getExecutionId(), is(save.getExecutionId()));
    }
}
