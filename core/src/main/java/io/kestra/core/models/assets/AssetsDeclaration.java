package io.kestra.core.models.assets;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.AssetFailureBehavior;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class AssetsDeclaration {
    private Property<Boolean> enableAuto;
    private Property<List<AssetIdentifier>> inputs;
    private Property<List<Asset>> outputs;

    @Schema(
        title = "Asset failure behavior",
        description = "Behavior applied to the task state when a declared asset fails to render, emit, or be persisted (e.g. a lock conflict): FAIL escalates it to FAILED, WARN (default) warns it if it would otherwise succeed, IGNORE leaves the state untouched."
    )
    private Property<AssetFailureBehavior> assetFailureBehavior;

    @JsonCreator
    public AssetsDeclaration(Property<Boolean> enableAuto, Property<List<AssetIdentifier>> inputs, Property<List<Asset>> outputs, Property<AssetFailureBehavior> assetFailureBehavior) {
        this.enableAuto = Optional.ofNullable(enableAuto).orElse(Property.ofValue(false));
        this.inputs = inputs;
        this.outputs = outputs;
        this.assetFailureBehavior = Optional.ofNullable(assetFailureBehavior).orElse(Property.ofValue(AssetFailureBehavior.WARN));
    }

    public AssetsDeclaration(boolean enableAuto, List<AssetIdentifier> inputs, List<Asset> outputs) {
        this(
            Property.ofValue(enableAuto),
            Property.ofValue(inputs),
            Property.ofValue(outputs),
            null
        );
    }
}
