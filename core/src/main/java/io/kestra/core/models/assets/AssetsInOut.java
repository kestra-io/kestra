package io.kestra.core.models.assets;

import io.kestra.core.utils.ListUtils;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.Getter;

/**
 * @deprecated assets are written when the task emits them through {@link io.kestra.core.runners.AssetEmitter}; kept so plugins built against 2.0 keep linking.
 */
@Deprecated(forRemoval = true, since = "2.1.0")
@Getter
public class AssetsInOut {
    private List<AssetIdentifier> inputs;

    private List<Asset> outputs;

    @JsonCreator
    public AssetsInOut(List<AssetIdentifier> inputs, List<Asset> outputs) {
        this.inputs = ListUtils.emptyOnNull(inputs);
        this.outputs = ListUtils.emptyOnNull(outputs);
    }
}
