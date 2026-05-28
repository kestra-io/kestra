package io.kestra.core.plugins.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.UriProvider;
import org.apache.commons.lang3.time.DurationFormatUtils;

public final class ExecutionService {
    private ExecutionService() {
    }

    public static Map<String, Object> executionMap(RunContext runContext, ExecutionInterface executionInterface) throws IllegalVariableEvaluationException {
        var executionRendererId = runContext.render(executionInterface.getExecutionId()).as(String.class).orElseThrow();

        var isCurrentExecution = isCurrentExecution(runContext, executionRendererId);
        if (isCurrentExecution) {
            runContext.logger().info("Loading execution data for the current execution.");
        }

        var executionContext = isCurrentExecution ? contextFromCurrentExecution(runContext) : contextFromTriggerExecution(runContext);
        UriProvider uriProvider = ((DefaultRunContext) runContext).services().uriProvider();

        Map<String, Object> templateRenderMap = new HashMap<>();
        templateRenderMap.put("duration", executionContext.state().humanDuration());
        templateRenderMap.put("startDate", executionContext.state().startDate());
        templateRenderMap.put("link", uriProvider.executionUrl(executionContext.tenantId(), executionContext.namespace(), executionContext.flowId(), executionContext.id()));
        templateRenderMap.put("execution", JacksonMapper.toMap(executionContext));

        runContext.render(executionInterface.getCustomMessage())
            .as(String.class)
            .ifPresent(s -> templateRenderMap.put("customMessage", s));

        final Map<String, Object> renderedCustomFields = runContext.render(executionInterface.getCustomFields()).asMap(String.class, Object.class);
        if (!renderedCustomFields.isEmpty()) {
            templateRenderMap.put("customFields", renderedCustomFields);
        }

        templateRenderMap.put("firstFailed", executionContext.firstFailed() != null ? executionContext.firstFailed() : false);
        templateRenderMap.put("lastTask", executionContext.lastTask());

        return templateRenderMap;
    }

    @SuppressWarnings("unchecked")
    private static ExecutionContext contextFromTriggerExecution(RunContext runContext) {
        Map<String, Object> executionMap = (Map<String, Object>) runContext.getVariables().get("execution");
        Map<String, Object> triggerVar = (Map<String, Object>) runContext.getVariables().get("trigger");
        if (triggerVar == null || !triggerVar.containsKey("executionId")) {
            throw new IllegalArgumentException("Trigger variable is missing 'executionId' key, notification plugins can only be used either on the current execution or an execution triggered by a Flow trigger.");
        }

        return new ExecutionContext(
            (String) executionMap.get("tenantId"),
            (String) triggerVar.get("namespace"),
            (String) triggerVar.get("flowId"),
            (String) triggerVar.get("executionId"),
            new ExecutionContextState(
                (String) triggerVar.get("state"),
                (String) triggerVar.get("startDate"),
                (String) triggerVar.get("endDate")
            ),
            triggerVar.containsKey("firstFailedTaskId") ? new ExecutionContextTask((String) triggerVar.get("firstFailedTaskId")) : null,
            triggerVar.containsKey("lastTaskId") ? new ExecutionContextTask((String) triggerVar.get("lastTaskId")) : null
        );
    }

    @SuppressWarnings("unchecked")
    private static ExecutionContext contextFromCurrentExecution(RunContext runContext) {
        Map<String, Object> executionMap = (Map<String, Object>) runContext.getVariables().get("execution");
        Map<String, Object> flowMap = (Map<String, Object>) runContext.getVariables().get("flow");
        LinkedHashMap<String, Object> tasksMap = (LinkedHashMap<String, Object>) runContext.getVariables().get("tasks");
        SequencedSet<Map.Entry<String, Object>> tasks = tasksMap != null ? tasksMap.sequencedEntrySet() : new LinkedHashMap<String, Object>().sequencedEntrySet();

        return new ExecutionContext(
            (String) flowMap.get("tenantId"),
            (String) flowMap.get("namespace"),
            (String) flowMap.get("id"),
            (String) executionMap.get("id"),
            new ExecutionContextState(
                (String) executionMap.get("state"),
                (String) executionMap.get("startDate"),
                (String) executionMap.get("endDate")
            ),
            extractFirstFailed(tasks),
            extractLastTask(tasks)
        );
    }

    private static ExecutionContextTask extractLastTask(SequencedSet<Map.Entry<String, Object>> tasks) {
        return tasks.isEmpty() ? null : extractTask(tasks.getLast());
    }

    @SuppressWarnings("unchecked")
    private static ExecutionContextTask extractFirstFailed(SequencedCollection<Map.Entry<String, Object>> tasks) {
        return tasks.stream()
            .filter(t -> "FAILED".equals(((Map<String, Object>) t.getValue()).get("state")))
            .findFirst()
            .map(t -> extractTask(t))
            .orElse(null);
    }

    private static ExecutionContextTask extractTask(Map.Entry<String, Object> task) {
        return new ExecutionContextTask(task.getKey());
    }

    @SuppressWarnings("unchecked")
    private static boolean isCurrentExecution(RunContext runContext, String executionId) {
        var executionVars = (Map<String, String>) runContext.getVariables().get("execution");
        return executionId.equals(executionVars.get("id"));
    }

    private record ExecutionContext(String tenantId, String namespace, String flowId, String id, ExecutionContextState state, ExecutionContextTask firstFailed, ExecutionContextTask lastTask) {}

    private record ExecutionContextState(String current, String startDate, String endDate) {
        public String humanDuration() {
            Duration duration = Duration.between(Instant.parse(startDate), Optional.ofNullable(endDate).map(d -> Instant.parse(d)).orElse(Instant.now()));
            return DurationFormatUtils.formatDurationHMS(duration.toMillis());
        }
    }

    private record ExecutionContextTask(String taskId) {}
}