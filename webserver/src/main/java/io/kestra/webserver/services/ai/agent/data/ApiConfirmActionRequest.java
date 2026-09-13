package io.kestra.webserver.services.ai.agent.data;

import io.micronaut.core.annotation.Nullable;

public record ApiConfirmActionRequest(String confirmationId, ApiDecision decision, @Nullable String reason, @Nullable String providerId) {
}
