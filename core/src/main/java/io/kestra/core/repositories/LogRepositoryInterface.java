package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.statistics.LogStatistics;
import io.kestra.core.utils.DateUtils;
import io.kestra.plugin.core.dashboard.data.Logs;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;

public interface LogRepositoryInterface extends SaveRepositoryInterface<LogEntry>, QueryBuilderInterface<Logs.Fields> {
    /**
     * Finds all the log entries for the given tenant, execution and min log-level.
     * <p>
     * This method will verify the current user's permissions.
     *
     * @param tenantId          The tenant'sID.
     * @param executionId       The execution's ID.
     * @param minLevel          The minimum log-level.
     * @return The list of log entries.
     */
    List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel);

    /**
     * Finds all the log entries for the given tenant, execution and min log-level.
     * <p>
     * This method will NOT verify the current user's permissions.
     *
     * @param tenantId          The tenant'sID.
     * @param executionId       The execution's ID.
     * @param minLevel          The minimum log-level.
     * @return The list of log entries.
     */
    List<LogEntry> findByExecutionIdWithoutAcl(String tenantId, String executionId, Level minLevel);

    ArrayListTotal<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel, Pageable pageable);

    /**
     * This method is the same as {@link #findByExecutionId(String, String, Level)} but with
     * namespace and flow as additional parameters so that the logs are only found if it is an execution for this flow.
     * <p>
     * This method is designed to be used in tasks that must check that they are allowed to access the namespace of the execution.
     */
    List<LogEntry> findByExecutionId(String tenantId, String namespace, String flowId, String executionId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskIdWithoutAcl(String tenantId, String executionId, String taskId, Level minLevel);

    ArrayListTotal<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel, Pageable pageable);

    /**
     * This method is the same as {@link #findByExecutionIdAndTaskId(String, String, String, Level)} but with
     * namespace and flow as additional parameters so that the logs are only found if it is an execution for this flow.
     * <p>
     * This method is designed to be used in tasks that must check that they are allowed to access the namespace of the execution.
     */
    List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String namespace, String flowId, String executionId, String taskId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskRunIdWithoutAcl(String tenantId, String executionId, String taskRunId, Level minLevel);

    ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel, Pageable pageable);

    List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt);

    List<LogEntry> findByExecutionIdAndTaskRunIdAndAttemptWithoutAcl(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt);

    ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable);

    ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable String triggerId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    );

    List<LogStatistics> statistics(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy
    );

    LogEntry save(LogEntry log);

    Integer purge(Execution execution);

    void deleteByQuery(String tenantId, String executionId, String taskId, String taskRunId, Level minLevel, Integer attempt);

    void deleteByQuery(String tenantId, String namespace, String flowId, String triggerId);

    int deleteByQuery(String tenantId, String namespace, String flowId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate);
}
