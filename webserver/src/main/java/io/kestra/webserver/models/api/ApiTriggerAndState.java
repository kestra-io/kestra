package io.kestra.webserver.models.api;

import io.kestra.core.models.triggers.AbstractTrigger;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

/**
 * API DTO combining a trigger definition with its runtime state.
 */
@Builder
public record ApiTriggerAndState(
    @NotNull AbstractTrigger trigger,
    @NotNull ApiTriggerState state
) {
}
