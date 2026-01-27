package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest(environments = {"test", "cleaner"}, startRunner = true, startWorker = false)
public abstract class AbstractJdbcCleanerTest {
    @Inject
    private JdbcCleaner cleaner;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logsQueue;

    @Inject
    private LogRepositoryInterface logRepository;

    @Test
    void test() throws QueueException, InterruptedException {
        // first delete everything so we are sure no dangling records exist in the queue
        cleaner.deleteQueue();

        // then emit 5 entries wait for them to be processed before we can delete again and check that we have the right count
        logsQueue.emit(logEntry());
        logsQueue.emit(logEntry());
        logsQueue.emit(logEntry());
        logsQueue.emit(logEntry());
        logsQueue.emit(logEntry());
        await().atMost(Duration.ofSeconds(10))
            .until(() -> logRepository.findAllAsync(TenantService.MAIN_TENANT).collectList().map(l -> l.size()).blockOptional().orElse(0) >= 5);

        // we need to sleep one second to ensure MySQL would retrieve the results reliably
        Thread.sleep(1000);

        long deleted = cleaner.deleteQueue();
        assertThat(deleted).isGreaterThanOrEqualTo(5); // we cannot be sure in CI to have the exact count so that's the best we can do
    }


    private LogEntry logEntry() {
        return LogEntry.builder()
            .flowId("flowId")
            .namespace("io.kestra.unittest")
            .taskId("taskId")
            .executionId(IdUtils.create())
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .timestamp(Instant.now())
            .level(Level.INFO)
            .thread("Thread")
            .tenantId(TenantService.MAIN_TENANT)
            .triggerId("triggerId")
            .message("Hello World")
            .build();
    }
}