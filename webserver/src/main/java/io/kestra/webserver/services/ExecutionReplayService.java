package io.kestra.webserver.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Checks that an execution can be replayed, shared by the execution API and the agent tools.
 */
@Singleton
public class ExecutionReplayService {
    private static final List<String> REPLAY_IGNORED_CHANGED_ATTRIBUTES = List.of("description", "retry", "timeout", "workerSelector", "allowFailure", "allowWarning");

    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;

    @Inject
    public ExecutionReplayService(final FlowRepositoryInterface flowRepository, final FlowService flowService) {
        this.flowRepository = flowRepository;
        this.flowService = flowService;
    }

    /**
     * Rejects a replay of {@code execution}, from {@code taskRunId} when set and onto {@code revision} when set.
     *
     * @return the flow revision the replay will run
     * @throws ConflictException if the execution is not replayable, or the revision is incompatible with a replay from the task run
     * @throws NoSuchElementException if the revision does not exist
     * @throws IllegalArgumentException if the revision is a draft and the execution is not a playground one
     */
    public Flow validateReplay(final Execution execution, @Nullable final String taskRunId, @Nullable final Integer revision) throws InternalException {
        controlRevision(execution, revision);
        controlReplayable(execution, taskRunId, revision);

        Flow flow = flowService.getFlowIfExecutableOrThrow(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), Optional.ofNullable(revision));
        ExecutionCreationService.controlDraftExecutableAs(flow, execution.getKind());
        return flow;
    }

    /**
     * @throws NoSuchElementException if {@code revision} is set and is not a revision of the execution's flow
     */
    public void controlRevision(final Execution execution, @Nullable final Integer revision) {
        if (revision != null) {
            Optional<Flow> flowRevision = this.flowRepository.findById(
                execution.getTenantId(),
                execution.getNamespace(),
                execution.getFlowId(),
                Optional.of(revision)
            );

            if (flowRevision.isEmpty()) {
                throw new NoSuchElementException(
                    "Unable to find revision " + revision +
                        " on flow " + execution.getNamespace() + "." + execution.getFlowId()
                );
            }
        }
    }

    private void controlReplayable(Execution execution, @Nullable String taskRunId, @Nullable Integer revision) throws InternalException {
        State state = execution.getState();
        // the Playground replays its own execution that is suspended on a breakpoint to reuse the tasks already run
        boolean isSuspendedPlayground = ExecutionKind.PLAYGROUND == execution.getKind() && (state.isBreakpoint() || state.isPaused());
        if (!state.isTerminated() && !isSuspendedPlayground) {
            throw new ConflictException(
                "Cannot replay execution: current state is '%s', expected terminated.".formatted(state.getCurrent())
            );
        }

        // if we want to replay from a taskrun to a different revision, we must check that both revisions are compatible
        // TODO this will load two times the flow as controlRevision already loaded it once and is called just before
        if (taskRunId != null && revision != null && !execution.getFlowRevision().equals(revision)) {
            Flow executionflow = this.flowRepository.findByExecution(execution);
            Flow newFlow = this.flowRepository.findById(
                execution.getTenantId(),
                execution.getNamespace(),
                execution.getFlowId(),
                Optional.of(revision)
            ).orElseThrow(FlowNotFoundException::new);
            TaskRun taskRun = execution.findTaskRunByTaskRunId(taskRunId);

            // 1. refuse if any task prior to this taskrun changed, looking inside flowable tasks recursively
            controlReplayTasksUnchanged(executionflow.getTasks(), newFlow.getTasks(), taskRun.getTaskId(), taskRunId, revision);

            // 2. refuse if any input definition is changed
            for (Input<?> originalInput : ListUtils.emptyOnNull(executionflow.getInputs())) {
                Input<?> newInput = ListUtils.emptyOnNull(newFlow.getInputs()).stream()
                    .filter(input -> input.getId().equals(originalInput.getId()))
                    .findFirst()
                    .orElse(null);

                if (newInput == null) {
                    throw new ConflictException(
                        "Cannot replay from taskrun '%s' to revision '%s' as input '%s' has been removed. Replay the whole execution instead."
                            .formatted(taskRunId, revision, originalInput.getId())
                    );
                }

                if (!originalInput.getType().equals(newInput.getType())) {
                    throw new ConflictException(
                        "Cannot replay from taskrun '%s' to revision '%s' as input '%s' type has changed. Replay the whole execution instead."
                            .formatted(taskRunId, revision, originalInput.getId())
                    );
                }

                if (!Objects.equals(originalInput.getDefaults(), newInput.getDefaults())) {
                    throw new ConflictException(
                        "Cannot replay from taskrun '%s' to revision '%s' as input '%s' defaults have changed. Replay the whole execution instead."
                            .formatted(taskRunId, revision, originalInput.getId())
                    );
                }
            }

            // 3. Refuse if an input has been added
            for (Input<?> newInput : ListUtils.emptyOnNull(newFlow.getInputs())) {
                Input<?> originalInput = ListUtils.emptyOnNull(executionflow.getInputs()).stream()
                    .filter(input -> input.getId().equals(newInput.getId()))
                    .findFirst()
                    .orElse(null);

                if (originalInput == null) {
                    throw new ConflictException(
                        "Cannot replay from taskrun '%s' to revision '%s' as input '%s' has been added. Replay the whole execution instead."
                            .formatted(taskRunId, revision, newInput.getId())
                    );
                }
            }

            // 4. refuse if any variable definition changed
            for (Map.Entry<String, Object> originalVar : MapUtils.emptyOnNull(executionflow.getVariables()).entrySet()) {
                Object newVar = MapUtils.emptyOnNull(newFlow.getVariables()).get(originalVar.getKey());

                if (newVar == null) {
                    throw new ConflictException(
                        "Cannot replay from taskrun '%s' to revision '%s' as variable '%s' has been removed. Replay the whole execution instead."
                            .formatted(taskRunId, revision, originalVar.getKey())
                    );
                }

                if (!originalVar.getValue().equals(newVar)) {
                    throw new ConflictException(
                        "Cannot replay from taskrun '%s' to revision '%s' as variable '%s' has changed. Replay the whole execution instead."
                            .formatted(taskRunId, revision, originalVar.getKey())
                    );
                }
            }
        }
    }

    // A flowable containing the task to replay from is compared without its children, which are then compared recursively.
    private boolean controlReplayTasksUnchanged(List<Task> originalTasks, List<Task> newTasks, String targetTaskId, String taskRunId, Integer revision) {
        for (int i = 0; i < originalTasks.size(); i++) {
            Task originalTask = originalTasks.get(i);
            Task newTask = newTasks.size() > i ? newTasks.get(i) : null;
            if (newTask == null) {
                throw new ConflictException(
                    "Cannot replay from taskrun '%s' to revision '%s' as task '%s' has been removed. Replay the whole execution instead.".formatted(taskRunId, revision, originalTask.getId())
                );
            }
            if (!originalTask.getId().equals(newTask.getId())) {
                throw new ConflictException(
                    "Cannot replay from taskrun '%s' to revision '%s' as task '%s' has been moved. Replay the whole execution instead.".formatted(taskRunId, revision, originalTask.getId())
                );
            }
            if (originalTask.getId().equals(targetTaskId)) {
                return true;
            }
            if (!originalTask.getType().equals(newTask.getType())) {
                throw new ConflictException(
                    "Cannot replay from taskrun '%s' to revision '%s' as task '%s' type has changed. Replay the whole execution instead.".formatted(taskRunId, revision, originalTask.getId())
                );
            }

            boolean isFlowable = originalTask.isFlowable();
            Map<String, Object> originalTaskAsMap = JacksonMapper.toMap(originalTask);
            Map<String, Object> newTaskAsMap = JacksonMapper.toMap(newTask);
            originalTaskAsMap.forEach((key, value) ->
            {
                if (REPLAY_IGNORED_CHANGED_ATTRIBUTES.contains(key) || (isFlowable && containsTask(value))) {
                    return;
                }
                if (!Objects.equals(value, newTaskAsMap.get(key))) {
                    throw new ConflictException(
                        "Cannot replay from taskrun '%s' to revision '%s' as task '%s' has changed. Replay the whole execution instead.".formatted(taskRunId, revision, originalTask.getId())
                    );
                }
            });

            if (isFlowable && controlReplayTasksUnchanged(((FlowableTask<?>) originalTask).allChildTasks(), ((FlowableTask<?>) newTask).allChildTasks(), targetTaskId, taskRunId, revision)) {
                return true;
            }
        }
        return false;
    }

    // A serialized task is a map holding both an id and a type.
    private static boolean containsTask(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (map.containsKey("id") && map.containsKey("type")) || map.values().stream().anyMatch(ExecutionReplayService::containsTask);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(ExecutionReplayService::containsTask);
        }
        return false;
    }

}
