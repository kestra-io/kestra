package io.kestra.webserver.services.ai.agent.data;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for renaming a Copilot thread. The title is required and non-blank — clearing a title is
 * not a supported operation (a thread without a user title falls back to its auto-derived one).
 */
public record ApiRenameThreadRequest(@NotBlank String title) {
}
