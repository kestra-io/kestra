package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.QueueException;

public interface AssetEmitter {
    void emit(AssetEmit assetEmit) throws QueueException;

    /**
     * Emits assets on behalf of a dynamic task run the emitting task created, so their lineage and usage belong to it instead of to the emitting task run.
     * The emit is sent once the attempt ends, together with the dynamic task runs the task registered, and dropped when the task never registers that task run.
     * It never locks its outputs, since a dynamic task run never ends an attempt that would release them, and it is not part of {@link #emitted()}.
     *
     * @param dynamicTaskRun a task run whose parent is the emitting task run, in the state it ended in
     * @throws IllegalArgumentException when {@code dynamicTaskRun} is not a child of the emitting task run
     */
    default void emitFor(TaskRun dynamicTaskRun, AssetEmit assetEmit) throws QueueException {
        emit(assetEmit);
    }

    List<AssetEmit> emitted();

    /**
     * @return why the emits refused by the backend were refused, so the task can apply its {@code assetFailureBehavior}.
     */
    default List<String> failures() {
        return List.of();
    }

    /**
     * @return why outputs of a locking emit were refused because another owner holds their lock; refused at the start of an attempt, they keep the task from running when its assetFailureBehavior is FAIL.
     */
    default List<String> lockRefusals() {
        return List.of();
    }
}
