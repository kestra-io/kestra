package io.kestra.core.models.assets;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record AssetAction(
    @NotBlank
    @Schema(title = "The namespace of the flow backing this action.")
    String namespace,

    @NotBlank
    @Schema(title = "The id of the flow backing this action.")
    String flowId,

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(
        title = "The conditions under which this action is offered.",
        description = "All conditions must hold. An action without conditions is always offered."
    )
    List<@Valid AssetActionCondition> when
) {
    public AssetAction(String namespace, String flowId) {
        this(namespace, flowId, null);
    }
}
