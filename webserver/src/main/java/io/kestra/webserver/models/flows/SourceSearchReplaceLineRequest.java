package io.kestra.webserver.models.flows;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SourceSearchReplaceLineRequest(
    @NotBlank String query,
    boolean caseSensitive,
    boolean wholeWord,
    boolean regex,
    @NotNull String replacement,
    @NotBlank String namespace,
    @NotBlank String id,
    @Min(1) int line,
    @Min(0) int column
) {
}
