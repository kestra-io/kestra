package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;

@Singleton
public class LogService {
    private static final String FLOW_PREFIX_NO_TENANT = "[namespace: {}] [flow: {}] ";
    private static final String FLOW_PREFIX_WITH_TENANT = "[tenant: {}] [namespace: {}] [flow: {}] ";

    private static final String EXECUTION_PREFIX_NO_TENANT = FLOW_PREFIX_NO_TENANT + "[execution: {}] ";
    private static final String EXECUTION_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[execution: {}] ";

    private static final String TRIGGER_PREFIX_NO_TENANT = FLOW_PREFIX_NO_TENANT + "[trigger: {}] ";
    private static final String TRIGGER_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[trigger: {}] ";

    private static final String TASKRUN_PREFIX_NO_TENANT = FLOW_PREFIX_NO_TENANT + "[task: {}] [execution: {}] [taskrun: {}] ";
    private static final String TASKRUN_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[task: {}] [execution: {}] [taskrun: {}] ";

    @Value("${kestra.ee.tenants.enabled:false}")
    private boolean tenantEnabled;

    @Inject
    LogRepositoryInterface logRepository;

    public void logFlow(Flow flow, Logger logger, Level level, String message, Object... args) {
        String finalMsg = tenantEnabled ? FLOW_PREFIX_WITH_TENANT + message : FLOW_PREFIX_NO_TENANT + message;
        Object[] executionArgs = tenantEnabled ?
            new Object[] { flow.getTenantId(), flow.getNamespace(), flow.getId() } :
            new Object[] { flow.getNamespace(), flow.getId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        logger.atLevel(level).log(finalMsg, finalArgs);
    }

    public void logExecution(Execution execution, Logger logger, Level level, String message, Object... args) {
        String finalMsg = tenantEnabled ? EXECUTION_PREFIX_WITH_TENANT + message : EXECUTION_PREFIX_NO_TENANT + message;
        Object[] executionArgs = tenantEnabled ?
            new Object[] { execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId() } :
            new Object[] { execution.getNamespace(), execution.getFlowId(), execution.getId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        logger.atLevel(level).log(finalMsg, finalArgs);
    }

    public void logTrigger(TriggerContext triggerContext, Logger logger, Level level, String message, Object... args) {
        String finalMsg = tenantEnabled ? TRIGGER_PREFIX_WITH_TENANT + message : TRIGGER_PREFIX_NO_TENANT + message;
        Object[] executionArgs = tenantEnabled ?
            new Object[] { triggerContext.getTenantId(), triggerContext.getNamespace(), triggerContext.getFlowId(), triggerContext.getTriggerId() } :
            new Object[] { triggerContext.getNamespace(), triggerContext.getFlowId(), triggerContext.getTriggerId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        logger.atLevel(level).log(finalMsg, finalArgs);
    }

    public void logTaskRun(TaskRun taskRun, Logger logger, Level level, String message, Object... args) {
        String prefix = tenantEnabled ? TASKRUN_PREFIX_WITH_TENANT : TASKRUN_PREFIX_NO_TENANT;
        String finalMsg = taskRun.getValue() == null ? prefix + message : prefix + "[value: {}] " + message;
        Object[] executionArgs = tenantEnabled ?
            new Object[] { taskRun.getTenantId(), taskRun.getNamespace(), taskRun.getFlowId(), taskRun.getTaskId(), taskRun.getExecutionId(), taskRun.getId() } :
            new Object[] { taskRun.getNamespace(), taskRun.getFlowId(), taskRun.getTaskId(), taskRun.getExecutionId(), taskRun.getId() };
        if (taskRun.getValue() != null) {
            executionArgs = ArrayUtils.add(executionArgs, taskRun.getValue());
        }
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        logger.atLevel(level).log(finalMsg, finalArgs);
    }

    public int purge(String tenantId, String namespace, String flowId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate) {
        return logRepository.deleteByQuery(tenantId, namespace, flowId, logLevels, startDate, endDate);
    }
}
