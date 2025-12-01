package io.kestra.core.services;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for fetching logs for from an execution.
 */
@Singleton
public class ExecutionLogService {
   
    private final LogRepositoryInterface logRepository;
    
    @Inject
    public ExecutionLogService(LogRepositoryInterface logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * Purges log entries matching the given criteria.
     *
     * @param tenantId    the tenant identifier
     * @param namespace   the namespace of the flow
     * @param flowId      the flow identifier
     * @param executionId the execution identifier
     * @param logLevels   the list of log levels to delete
     * @param startDate   the start of the date range
     * @param endDate     the end of the date range.
     * @return the number of log entries deleted
     */
    public int purge(String tenantId, String namespace, String flowId, String executionId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate) {
        return logRepository.deleteByQuery(tenantId, namespace, flowId, executionId, logLevels, startDate, endDate);
    }


    /**
     * Fetches the error logs of an execution.
     * <p>
     * This method limits the results to the first 25 error logs, ordered by timestamp asc.
     *
     * @return the log entries
     */
    public List<LogEntry> errorLogs(String tenantId, String executionId) {
        return logRepository.findByExecutionId(tenantId, executionId, Level.ERROR, Pageable.from(1, 25, Sort.of(Sort.Order.asc("timestamp"))));
    }
    
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
