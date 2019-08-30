package org.floworc.core.tasks;

import org.floworc.core.executions.Execution;

import java.util.List;
import java.util.Optional;

public interface FlowableTask {

    default boolean hasChildTasks() {
        return false;
    }

    /**
     * Return list of childs tasks for current execution
     *
     * @param execution current execution to allow filtering of the task ready to be consumed
     * @return list of task ready to be consumed: <ul>
     *       <li>{@link Optional#empty()}: no childs tasks or no more tasks available.</li>
     *       <li>{@link Optional#of(Object)} with empty list: no childs available for now, retry later.</li>
     *       <li>{@link Optional#of(Object)} with a non empty list: all childs that must be run now.</li>
     *     </ul>
     */
    default Optional<List<Task>> getChildTasks(Execution execution) {
        return Optional.empty();
    }
}
