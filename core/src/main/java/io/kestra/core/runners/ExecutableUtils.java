package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.Storage;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.trace.propagation.ExecutionTextMapSetter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.stream.Streams;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static io.kestra.core.trace.Tracer.throwCallable;
import static io.kestra.core.utils.Rethrow.throwConsumer;

@Slf4j
public final class ExecutableUtils {

    public static final String TASK_VARIABLE_ITERATIONS = "iterations";
    public static final String TASK_VARIABLE_NUMBER_OF_BATCHES = "numberOfBatches";
    public static final String TASK_VARIABLE_SUBFLOW_OUTPUTS_BASE_URI = "subflowOutputsBaseUri";

    private ExecutableUtils() {
        // prevent initialization
    }

    public static State.Type guessState(Execution execution, boolean transmitFailed, boolean allowedFailure, boolean allowWarning) {
        if (transmitFailed &&
            (execution.getState().isFailed() || execution.getState().isPaused() || execution.getState().getCurrent() == State.Type.KILLED || execution.getState().getCurrent() == State.Type.WARNING)
        ) {
            State.Type finalState = (allowedFailure && execution.getState().isFailed()) ? State.Type.WARNING : execution.getState().getCurrent();
            return finalState.equals(State.Type.WARNING) && allowWarning ? State.Type.SUCCESS : finalState;
        } else {
            return State.Type.SUCCESS;
        }
    }

    public static SubflowExecutionResult subflowExecutionResult(TaskRun parentTaskrun, Execution execution) {
        List<TaskRunAttempt> attempts = parentTaskrun.getAttempts() == null ? new ArrayList<>() : new ArrayList<>(parentTaskrun.getAttempts());
        attempts.add(TaskRunAttempt.builder().state(parentTaskrun.getState()).build());
        return SubflowExecutionResult.builder()
            .executionId(execution.getId())
            .state(parentTaskrun.getState().getCurrent())
            .parentTaskRun(parentTaskrun.withAttempts(attempts))
            .build();
    }

