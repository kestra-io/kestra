package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.queues.QueueException;

public interface AssetEmitter {
    void emit(AssetEmit assetEmit) throws QueueException;

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
