package io.kestra.core.services;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for fetching logs for from an execution.
 */
@Singleton
public class ExecutionLogService {
    @Inject
    private LogRepositoryInterface logRepository;

    public InputStream getExecutionLogsAsStream(String tenantId,
                                                String executionId,
                                                Level minLevel,
                                                String taskRunId,
                                                List<String> taskIds,
                                                Integer attempt,
                                                boolean withAccessControl) {
        List<LogEntry> logs = getExecutionLogs(tenantId, executionId, minLevel, taskRunId, taskIds, attempt, withAccessControl);
        return new ByteArrayInputStream(logs.stream().map(LogEntry::toPrettyString).collect(Collectors.joining("\n")).getBytes());
    }

    public List<LogEntry> getExecutionLogs(String tenantId,
                                           String executionId,
                                           Level minLevel,
                                           String taskRunId,
                                           List<String> taskIds,
                                           Integer attempt,
                                           boolean withAccessControl) {
        // Get by Execution ID and TaskID.
        if (taskIds != null) {
            if (taskIds.size() == 1) {
                return withAccessControl ?
                    logRepository.findByExecutionIdAndTaskId(tenantId, executionId, taskIds.getFirst(), minLevel) :
                    logRepository.findByExecutionIdAndTaskIdWithoutAcl(tenantId, executionId, taskIds.getFirst(), minLevel);
            } else {
                return getExecutionLogs(tenantId, executionId, minLevel, taskIds, withAccessControl).toList();
            }
        }

        // Get by Execution ID, TaskRunID, and attempt.
        if (taskRunId != null) {
            if (attempt != null) {
                return withAccessControl ?
                    logRepository.findByExecutionIdAndTaskRunIdAndAttempt(tenantId, executionId, taskRunId, minLevel, attempt) :
                    logRepository.findByExecutionIdAndTaskRunIdAndAttemptWithoutAcl(tenantId, executionId, taskRunId, minLevel, attempt);
            } else {
                return withAccessControl ?
                    logRepository.findByExecutionIdAndTaskRunId(tenantId, executionId, taskRunId, minLevel) :
                    logRepository.findByExecutionIdAndTaskRunIdWithoutAcl(tenantId, executionId, taskRunId, minLevel);
            }
        }

        // Get by Execution ID
        return withAccessControl ?
             logRepository.findByExecutionId(tenantId, executionId, minLevel) :
             logRepository.findByExecutionIdWithoutAcl(tenantId, executionId, minLevel);
    }

    public Stream<LogEntry> getExecutionLogs(String tenantId,
                                             String executionId,
                                             Level minLevel,
                                             List<String> taskIds,
                                             boolean withAccessControl) {

        List<LogEntry> results = withAccessControl ?
            logRepository.findByExecutionId(tenantId, executionId, minLevel) :
            logRepository.findByExecutionIdWithoutAcl(tenantId, executionId, minLevel);

        return results
            .stream()
            .filter(data -> taskIds.isEmpty() || taskIds.contains(data.getTaskId()));
    }
}
