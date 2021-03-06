package io.kestra.core.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.util.StringUtils;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.Rethrow.throwPredicate;

/**
 * Provides business logic to manipulate {@link Execution}
 */
@Singleton
public class ExecutionService {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    /**
     * Returns an execution that can be run from a specific task.
     * <p>
     * Two main cases may be distinguished :
     * <ul>
     *     <li>If a {@code referenceTaskId} is provided, a new execution (with a new execution id) is created. The
     *     returned execution will start from the the task whose id equals the {@code referenceTaskId}.
     *     If a task before the reference task is in failed state, an {@code IllegalArgumentException} will be thrown.</li>
     *     <li>If no {@code referenceTaskId} is provided, the {@code execution} (with the same execution id) is updated
     *     and returned so that it can be run from the last failed task.</li>
     * </ul>
     *
     * @param execution       The execution to get a restart execution from.
     * @param referenceTaskId The task identifier from which the restart should operate. If not provided, the restart
     *                        will operate from the last failed task.
     * @return an execution that can be run
     * @throws IllegalStateException    If provided execution is not in a terminated state.
     * @throws IllegalArgumentException If no referenceTaskId is provided or if there is no failed task. Also thrown if
     *                                  a {@code referenceTaskId} is provided but there is a failed task before the reference task.
     */
    public Execution restart(final Execution execution, String referenceTaskId) throws Exception {
        final Execution newExecution = this.getRestartExecution(execution, referenceTaskId);
        executionQueue.emit(newExecution);

        return newExecution;
    }

    private Execution getRestartExecution(final Execution execution, String referenceTaskId) throws Exception {
        if (!execution.getState().isTerninated()) {
            throw new IllegalStateException("Execution must be terminated to be restarted, " +
                "current state is '" + execution.getState().getCurrent() + "' !"
            );
        }

        if (StringUtils.hasText(referenceTaskId)) {
            return createRestartFromTaskId(execution, referenceTaskId);
        } else {
            return createRestartFromLastFailed(execution);
        }
    }

    /**
     * Returns the desired state for a specific {@link TaskRun} so that it can be restarted.
     * <p>
     * Let's say we want to restart an execution with the following task run list from task C2 :
     * <ul>
     *     <li>A</li>
     *     <li>B</li>
     *     <li>C</li>
     *     <li> - C1</li>
     *     <li> - C2</li>
     *     <li> - C3</li>
     *     <li>D</li>
     * </ul>
     * For this we need to put the task runs in the following states :
     * <ul>
     *     <li>A (RUNNING)</li>
     *     <li>B (RUNNING)</li>
     *     <li>C (RUNNING)</li>
     *     <li> - C1 (SUCCESS)</li>
     *     <li> - C2 (CREATED)</li>
     *     <li> - C3 (no state)</li>
     *     <li>D (no state)</li>
     * </ul>
     *
     * @param execution                 The execution.
     * @param taskRunIndex              The task run index in the execution task run's list.
     * @param referenceTaskRunIndex     The task run index from which we want to restart the execution.
     *                                  This is our reference task run to determine the state of the other tasks.
     * @param referenceTaskRunAncestors The task run identifier of the reference task run ancestors.
     * @return an execution that can be run
     * @throws IllegalArgumentException If the provided {@code taskRunIndex} is not valid
     */
    private State getRestartState(final Execution execution, int taskRunIndex, int referenceTaskRunIndex, Set<String> referenceTaskRunAncestors) throws IllegalArgumentException {
        if (taskRunIndex < 0 || taskRunIndex >= execution.getTaskRunList().size() || taskRunIndex > referenceTaskRunIndex)
            throw new IllegalArgumentException("Unable to determine restart state !");

        final TaskRun taskRun = execution.getTaskRunList().get(taskRunIndex);

        State state;

        if (taskRunIndex == referenceTaskRunIndex) {
            // The current task run is the reference task run, its default state will be RESTARTED
            state = new State(State.Type.RESTARTED);
        }
        else if (referenceTaskRunAncestors.contains(taskRun.getId())) {
            // The current task run is an ascendant of the reference task run
            state = new State(State.Type.RESTARTED)
                .withState(State.Type.RUNNING);
        }
        else {
            state = new State(State.Type.SUCCESS);
        }

        return state;
    }


    /**
     * Returns the provided {@code referenceTaskRunIndex} ancestors id
     *
     * @param execution             the execution
     * @param referenceTaskRunIndex The task run index for which we want to find the ancestors
     * @return a set containing the reference task run ancestors
     */
    private Set<String> getAncestors(final Execution execution, int referenceTaskRunIndex) throws IllegalArgumentException {
        final Set<String> ancestors = new HashSet<>();

        String nextAncestorId = null;
        int currentIndex = referenceTaskRunIndex;

        do {
            TaskRun taskRun = execution.getTaskRunList().get(currentIndex);

            if (nextAncestorId == null || taskRun.getId().equals(nextAncestorId)) {
                nextAncestorId = taskRun.getParentTaskRunId();
            }

            if (nextAncestorId != null) {
                ancestors.add(nextAncestorId);
            }

            currentIndex--;

        } while (currentIndex >= 0 && nextAncestorId != null);

        return ancestors;
    }

