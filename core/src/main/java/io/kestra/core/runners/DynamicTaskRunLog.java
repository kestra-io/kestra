package io.kestra.core.runners;

import org.slf4j.event.Level;

/**
 * A log line to attach to a dynamically-generated taskrun when it is registered through
 * {@link RunContext#dynamicWorkerResult(WorkerTaskResult, java.util.List)}.
 * <p>
 * Callers only provide the level and message — the execution, tenant, namespace, flow, taskrun
 * identity and attempt are filled in from the run context, and secrets are masked, so a plugin
 * never builds (nor can tamper with) a full {@link io.kestra.core.models.executions.LogEntry}.
 */
public record DynamicTaskRunLog(Level level, String message) {
}