    public static <T extends Task & ExecutableTask<?>> Optional<SubflowExecution<?>> subflowExecution(
        RunContext runContext,
        FlowExecutorInterface flowExecutorInterface,
        Execution currentExecution,
        Flow currentFlow,
        T currentTask,
        TaskRun currentTaskRun,
        Map<String, Object> inputs,
        List<Label> labels,
        boolean inheritLabels,
        Property<ZonedDateTime> scheduleDate
    ) throws IllegalVariableEvaluationException {
        // extract a trace context for propagation
        var openTelemetry = ((DefaultRunContext) runContext).getApplicationContext().getBean(OpenTelemetry.class);
        var propagator = openTelemetry.getPropagators().getTextMapPropagator();
        var tracerFactory = ((DefaultRunContext) runContext).getApplicationContext().getBean(TracerFactory.class);
        var tracer = tracerFactory.getTracer(currentTask.getClass(), "EXECUTOR");

        return tracer.inNewContext(
            currentExecution,
            currentTask.getType(),
            throwCallable(() -> {
            // If we are in a flow that is restarted, we search for existing run of the task to restart them
            if (currentExecution.getLabels() != null && currentExecution.getLabels().contains(new Label(Label.RESTARTED, "true"))
                && currentTask.getRestartBehavior() == ExecutableTask.RestartBehavior.RETRY_FAILED) {
                ExecutionRepositoryInterface executionRepository = ((DefaultRunContext) runContext).getApplicationContext().getBean(ExecutionRepositoryInterface.class);

                Optional<Execution> existingSubflowExecution = Optional.empty();
                if (currentTaskRun.getOutputs() != null && currentTaskRun.getOutputs().containsKey("executionId")) {
                    // we know which execution to restart; this should be the case for Subflow tasks
                    existingSubflowExecution = executionRepository.findById(currentExecution.getTenantId(), (String) currentTaskRun.getOutputs().get("executionId"));
                }

                if (existingSubflowExecution.isEmpty()) {
                    // otherwise, we try to find the correct one; this should be the case for ForEachItem tasks
                    List<Execution> childExecutions = executionRepository.findAllByTriggerExecutionId(currentExecution.getTenantId(), currentExecution.getId())
                        .filter(e -> e.getNamespace().equals(currentTask.subflowId().namespace()) && e.getFlowId().equals(currentTask.subflowId().flowId()) && e.getTrigger().getId().equals(currentTask.getId()))
                        .filter(e -> Objects.equals(e.getTrigger().getVariables().get("taskRunId"), currentTaskRun.getId()) && Objects.equals(e.getTrigger().getVariables().get("taskRunValue"), currentTaskRun.getValue()) && Objects.equals(e.getTrigger().getVariables().get("taskRunIteration"), currentTaskRun.getIteration()))
                        .collectList()
                        .block();

                    if (childExecutions != null && childExecutions.size() == 1) {
                        // if there are more than one, we ignore the results and create a new one
                        existingSubflowExecution = Optional.of(childExecutions.getFirst());
                    }
                }

                if (existingSubflowExecution.isPresent()) {
                    Execution subflowExecution = existingSubflowExecution.get();
                    if (!subflowExecution.getState().isFailed()) {
                        // don't restart it as it's terminated successfully
                        return Optional.empty();
                    }
                    ExecutionService executionService = ((DefaultRunContext) runContext).getApplicationContext().getBean(ExecutionService.class);
                    try {
                        Execution restarted = executionService.restart(subflowExecution, null);
                        // inject the traceparent into the new execution
                        propagator.inject(Context.current(), restarted, ExecutionTextMapSetter.INSTANCE);
                        return Optional.of(SubflowExecution.builder()
                            .parentTask(currentTask)
                            .parentTaskRun(currentTaskRun.withState(State.Type.RUNNING))
                            .execution(restarted)
                            .build());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            String subflowNamespace = runContext.render(currentTask.subflowId().namespace());
            String subflowId = runContext.render(currentTask.subflowId().flowId());
            Optional<Integer> subflowRevision = currentTask.subflowId().revision();

            Flow flow = flowExecutorInterface.findByIdFromTask(
                    currentExecution.getTenantId(),
                    subflowNamespace,
                    subflowId,
                    subflowRevision,
                    currentExecution.getTenantId(),
                    currentFlow.getNamespace(),
                    currentFlow.getId()
                )
                .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + subflowNamespace + "'.'" + subflowId + "' with revision '" + subflowRevision.orElse(0) + "'"));

            if (flow.isDisabled()) {
                throw new IllegalStateException("Cannot execute a flow which is disabled");
            }

            if (flow instanceof FlowWithException fwe) {
                throw new IllegalStateException("Cannot execute an invalid flow: " + fwe.getException());
            }

            List<Label> newLabels = inheritLabels ? new ArrayList<>(filterLabels(currentExecution.getLabels(), flow)) : new ArrayList<>(systemLabels(currentExecution));
            if (labels != null) {
                labels.forEach(throwConsumer(label -> newLabels.add(new Label(runContext.render(label.key()), runContext.render(label.value())))));
            }

            var variables = ImmutableMap.<String, Object>builder().putAll(Map.of(
                "executionId", currentExecution.getId(),
                "namespace", currentFlow.getNamespace(),
                "flowId", currentFlow.getId(),
                "flowRevision", currentFlow.getRevision(),
                "taskRunId", currentTaskRun.getId(),
                "taskId", currentTaskRun.getTaskId()
            ));
            if (currentTaskRun.getOutputs() != null) {
                variables.put("taskRunOutputs", currentTaskRun.getOutputs());
            }
            if (currentTaskRun.getValue() != null) {
                variables.put("taskRunValue", currentTaskRun.getValue());
            }
            if (currentTaskRun.getIteration() != null) {
                variables.put("taskRunIteration", currentTaskRun.getIteration());
            }

            FlowInputOutput flowInputOutput = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowInputOutput.class);
            Instant scheduleOnDate = runContext.render(scheduleDate).as(ZonedDateTime.class).map(date -> date.toInstant()).orElse(null);
            Execution execution = Execution
                .newExecution(
                    flow,
                    (f, e) -> flowInputOutput.readExecutionInputs(f, e, inputs),
                    newLabels,
                    Optional.empty())
                .withTrigger(ExecutionTrigger.builder()
                    .id(currentTask.getId())
                    .type(currentTask.getType())
                    .variables(variables.build())
                    .build()
                )
                .withScheduleDate(scheduleOnDate);
            // inject the traceparent into the new execution
            propagator.inject(Context.current(), execution, ExecutionTextMapSetter.INSTANCE);
            return Optional.of(SubflowExecution.builder()
                .parentTask(currentTask)
                .parentTaskRun(currentTaskRun.withState(State.Type.RUNNING))
                .execution(execution)
                .build());
        }));
    }

    private static List<Label> filterLabels(List<Label> labels, Flow flow) {
        if (ListUtils.isEmpty(flow.getLabels())) {
            return labels;
        }
        
        return labels.stream()
            .filter(label -> flow.getLabels().stream().noneMatch(flowLabel -> flowLabel.key().equals(label.key())))
            .toList();
    }

    private static List<Label> systemLabels(Execution execution) {
        return Streams.of(execution.getLabels())
            .filter(label -> label.key().startsWith(Label.SYSTEM_PREFIX))
            .toList();
    }