    /**
     * Returns an new execution based on the provided one that can be re-run from the task whose id equals
     * {@code referenceTaskId}
     *
     * @param execution       The provided execution.
     * @param referenceTaskId The task identifier from which the restart should operate.
     * @return A new execution that can be run.
     * @throws IllegalArgumentException If no referenceTaskId is provided or a task in failed state is found before the
     *                                  reference task.
     */
    private Execution createRestartFromTaskId(final Execution execution, String referenceTaskId) throws IllegalArgumentException {
        final Predicate<TaskRun> isNotReferenceTask = taskRun -> !(referenceTaskId.equals(taskRun.getTaskId()));
        final Predicate<TaskRun> isNotFailed = taskRun -> !taskRun.getState().getCurrent().equals(State.Type.FAILED);

        // Extract the reference task run index
        final long refTaskRunIndex = execution
            .getTaskRunList()
            .stream()
            .takeWhile(isNotFailed.and(isNotReferenceTask))
            .count();

        if (refTaskRunIndex == execution.getTaskRunList().size()) {
            throw new IllegalArgumentException("Task [" + referenceTaskId + "] does not exist !");
        }

        final String newExecutionId = IdUtils.create();

        // This map will be used to map old ids to new ids
        final Map<String, String> oldToNewIdsMap = new HashMap<>();
        final Set<String> refTaskRunAncestors = getAncestors(execution, (int) refTaskRunIndex);

        // Create new task run list
        List<TaskRun> newTaskRuns = IntStream
            .range(0, (int) refTaskRunIndex + 1)
            .mapToObj(currentIndex -> {
                final TaskRun originalTaskRun = execution.getTaskRunList().get(currentIndex);

                final State state = getRestartState(execution, currentIndex, (int) refTaskRunIndex, refTaskRunAncestors);

                final String newTaskRunId = IdUtils.create();

                // Map old taskRun id to new taskRun id
                oldToNewIdsMap.put(originalTaskRun.getId(), newTaskRunId);

                return originalTaskRun.forChildExecution(
                    newTaskRunId,
                    newExecutionId,
                    oldToNewIdsMap.get(originalTaskRun.getParentTaskRunId()),
                    state);
            })
            .collect(Collectors.toList());

        // Build and launch new execution
        return execution.childExecution(
            newExecutionId,
            newTaskRuns,
            new State()
                .withState(State.Type.RESTARTED)
        );
    }


    /**
     * Returns the provided execution updated to be run from the last failed task.
     *
     * @param execution the execution to restart
     * @return The provided execution that can be run again from the last failed task.
     * @throws IllegalArgumentException If there is no failed task.
     */
    private Execution createRestartFromLastFailed(final Execution execution) throws Exception {
        final Flow flow = flowRepositoryInterface.findByExecution(execution);

        final Predicate<TaskRun> notLastFailed = throwPredicate(taskRun -> {
            boolean isFailed = taskRun.getState().getCurrent().equals(State.Type.FAILED);
            boolean isFlowable = Optional.of(flow)
                .map(throwFunction(f -> f.findTaskByTaskId(taskRun.getTaskId()).isFlowable()))
                .orElse(false);

            return !isFailed || isFlowable;
        });

        // Find first failed task run
        final long refTaskRunIndex = execution
            .getTaskRunList()
            .stream()
            .takeWhile(notLastFailed)
            .count();

        if (refTaskRunIndex == execution.getTaskRunList().size()) {
            throw new IllegalArgumentException("No failed task found to restart execution from !");
        }

        final Set<String> refTaskRunAncestors = getAncestors(execution, (int) refTaskRunIndex);

        final Execution toRestart = execution
            .withState(State.Type.RESTARTED)
            .withTaskRunList(execution.getTaskRunList().subList(0, (int) refTaskRunIndex + 1));

        IntStream
            .range(0, (int) refTaskRunIndex + 1)
            .forEach(i -> {
                TaskRun originalTaskRun = execution.getTaskRunList().get(i);

                // Update original task run state
                State state = getRestartState(execution, i, (int) refTaskRunIndex, refTaskRunAncestors);

                if (!originalTaskRun.getState().getCurrent().equals(state.getCurrent())) {
                    for (State.History history : state.getHistories()) {
                        originalTaskRun = originalTaskRun.withState(history.getState());
                    }
                }

                toRestart.getTaskRunList().set(i, originalTaskRun);
            });

        return toRestart;
    }
}
