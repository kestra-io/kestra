package io.kestra.core.models.executions;

import java.util.Map;

/**
 * Utility class to hold a {@link TaskRun} and its outputs.
 * Must only be used as a temporary carrier for methods that must return both.
 */
public record TaskRunWithOutput(TaskRun taskRun, Map<String, Object> outputs) {
}
