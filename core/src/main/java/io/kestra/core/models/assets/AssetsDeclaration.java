package io.kestra.core.models.assets;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.property.Property;

import lombok.Getter;

@Getter
public class AssetsDeclaration {
    private Property<Boolean> enableAuto;
    private Property<List<AssetIdentifier>> inputs;
    private Property<List<Asset>> outputs;

    @JsonCreator
    public AssetsDeclaration(Property<Boolean> enableAuto, Property<List<AssetIdentifier>> inputs, Property<List<Asset>> outputs) {
        this.enableAuto = Optional.ofNullable(enableAuto).orElse(Property.ofValue(false));
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public AssetsDeclaration(boolean enableAuto, List<AssetIdentifier> inputs, List<Asset> outputs) {
        this(
            Property.ofValue(enableAuto),
            Property.ofValue(inputs),
            Property.ofValue(outputs)
        );
    }
}
