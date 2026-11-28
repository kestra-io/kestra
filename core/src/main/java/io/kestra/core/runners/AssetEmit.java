package io.kestra.core.runners;

import java.util.Collections;
import java.util.List;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;

/**
 * @param lock whether the outputs are locked to the emitting task run until it ends: one without a status is then pending when new and updating otherwise
 */
public record AssetEmit(List<AssetIdentifier> inputs, List<Asset> outputs, boolean lock) {
    public AssetEmit(List<AssetIdentifier> inputs, List<Asset> outputs) {
        this(inputs, outputs, false);
    }

    public AssetEmit {
        if (inputs == null) {
            inputs = Collections.emptyList();
        }
        if (outputs == null) {
            outputs = Collections.emptyList();
        }
    }
}
