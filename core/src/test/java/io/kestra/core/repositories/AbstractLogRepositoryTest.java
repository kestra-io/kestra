package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.statistics.LogStatistics;
import io.kestra.core.utils.IdUtils;
import io.micronaut.data.model.Pageable;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public abstract class AbstractLogRepositoryTest {
    @Inject
    protected LogRepositoryInterface logRepository;

    private static LogEntry.LogEntryBuilder logEntry(Level level) {
        return LogEntry.builder()
            .flowId("flowId")
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

        ArrayListTotal<LogEntry> find = logRepository.find(Pageable.UNPAGED, null, null, null, null, null, null, null, null);
        assertThat(find.size(), is(0));

        LogEntry save = logRepository.save(builder.build());

        find = logRepository.find(Pageable.UNPAGED, "doe", null, null, null, null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getExecutionId(), is(save.getExecutionId()));

        find = logRepository.find(Pageable.UNPAGED,  "doe", null, null, null, null, Level.WARN,null,  null);
        assertThat(find.size(), is(0));

        find = logRepository.find(Pageable.UNPAGED, null, null, null, null, null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getExecutionId(), is(save.getExecutionId()));

        logRepository.find(Pageable.UNPAGED, "kestra-io/kestra", null, null, null, null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getExecutionId(), is(save.getExecutionId()));

        List<LogEntry> list = logRepository.findByExecutionId(null, save.getExecutionId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.getFirst().getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionId(null, "io.kestra.unittest", "flowId", save.getExecutionId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.getFirst().getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskId(null, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.getFirst().getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskId(null, "io.kestra.unittest", "flowId", save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.getFirst().getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskRunId(null, save.getExecutionId(), save.getTaskRunId(), null);
        assertThat(list.size(), is(1));
        assertThat(list.getFirst().getExecutionId(), is(save.getExecutionId()));

        list = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(null, save.getExecutionId(), save.getTaskRunId(), null, 0);
        assertThat(list.size(), is(1));
        assertThat(list.getFirst().getExecutionId(), is(save.getExecutionId()));

        Integer countDeleted = logRepository.purge(Execution.builder().id(save.getExecutionId()).build());
        assertThat(countDeleted, is(1));

        list = logRepository.findByExecutionIdAndTaskId(null, save.getExecutionId(), save.getTaskId(), null);
        assertThat(list.size(), is(0));
    }

    @Test
    void pageable() {
        String executionId = "123";
        LogEntry.LogEntryBuilder builder = logEntry(Level.INFO);
        builder.executionId(executionId);

        for (int i = 0; i < 80; i++) {
            logRepository.save(builder.build());
        }

        builder = logEntry(Level.INFO).executionId(executionId).taskId("taskId2").taskRunId("taskRunId2");
        LogEntry logEntry2 = logRepository.save(builder.build());
        for (int i = 0; i < 20; i++) {
            logRepository.save(builder.build());
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

        logRepository.deleteByQuery(null, log1.getExecutionId(), null, (String) null, null, null);

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(null, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size(), is(0));

        logRepository.save(log1);

        logRepository.deleteByQuery(null, "io.kestra.unittest", "flowId", List.of(Level.TRACE, Level.DEBUG, Level.INFO), null, ZonedDateTime.now().plusMinutes(1));

        find = logRepository.findByExecutionId(null, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size(), is(0));
    }

    @Test
    void deleteByQuery() {
        LogEntry log1 = logEntry(Level.INFO).build();
        logRepository.save(log1);

        logRepository.deleteByQuery(null, log1.getExecutionId(), null, (String) null, null, null);

        ArrayListTotal<LogEntry> find = logRepository.findByExecutionId(null, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size(), is(0));

        logRepository.save(log1);

        logRepository.deleteByQuery(null, "io.kestra.unittest", "flowId", null);

        find = logRepository.findByExecutionId(null, log1.getExecutionId(), null, Pageable.from(1, 50));
        assertThat(find.size(), is(0));
    }

    @Test
    void statistics() throws InterruptedException {
        for (int i = 0; i < 28; i++) {
            logRepository.save(
                logEntry(i < 5 ? Level.TRACE : (i < 8 ? Level.INFO : Level.ERROR))
                    .flowId(i < 15 ? "first" : "second")
                    .build()
            );
        }

        // mysql need some time ...
        Thread.sleep(500);

        List<LogStatistics> list = logRepository.statistics(null, null, null, "first", null, null, null, null);
        assertThat(list.size(), is(31));
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.TRACE) == 5).count(), is(1L));
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.INFO) == 3).count(), is(1L));
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.ERROR) == 7).count(), is(1L));

        list = logRepository.statistics(null, null, null, "second", null, null, null, null);
        assertThat(list.size(), is(31));
        assertThat(list.stream().filter(logStatistics -> logStatistics.getCounts().get(Level.ERROR) == 13).count(), is(1L));
    }

    @Test
    void findAsych() {
        logRepository.save(logEntry(Level.INFO).build());
        logRepository.save(logEntry(Level.ERROR).build());
        logRepository.save(logEntry(Level.WARN).build());

        ZonedDateTime startDate = ZonedDateTime.now().minusSeconds(1);

        Flux<LogEntry> find = logRepository.findAsync(null, "io.kestra.unittest", Level.INFO, startDate);
        List<LogEntry> logEntries = find.collectList().block();
        assertThat(logEntries.size(), is(3));

        find = logRepository.findAsync(null, null, Level.ERROR, startDate);
        logEntries = find.collectList().block();
        assertThat(logEntries.size(), is(1));

        find = logRepository.findAsync(null, "io.kestra.unused", Level.INFO, startDate);
        logEntries = find.collectList().block();
        assertThat(logEntries.size(), is(0));

        find = logRepository.findAsync(null, null, Level.INFO, startDate.plusSeconds(2));
        logEntries = find.collectList().block();
        assertThat(logEntries.size(), is(0));
    }
}
