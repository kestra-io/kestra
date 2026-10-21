package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.input.AssetInput;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;

/**
 * Resolves the value of an {@code ASSET} input or output (an asset id, a list of ids, or previously resolved assets)
 * into the assets it designates, re-fetched from the asset store.
 * <p>
 * This is an Enterprise Edition concern: the open-source default ({@code DisabledAssetInputResolver}) errors.
 */
public interface AssetInputResolver {
    /**
     * @param input the declaring input, whose filters the assets must match, or {@code null} for a flow output
     * @return a map per asset, or a list of them when the input accepts {@code multiple} assets
     */
    Object resolve(Execution execution, @Nullable AssetInput input, Object value);

    /**
     * Turns the asset ids stored for the {@code ASSET} outputs of a terminated execution back into the assets, read live.
     */
    default Map<String, Object> expandOutputs(Execution execution, Map<String, Object> outputs) {
        return outputs;
    }

    /**
     * @return the executions with their {@code ASSET} inputs read live, for an API response only: the result must never be persisted
     */
    default List<Execution> expandInputs(List<Execution> executions) {
        return executions;
    }
}
