package io.kestra.core.models.flows;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

/**
 * A flow attached to a resource as a runnable day-2 action, such as an asset action or a case action.
 */
public record FlowAction(
    @NotBlank
    @Schema(title = "The namespace of the flow backing this action.")
    String namespace,

    @NotBlank
    @Schema(title = "The id of the flow backing this action.")
    String flowId,

    @Nullable
    @Schema(title = "The label displayed on this action.")
    String label
) {
}
