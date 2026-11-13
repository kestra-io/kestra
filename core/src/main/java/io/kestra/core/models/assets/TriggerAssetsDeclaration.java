package io.kestra.core.models.assets;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;

/**
 * The assets a trigger declares, which unlike a task's have no {@code assetFailureBehavior}: it only applies to a task run.
 */
@Getter
// still accepted, since flows saved while triggers offered it may set it
@JsonIgnoreProperties("assetFailureBehavior")
public class TriggerAssetsDeclaration {
    @Schema(description = "Whether to auto-register assets referenced dynamically at runtime that are not statically declared in inputs or outputs.")
    private Property<Boolean> enableAuto;
    @Schema(description = "The assets consumed as inputs.")
    private Property<List<@Valid AssetIdentifier>> inputs;
    @Schema(description = "The assets produced as outputs.")
    private Property<List<@Valid Asset>> outputs;

    @JsonCreator
    public TriggerAssetsDeclaration(Property<Boolean> enableAuto, Property<List<@Valid AssetIdentifier>> inputs, Property<List<@Valid Asset>> outputs) {
        this.enableAuto = Optional.ofNullable(enableAuto).orElse(Property.ofValue(false));
        this.inputs = inputs;
        this.outputs = outputs;
    }
}
