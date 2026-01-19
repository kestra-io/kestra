package io.kestra.core.runners;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.queues.QueueException;

import java.util.List;

public interface AssetEmitter {
    void upsert(Asset asset) throws QueueException;

    List<Asset> outputs();
}
