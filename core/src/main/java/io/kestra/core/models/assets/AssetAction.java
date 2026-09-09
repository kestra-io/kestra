package io.kestra.core.models.assets;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AssetAction(
    @NotBlank
    @Schema(title = "The namespace of the flow backing this action.")
    String namespace,

    @NotBlank
    @Schema(title = "The id of the flow backing this action.")
    String flowId
) {
}
