package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.input.AssetInput;

import jakarta.inject.Singleton;

/**
 * Open-source default: assets are an Enterprise Edition feature, so resolving an {@code ASSET} value fails.
 */
@Singleton
public class DisabledAssetInputResolver implements AssetInputResolver {
    @Override
    public Object resolve(Execution execution, AssetInput input, Object value) {
        throw new IllegalArgumentException("The ASSET type requires Kestra Enterprise Edition.");
    }
}
