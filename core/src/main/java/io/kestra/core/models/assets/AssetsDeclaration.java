package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

@Getter
public class AssetsDeclaration extends AssetsInOut {
    private boolean enableAuto;

    @JsonCreator
    public AssetsDeclaration(Boolean enableAuto, List<AssetIdentifier> inputs, List<Asset> outputs) {
        super(inputs, outputs);

        this.enableAuto = Optional.ofNullable(enableAuto).orElse(false);
    }
}
