package io.kestra.core.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.TriggerContext;

/**
 * Utility class for server logging
 */
public final class Logs {

    private static final String FLOW_PREFIX_WITH_TENANT = "[tenant: {}] [namespace: {}] [flow: {}] ";
    private static final String EXECUTION_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[execution: {}] ";
    private static final String TRIGGER_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[trigger: {}] ";
    private static final String TASKRUN_PREFIX_WITH_TENANT = FLOW_PREFIX_WITH_TENANT + "[task: {}] [execution: {}] [taskrun: {}] ";

    private Logs() {
    }

    public static void logExecution(FlowId flow, Logger logger, Level level, String message, Object... args) {
        String finalMsg = FLOW_PREFIX_WITH_TENANT + message;
        Object[] executionArgs = new Object[] { flow.getTenantId(), flow.getNamespace(), flow.getId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        try (var ignored = mdcScope(
            "tenantId", flow.getTenantId(),
            "namespace", flow.getNamespace(),
            "flowId", flow.getId()
        )) {
            logger.atLevel(level).log(finalMsg, finalArgs);
        }
    }

    /**
     * Log an {@link Execution} via the executor logger named: 'executor.{tenantId}.{namespace}.{flowId}'.
     */
    public static void logExecution(Execution execution, Level level, String message, Object... args) {
        Logger logger = logger(execution);
        logExecution(execution, logger, level, message, args);
    }

    public static void logExecution(Execution execution, Logger logger, Level level, String message, Object... args) {
        Object[] executionArgs = new Object[] { execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        try (var ignored = mdcScope(
            "tenantId", execution.getTenantId(),
            "namespace", execution.getNamespace(),
            "flowId", execution.getFlowId(),
            "executionId", execution.getId()
        )) {
            logger.atLevel(level).log(EXECUTION_PREFIX_WITH_TENANT + message, finalArgs);
        }
    }

    /**
     * Log a {@link TriggerContext} via the scheduler logger named: 'trigger.{tenantId}.{namespace}.{flowId}.{triggerId}'.
     */
    public static void logTrigger(TriggerContext triggerContext, Level level, String message, Object... args) {
        Logger logger = logger(triggerContext);
        logTrigger(triggerContext, logger, level, message, args);
    }

    public static void logTrigger(TriggerContext triggerContext, Logger logger, Level level, String message, Object... args) {
        Object[] executionArgs = new Object[] { triggerContext.getTenantId(), triggerContext.getNamespace(), triggerContext.getFlowId(), triggerContext.getTriggerId() };
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        try (var ignored = mdcScope(
            "tenantId", triggerContext.getTenantId(),
            "namespace", triggerContext.getNamespace(),
            "flowId", triggerContext.getFlowId(),
            "triggerId", triggerContext.getTriggerId()
        )) {
            logger.atLevel(level).log(TRIGGER_PREFIX_WITH_TENANT + message, finalArgs);
        }
    }

    /**
     * Log a {@link TaskRun} via the worker logger named: 'worker.{tenantId}.{namespace}.{flowId}.{taskId}'.
     */
    public static void logTaskRun(TaskRun taskRun, Level level, String message, Object... args) {
        String prefix = TASKRUN_PREFIX_WITH_TENANT;
        String finalMsg = taskRun.getValue() == null ? prefix + message : prefix + "[value: {}] " + message;
        Object[] executionArgs = new Object[] { taskRun.getTenantId(), taskRun.getNamespace(), taskRun.getFlowId(), taskRun.getTaskId(), taskRun.getExecutionId(), taskRun.getId() };
        if (taskRun.getValue() != null) {
            executionArgs = ArrayUtils.add(executionArgs, taskRun.getValue());
        }
        Object[] finalArgs = ArrayUtils.addAll(executionArgs, args);
        Logger logger = logger(taskRun);
        try (var ignored = mdcScope(
            "tenantId", taskRun.getTenantId(),
            "namespace", taskRun.getNamespace(),
            "flowId", taskRun.getFlowId(),
            "taskId", taskRun.getTaskId(),
            "executionId", taskRun.getExecutionId(),
            "taskRunId", taskRun.getId()
        )) {
            logger.atLevel(level).log(finalMsg, finalArgs);
        }
    }

    private static Logger logger(TaskRun taskRun) {
        return LoggerFactory.getLogger(
            "worker." + taskRun.getTenantId() + "." + taskRun.getNamespace() + "." + taskRun.getFlowId() + "." + taskRun.getTaskId()
        );
    }

    private static Logger logger(TriggerContext triggerContext) {
        return LoggerFactory.getLogger(
            "scheduler." + triggerContext.getTenantId() + "." + triggerContext.getNamespace() + "." + triggerContext.getFlowId() + "." + triggerContext.getTriggerId()
        );
    }

    private static Logger logger(Execution execution) {
        return LoggerFactory.getLogger(
            "executor." + execution.getTenantId() + "." + execution.getNamespace() + "." + execution.getFlowId()
        );
    }

    /**
     * Populates SLF4J MDC for the duration of a try-with-resources block, then removes
     * only the keys it set. Skips null values.
     */
    private static MDCScope mdcScope(String... kvPairs) {
        List<String> putKeys = new ArrayList<>(kvPairs.length / 2);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            if (kvPairs[i + 1] != null) {
                MDC.put(kvPairs[i], kvPairs[i + 1]);
                putKeys.add(kvPairs[i]);
            }
        }
        return () -> putKeys.forEach(MDC::remove);
    }

    /**
     * Narrows {@link AutoCloseable#close()} to remove the {@code throws Exception},
     * so callers don't need a {@code try/catch} around a plain MDC cleanup.
     */
    private interface MDCScope extends AutoCloseable {
        @Override
        void close();
    }
}
