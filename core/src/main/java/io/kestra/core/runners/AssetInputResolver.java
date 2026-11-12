package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.input.AssetInput;

import jakarta.annotation.Nullable;

/**
 * Checks the value of an {@code ASSET} input or output (an asset id, or a list of ids) against the asset store and the input's filters.
 * <p>
 * This is an Enterprise Edition concern: the open-source default ({@code DisabledAssetInputResolver}) errors.
 */
public interface AssetInputResolver {
    /**
     * @param input the declaring input, whose filters the assets must match, or {@code null} for a flow output
     * @return the asset id, or the list of ids when the input accepts {@code multiple} assets
     */
    Object resolve(Execution execution, @Nullable AssetInput input, Object value);
}
