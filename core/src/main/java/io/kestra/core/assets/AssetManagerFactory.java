package io.kestra.core.assets;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.runners.AssetEmitter;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class AssetManagerFactory {
    public AssetEmitter of(boolean enabled) {
        return new AssetEmitter() {
            @Override
            public void upsert(Asset asset) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Asset> outputs() {
                return new ArrayList<>();
            }
        };
    }
}
