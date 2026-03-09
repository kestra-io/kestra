package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public class AssetsInOut {
    private List<AssetIdentifier> inputs;

    private List<Asset> outputs;

    @JsonCreator
    public AssetsInOut(List<AssetIdentifier> inputs, List<Asset> outputs) {
        this.inputs = Optional.ofNullable(inputs).orElse(Collections.emptyList());
        this.outputs = Optional.ofNullable(outputs).orElse(Collections.emptyList());
    }
}
