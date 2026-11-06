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
}
