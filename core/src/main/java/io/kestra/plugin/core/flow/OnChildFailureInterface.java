package io.kestra.plugin.core.flow;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.Enums;

/**
 * Marks a flowable task that can interrupt its still-running children as soon as one of them fails,
 * instead of letting them run to completion while the flowable itself is already terminal.
 */
public interface OnChildFailureInterface {
    Property<OnChildFailure> getOnChildFailure();

    /**
     * Every task run under {@code parentTaskRun}, at any depth, that has not yet terminated.
     */
    default List<TaskRun> nonTerminatedChildrenTaskRuns(Execution execution, TaskRun parentTaskRun) {
        return execution.findAllChildren(parentTaskRun).stream()
            .filter(taskRun -> !taskRun.getState().isTerminated())
            .toList();
    }

    enum OnChildFailure {
        CONTINUE,
        CANCEL,
        FAIL;

        @JsonCreator
        public static OnChildFailure fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, OnChildFailure.class, CONTINUE);
        }

        /**
         * The task-run state to apply to interrupted children.
         *
         * @throws IllegalStateException if called on {@link #CONTINUE}, which never trigger an interrupt.
         */
        public State.Type toTaskRunState() {
            return switch (this) {
                case CANCEL -> State.Type.CANCELLED;
                case FAIL -> State.Type.FAILED;
                case CONTINUE -> throw new IllegalStateException("No task run state is defined for onChildFailure value '%s'.".formatted(this));
            };
        }
    }
}
