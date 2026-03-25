package io.kestra.worker.stores;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.controller.grpc.ExecutionLogsServiceGrpc;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

@KestraTest
class GrpcWorkerExecutionLogMetaStoreTest extends AbstractGrpcMetaStoreTest {

    @Inject
    private ExecutionLogsServiceGrpc.ExecutionLogsServiceBlockingStub executionLogsStub;

    @Inject
    private LogRepositoryInterface logRepository;

    private GrpcWorkerExecutionLogMetaStore grpcWorkerExecutionLogMetaStore;

    @Override
    protected void initClientStore() {
        grpcWorkerExecutionLogMetaStore = new GrpcWorkerExecutionLogMetaStore(executionLogsStub, clientWorkerInfo());
    }

    @Test
    void errorLogs() {
        String tenantId = IdUtils.create();
        logRepository.save(logEntry(tenantId, Level.INFO, "some log message"));
        logRepository.save(logEntry(tenantId, Level.ERROR, "first error message"));
        logRepository.save(logEntry(tenantId, Level.ERROR, "second error message"));

        List<LogEntry> errorLogs = grpcWorkerExecutionLogMetaStore.errorLogs(tenantId, "execution");
        assertEquals(2, errorLogs.size());
    }

    private LogEntry logEntry(String tenantId, Level level, String message) {
        return LogEntry.builder().tenantId(tenantId).namespace("namespace").flowId("flow").executionId("execution").timestamp(Instant.now()).level(level).message(message).build();
    }
}