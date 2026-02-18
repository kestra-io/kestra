package io.kestra.webserver.models.ai;

import jakarta.validation.constraints.NotNull;

public record TestSuiteGenerationPrompt(@NotNull String conversationId, @NotNull String userPrompt, String yaml, String providerId) {}

