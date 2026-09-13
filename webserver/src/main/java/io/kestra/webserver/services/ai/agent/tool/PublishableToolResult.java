package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.ArtefactDraft;

/**
 * A tool return value that, besides the text the model receives, yields an artefact to publish —
 * persisted to the thread and streamed to the UI as an {@code artefact_draft}. When a {@code @Tool}
 * method returns a value implementing this, {@link ToolCatalog#dispatch} publishes {@link #artefact()}
 * in addition to feeding the text result to the model. This keeps the tool to a single output (its
 * return value) instead of also pushing the draft through a side channel.
 */
public interface PublishableToolResult {
    ArtefactDraft artefact();
}
