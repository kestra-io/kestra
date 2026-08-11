package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.queues.QueueException;

public interface AssetEmitter {
    void emit(AssetEmit assetEmit) throws QueueException;

    List<AssetEmit> emitted();
}
