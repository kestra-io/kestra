package io.kestra.webserver.models.flows;

import java.util.List;

import io.kestra.core.models.flows.SourceSearchScope;
import io.kestra.webserver.controllers.domain.IdWithNamespace;

import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SourceSearchReplaceApplyRequest(
    @NotBlank String query,
    boolean caseSensitive,
    boolean wholeWord,
    boolean regex,
    @Nullable SourceSearchScope scope,
    @NotNull String replacement,
    @NotEmpty @Size(max = MAX_FLOWS_PER_APPLY) List<IdWithNamespace> flows
) {
    /**
     * Upper bound on the flows one replace can rewrite, mirroring the number of candidates a single
     * source search can return. Each entry writes a new flow revision.
     */
    public static final int MAX_FLOWS_PER_APPLY = 1000;

    public SourceSearchScope scopeOrAll() {
        return scope == null ? SourceSearchScope.ALL : scope;
    }
}
