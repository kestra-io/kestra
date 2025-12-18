package io.kestra.core.services;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetUser;
import io.kestra.core.queues.QueueException;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class AssetService implements Runnable {
    @PostConstruct
    public void start() {
        this.run();
    }

    public void asyncUpsert(AssetUser assetUser, Asset asset) throws QueueException {
        // No-op
    }

    public void assetLineage(AssetUser assetUser, List<AssetIdentifier> inputs, List<AssetIdentifier> outputs) throws QueueException {
        // No-op
    }

    public void run() {
        // No-op
    }
}