    @SuppressWarnings("unchecked")
    public static TaskRun manageIterations(Storage storage, TaskRun taskRun, Execution execution, boolean transmitFailed, boolean allowFailure, boolean allowWarning) throws InternalException {
        Integer numberOfBatches = (Integer) taskRun.getOutputs().get(TASK_VARIABLE_NUMBER_OF_BATCHES);
        var previousTaskRun = execution.findTaskRunByTaskRunId(taskRun.getId());
        if (previousTaskRun == null) {
            throw new IllegalStateException("Should never happen");
        }

        State.Type currentState = taskRun.getState().getCurrent();
        Optional<State.Type> previousState = taskRun.getState().getHistories().size() > 1 ?
            Optional.of(taskRun.getState().getHistories().get(taskRun.getState().getHistories().size() - 2).getState()) :
            Optional.empty();

        // search for the previous iterations, if not found, we init it with an empty map
        Map<String, Integer> iterations = !MapUtils.isEmpty(previousTaskRun.getOutputs()) ?
            (Map<String, Integer>) previousTaskRun.getOutputs().get(TASK_VARIABLE_ITERATIONS) :
            new HashMap<>();

        int currentStateIteration = iterations.getOrDefault(currentState.toString(), 0);
        iterations.put(currentState.toString(), currentStateIteration + 1);
        if (previousState.isPresent() && previousState.get() != currentState) {
            int previousStateIterations = iterations.getOrDefault(previousState.get().toString(), numberOfBatches);
            iterations.put(previousState.get().toString(), previousStateIterations - 1);

            if (previousState.get() == State.Type.RESTARTED) {
                // if we are in a restart, we need to reset the failed executions
                iterations.put(State.Type.FAILED.toString(), 0);
            }
        }

        // update the state to success if terminatedIterations == numberOfBatches
        int terminatedIterations = iterations.getOrDefault(State.Type.SUCCESS.toString(), 0) +
            iterations.getOrDefault(State.Type.FAILED.toString(), 0) +
            iterations.getOrDefault(State.Type.KILLED.toString(), 0) +
            iterations.getOrDefault(State.Type.WARNING.toString(), 0) +
            iterations.getOrDefault(State.Type.CANCELLED.toString(), 0);

        if (terminatedIterations == numberOfBatches) {
            State.Type state = transmitFailed ? findTerminalState(iterations, allowFailure, allowWarning) : State.Type.SUCCESS;
            final Map<String, Object> outputs = new HashMap<>();
            outputs.put(TASK_VARIABLE_ITERATIONS, iterations);
            outputs.put(TASK_VARIABLE_NUMBER_OF_BATCHES, numberOfBatches);
            outputs.put(TASK_VARIABLE_SUBFLOW_OUTPUTS_BASE_URI, storage.getContextBaseURI().getPath());

            return previousTaskRun
                .withIteration(taskRun.getIteration())
                .withOutputs(outputs)
                .withAttempts(Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(state)).build()))
                .withState(state);
        }

        // else we update the previous taskRun as it's the same taskRun that is still running
        return previousTaskRun
            .withIteration(taskRun.getIteration())
            .withOutputs(Map.of(
                TASK_VARIABLE_ITERATIONS, iterations,
                TASK_VARIABLE_NUMBER_OF_BATCHES, numberOfBatches
            ));
    }

    private static State.Type findTerminalState(Map<String, Integer> iterations, boolean allowFailure, boolean allowWarning) {
        if (iterations.getOrDefault(State.Type.FAILED.toString(), 0) > 0) {
            return allowFailure ? allowWarning ? State.Type.SUCCESS : State.Type.WARNING : State.Type.FAILED;
        }
        if (iterations.getOrDefault(State.Type.KILLED.toString(), 0) > 0) {
            return State.Type.KILLED;
        }
        if (iterations.getOrDefault(State.Type.WARNING.toString(), 0) > 0) {
            if (allowWarning) {
                return State.Type.SUCCESS;
            }
            return State.Type.WARNING;
        }
        return State.Type.SUCCESS;
    }

    public static SubflowExecutionResult subflowExecutionResultFromChildExecution(RunContext runContext, Flow flow, Execution execution, ExecutableTask<?> executableTask, TaskRun taskRun) {
        try {
            return executableTask
                .createSubflowExecutionResult(runContext, taskRun, flow, execution)
                .orElse(null);
        } catch (Exception e) {
            log.error("Unable to create the Subflow Execution Result", e);
            // we return a fail subflow execution result to end the flow
            return SubflowExecutionResult.builder()
                .executionId(execution.getId())
                .state(State.Type.FAILED)
                .parentTaskRun(taskRun.withState(State.Type.FAILED).withAttempts(List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build())))
                .build();
        }
    }

    public static boolean isSubflow(Execution execution) {
        return execution.getTrigger() != null && (
            "io.kestra.plugin.core.flow.Subflow".equals(execution.getTrigger().getType()) ||
                "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable".equals(execution.getTrigger().getType())
        );
    }
}
