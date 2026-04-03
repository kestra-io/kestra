package io.kestra.webserver.models.api;

import io.kestra.core.models.triggers.AbstractTrigger;

import lombok.Builder;
import lombok.Getter;

/**
 * API DTO combining a trigger definition with its runtime state.
 */
@Builder
public record ApiTriggerAndState(
    AbstractTrigger trigger,
    ApiTriggerState state
) {
}
