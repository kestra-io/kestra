package io.kestra.core.runners;

import io.kestra.core.queues.QueueException;

import java.util.List;

public interface AssetEmitter {
    void emit(AssetEmit assetEmit) throws QueueException;

    List<AssetEmit> emitted();
}
