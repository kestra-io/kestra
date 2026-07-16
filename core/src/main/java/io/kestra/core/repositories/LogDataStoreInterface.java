package io.kestra.core.repositories;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.event.Level;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.runners.IndexingRepository;
import io.kestra.plugin.core.dashboard.data.Logs;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;

public interface LogDataStoreInterface extends IndexingRepository<LogEntry>, QueryBuilderInterface<Logs.Fields>, Plugin {
    /**
     * Whether this log store supports on-demand deletion of log entries ({@code purge}/{@code deleteByQuery}/{@code deleteByFilters}).
     * <p>
     * Backends whose entries can only expire via retention/TTL (e.g. GCP Cloud Logging) return {@code false}.
     * When {@code false}, all purge/delete methods must no-op and return {@code 0}, and must NOT call
     * {@link io.kestra.core.repositories.log.LogDataStoreAccessControl#onDeleteByQuery} — otherwise a delete
     * audit event would be published for a deletion that never happened. Callers already tolerate a {@code 0} result.
     *
     * @return {@code true} by default.
     */
    default boolean canPurge() {
        return true;
    }

    /**
     * How this log store paginates its {@link Page}-returning finds — {@link PaginationType#OFFSET}
     * (random-access pages with an exact total; the default for JDBC/Elasticsearch) or
     * {@link PaginationType#CURSOR} (forward-only cursor with no total, for stateless external stores).
     * <p>
     * The mode must be advertised independently of any single response because the UI cannot guarantee its first
     * request is page 1 (it deep-links/restores page N).
     *
     * @return {@link PaginationType#OFFSET} by default.
     */
    default PaginationType paginationType() {
        return PaginationType.OFFSET;
    }

    /**
     * Finds all the log entries for the given tenant, execution and min log-level.
     * <p>
     * This method will verify the current user's permissions.
     *
     * @param tenantId The tenant'sID.
     * @param executionId The execution's ID.
     * @param minLevel The minimum log-level.
     * @return The list of log entries.
     */
    List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel);

    /**
     * Finds all the log entries for the given tenant, execution and min log-level.
     * <p>
     * This method will NOT verify the current user's permissions.
     *
     * @param tenantId The tenant'sID.
     * @param executionId The execution's ID.
     * @param minLevel The minimum log-level.
     * @return The list of log entries.
     */
    List<LogEntry> findByExecutionIdWithoutAcl(String tenantId, String executionId, Level minLevel);

    Page<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel, Pageable pageable);

    /**
     * This method is the same as {@link #findByExecutionId(String, String, Level)} but with
     * namespace and flow as additional parameters so that the logs are only found if it is an execution for this flow.
     * <p>
     * This method is designed to be used in tasks that must check that they are allowed to access the namespace of the execution.
     */
    List<LogEntry> findByExecutionId(String tenantId, String namespace, String flowId, String executionId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskIdWithoutAcl(String tenantId, String executionId, String taskId, Level minLevel);

    Page<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel, Pageable pageable);

    /**
     * This method is the same as {@link #findByExecutionIdAndTaskId(String, String, String, Level)} but with
     * namespace and flow as additional parameters so that the logs are only found if it is an execution for this flow.
     * <p>
     * This method is designed to be used in tasks that must check that they are allowed to access the namespace of the execution.
     */
    List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String namespace, String flowId, String executionId, String taskId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel);

    List<LogEntry> findByExecutionIdAndTaskRunIdWithoutAcl(String tenantId, String executionId, String taskRunId, Level minLevel);

    Page<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel, Pageable pageable);

    List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt);

    List<LogEntry> findByExecutionIdAndTaskRunIdAndAttemptWithoutAcl(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt);

    Page<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable);

    Page<LogEntry> find(
        Pageable pageable,
        @Nullable String tenantId,
        List<QueryFilter> filters);

    Flux<LogEntry> findAsync(
        @Nullable String tenantId,
        List<QueryFilter> filters);

    Flux<LogEntry> findAllAsync(@Nullable String tenantId);

    LogEntry save(LogEntry log);

    Integer purge(Execution execution);

    Integer purge(List<Execution> executions);

    void deleteByQuery(String tenantId, String executionId, String taskId, String taskRunId, Level minLevel, Integer attempt);

    void deleteByQuery(String tenantId, String namespace, String flowId, String triggerId);

    void deleteByFilters(String tenantId, List<QueryFilter> filters);

    int deleteByQuery(String tenantId, String namespace, String flowId, String executionId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate, boolean purgeExecutionLogs,
        boolean purgeNonExecutionLogs, Integer batchSize);
}
