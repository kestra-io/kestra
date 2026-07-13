package io.kestra.webserver.services.ai.agent.domain;

import io.micronaut.core.annotation.Nullable;

public record ArtefactDraft(
    String draftId,
    ArtefactKind kind,
    String yaml,
    boolean valid,
    @Nullable String constraints) {
}
