package io.kestra.core.runners;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class DefaultExecutionLogMetaStoreTest {
    @Inject
    private DefaultExecutionLogMetaStore executionLogMetaStore;

    @Inject
    private LogRepositoryInterface logRepository;

    @Test
    void errorLogs() {
        String tenantId = IdUtils.create();
        logRepository.save(logEntry(tenantId, Level.INFO, "some log message"));
        logRepository.save(logEntry(tenantId, Level.ERROR, "first error message"));
        logRepository.save(logEntry(tenantId, Level.ERROR, "second error message"));

        List<LogEntry> errorLogs = executionLogMetaStore.errorLogs(tenantId, "execution");
        assertEquals(2, errorLogs.size());
    }

    private LogEntry logEntry(String tenantId, Level level, String message) {
        return LogEntry.builder().tenantId(tenantId).namespace("namespace").flowId("flow").executionId("execution").timestamp(Instant.now()).level(level).message(message).build();
    }
}