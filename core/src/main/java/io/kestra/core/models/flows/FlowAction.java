package io.kestra.core.models.flows;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A flow attached to a resource as a runnable day-2 action, such as an asset action or a case action.
 */
public record FlowAction(
    @NotBlank
    @Schema(title = "The namespace of the flow backing this action.") String namespace,

    @NotBlank
    @Schema(title = "The id of the flow backing this action.") String flowId,

    @Nullable
    @Schema(title = "The label displayed on this action.") String label,

    @Nullable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Size(max = 20)
    @Schema(
        title = "The conditions under which this action is offered.",
        description = "All conditions must hold, and an action without conditions is always offered. A field the resource "
            + "does not have reads as null, so `NOT_EQUALS`, `NOT_IN` and `NOT_CONTAINS` hold on it."
    ) List<@Valid FlowActionCondition> when) {
    public FlowAction(String namespace, String flowId, String label) {
        this(namespace, flowId, label, null);
    }
}
