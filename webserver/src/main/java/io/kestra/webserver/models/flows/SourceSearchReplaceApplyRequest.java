package io.kestra.webserver.models.flows;

import java.util.List;

import io.kestra.core.models.flows.SourceSearchScope;
import io.kestra.webserver.controllers.domain.IdWithNamespace;

import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SourceSearchReplaceApplyRequest(
    @NotBlank String query,
    boolean caseSensitive,
    boolean wholeWord,
    boolean regex,
    @Nullable SourceSearchScope scope,
    @NotNull String replacement,
    @NotEmpty List<IdWithNamespace> flows
) {
    public SourceSearchScope scopeOrAll() {
        return scope == null ? SourceSearchScope.ALL : scope;
    }
}
