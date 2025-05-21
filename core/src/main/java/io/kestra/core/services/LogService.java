package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;

@Singleton
public class LogService {
    private static final String FLOW_PREFIX_WITH_TENANT = "[tenant: {}] [namespace: {}] [flow: {}] ";
    private static final String EXECUTION_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[execution: {}] ";
    private static final String TRIGGER_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[trigger: {}] ";
    private static final String TASKRUN_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[task: {}] [execution: {}] [taskrun: {}] ";

    private final LogRepositoryInterface logRepository;

    @Inject
    public LogService(LogRepositoryInterface logRepository) {
        this.logRepository = logRepository;
    }

    public void logExecution(FlowId flow, Logger logger, Level level, String message, Object... args) {
        String finalMsg = FLOW_PREFIX_WITH_TENANT + message;
        Object[] executionArgs = new Object[] { flow.getTenantId(), flow.getNamespace(), flow.getId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        logger.atLevel(level).log(finalMsg, finalArgs);
    }

    /**
     * Log an execution via the execution logger named: 'execution.{flowId}'.
     */
    public void logExecution(Execution execution, Level level, String message, Object... args) {
        Logger logger = logger(execution);
        logExecution(execution, logger, level, message, args);
    }

    public void logExecution(Execution execution, Logger logger, Level level, String message, Object... args) {
        Object[] executionArgs = new Object[] { execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        logger.atLevel(level).log(EXECUTION_PREFIX_WITH_TENANT + message, finalArgs);
    }

    /**
     * Log a trigger via the trigger logger named: 'trigger.{flowId}.{triggereId}'.
     */
    public void logTrigger(TriggerContext triggerContext, Level level, String message, Object... args) {
        Logger logger = logger(triggerContext);
        logTrigger(triggerContext, logger, level, message, args);
    }

    public void logTrigger(TriggerContext triggerContext, Logger logger, Level level, String message, Object... args) {
        Object[] executionArgs = new Object[] { triggerContext.getTenantId(), triggerContext.getNamespace(), triggerContext.getFlowId(), triggerContext.getTriggerId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        logger.atLevel(level).log(TRIGGER_PREFIX_WITH_TENANT + message, finalArgs);
    }

    /**
     * Log a taskRun via the taskRun logger named: 'task.{flowId}.{taskId}'.
     */
    public void logTaskRun(TaskRun taskRun, Level level, String message, Object... args) {
        String prefix = TASKRUN_PREFIX_WITH_TENANT;
        String finalMsg = taskRun.getValue() == null ? prefix + message : prefix + "[value: {}] " + message;
        Object[] executionArgs = new Object[] { taskRun.getTenantId(), taskRun.getNamespace(), taskRun.getFlowId(), taskRun.getTaskId(), taskRun.getExecutionId(), taskRun.getId() };
        if (taskRun.getValue() != null) {
            executionArgs = ArrayUtils.add(executionArgs, taskRun.getValue());
        }
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        Logger logger = logger(taskRun);
        logger.atLevel(level).log(finalMsg, finalArgs);
    }

    public int purge(String tenantId, String namespace, String flowId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate) {
        return logRepository.deleteByQuery(tenantId, namespace, flowId, logLevels, startDate, endDate);
    }

    /**
     * Fetch the error logs of an execution.
     * Will limit the results to the first 25 error logs, ordered by timestamp asc.
     */
    public List<LogEntry> errorLogs(String tenantId, String executionId) {
        return logRepository.findByExecutionId(tenantId, executionId, Level.ERROR, Pageable.from(1, 25, Sort.of(Sort.Order.asc("timestamp"))));
    }

    private Logger logger(TaskRun taskRun) {
        return LoggerFactory.getLogger(
            "task." + taskRun.getFlowId() + "." + taskRun.getTaskId()
        );
    }

    private Logger logger(TriggerContext triggerContext) {
        return LoggerFactory.getLogger(
            "trigger." + triggerContext.getFlowId() + "." + triggerContext.getTriggerId()
        );
    }

    private Logger logger(Execution execution) {
        return LoggerFactory.getLogger(
            "execution." + execution.getFlowId()
        );
    }
}
