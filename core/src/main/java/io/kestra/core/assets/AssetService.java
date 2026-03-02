package io.kestra.core.assets;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetUser;
import io.kestra.core.queues.QueueException;
import io.micronaut.context.annotation.Secondary;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.List;

public interface AssetService {

    void asyncUpsert(AssetUser assetUser, Asset asset) throws QueueException;

    Asset syncUpsert(@Nullable Asset inRepository, AssetUser assetUser, Asset assetToUpsert) throws QueueException;

    void assetLineage(AssetUser assetUser, List<AssetIdentifier> inputs, List<AssetIdentifier> outputs) throws QueueException;

    void deleteAsset(Asset toDelete, AssetUser assetUser) throws QueueException;

    @Singleton
    @Secondary
    class NoopAssetService implements AssetService {
        @Override
        public void asyncUpsert(AssetUser assetUser, Asset asset) throws QueueException {
            // no-op
        }

        @Override
        public Asset syncUpsert(@Nullable Asset inRepository, AssetUser assetUser, Asset assetToUpsert) throws QueueException {
            // no-op
            return null;
        }

        @Override
        public void assetLineage(AssetUser assetUser, List<AssetIdentifier> inputs, List<AssetIdentifier> outputs) {
            // no-op
        }

        @Override
        public void deleteAsset(Asset toDelete, AssetUser assetUser) throws QueueException {
            // no-op
        }
    }
}
