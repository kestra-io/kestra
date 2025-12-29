package io.kestra.core.plugins.notifications;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.RetryUtils;
import io.kestra.core.utils.UriProvider;

import java.time.Duration;
import java.util.*;

public final class ExecutionService {
    private ExecutionService() {}

    public static Execution findExecution(RunContext runContext, Property<String> executionId) throws IllegalVariableEvaluationException, NoSuchElementException {
        ExecutionRepositoryInterface executionRepository = ((DefaultRunContext) runContext).getApplicationContext().getBean(ExecutionRepositoryInterface.class);

        RetryUtils.Instance<Execution, NoSuchElementException> retryInstance = RetryUtils
            .of(Exponential.builder()
                    .delayFactor(2.0)
                    .interval(Duration.ofSeconds(1))
                    .maxInterval(Duration.ofSeconds(15))
                    .maxAttempts(-1)
                    .maxDuration(Duration.ofMinutes(10))
                    .build(),
                runContext.logger()
            );

        var executionRendererId = runContext.render(executionId).as(String.class).orElse(null);
        var flowTriggerExecutionState = getOptionalFlowTriggerExecutionState(runContext);

        var flowVars = (Map<String, String>) runContext.getVariables().get("flow");
        var isCurrentExecution = isCurrentExecution(runContext, executionRendererId);
        if (isCurrentExecution) {
            runContext.logger().info("Loading execution data for the current execution.");
        }

        return retryInstance.run(
            NoSuchElementException.class,
            () -> executionRepository.findById(flowVars.get("tenantId"), executionRendererId)
                .filter(foundExecution -> isExecutionInTheWantedState(foundExecution, isCurrentExecution, flowTriggerExecutionState))
                .orElseThrow(() -> new NoSuchElementException("Unable to find execution '" + executionRendererId + "'"))

        );
    }

    /**
     * ExecutionRepository can be out of sync in ElasticSearch stack, with this filter we try to mitigate that
     *
     * @param execution                 the Execution we fetched from ExecutionRepository
     * @param isCurrentExecution        true if this *Execution Task is configured to send a notification for the current Execution
     * @param flowTriggerExecutionState the Execution State that triggered the Flow trigger, if any
     * @return true if we think we fetched the right Execution data for our usecase
     */
    public static boolean isExecutionInTheWantedState(Execution execution, boolean isCurrentExecution, Optional<String> flowTriggerExecutionState) {
        if (isCurrentExecution) {
            // we don't wait for current execution to be terminated as it could not be possible as long as this task is running
            return true;
        }

        if (flowTriggerExecutionState.isPresent()) {
            // we were triggered by a Flow trigger that can be, for example: PAUSED
            if (flowTriggerExecutionState.get().equals(State.Type.RUNNING.toString())) {
                // RUNNING special case: we take the first state we got
                return true;
            } else {
                // to handle the case where the ExecutionRepository is out of sync in ElasticSearch stack,
                // we try to match an Execution with the same state
                return execution.getState().getCurrent().name().equals(flowTriggerExecutionState.get());
            }
        } else {
            return execution.getState().getCurrent().isTerminated();
        }
    }

    public static Map<String, Object> executionMap(RunContext runContext, ExecutionInterface executionInterface) throws IllegalVariableEvaluationException {
        Execution execution = findExecution(runContext, executionInterface.getExecutionId());
        UriProvider uriProvider = ((DefaultRunContext) runContext).getApplicationContext().getBean(UriProvider.class);

        Map<String, Object> templateRenderMap = new HashMap<>();
        templateRenderMap.put("duration", execution.getState().humanDuration());
        templateRenderMap.put("startDate", execution.getState().getStartDate());
        templateRenderMap.put("link", uriProvider.executionUrl(execution));
        templateRenderMap.put("execution", JacksonMapper.toMap(execution));

        runContext.render(executionInterface.getCustomMessage())
            .as(String.class)
            .ifPresent(s -> templateRenderMap.put("customMessage", s));

        final Map<String, Object> renderedCustomFields = runContext.render(executionInterface.getCustomFields()).asMap(String.class, Object.class);
        if (!renderedCustomFields.isEmpty()) {
            templateRenderMap.put("customFields", renderedCustomFields);
        }

        var isCurrentExecution = isCurrentExecution(runContext, execution.getId());

        List<TaskRun> taskRuns;

        if (isCurrentExecution) {
            taskRuns = execution.getTaskRunList();
        } else {
            taskRuns = execution.getTaskRunList().stream()
                .filter(t -> (execution.hasFailed() ? State.Type.FAILED : State.Type.SUCCESS).equals(t.getState().getCurrent()))
                .toList();
        }

        if (!ListUtils.isEmpty(taskRuns)) {
            TaskRun lastTaskRun = taskRuns.getLast();
            templateRenderMap.put("firstFailed", State.Type.FAILED.equals(lastTaskRun.getState().getCurrent()) ? lastTaskRun : false);
            templateRenderMap.put("lastTask", lastTaskRun);
        }

        return templateRenderMap;
    }

    /**
     * if there is a state, we assume this is a Flow trigger with type: {@link io.kestra.plugin.core.trigger.Flow.Output}
     *
     * @return the state of the execution that triggered the Flow trigger, or empty if another usecase/trigger
     */
    private static Optional<String> getOptionalFlowTriggerExecutionState(RunContext runContext) {
        var triggerVar = Optional.ofNullable(
            runContext.getVariables().get("trigger")
        );
        return triggerVar.map(trigger -> ((Map<String, String>) trigger).get("state"));
    }

    private static boolean isCurrentExecution(RunContext runContext, String executionId) {
        var executionVars = (Map<String, String>) runContext.getVariables().get("execution");
        return executionId.equals(executionVars.get("id"));
    }
}