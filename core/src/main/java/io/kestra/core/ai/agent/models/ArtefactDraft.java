package io.kestra.core.ai.agent.models;

import io.micronaut.core.annotation.Nullable;

public record ArtefactDraft(
    String draftId,
    ArtefactKind kind,
    String yaml,
    boolean valid,
    @Nullable String constraints) {
}
