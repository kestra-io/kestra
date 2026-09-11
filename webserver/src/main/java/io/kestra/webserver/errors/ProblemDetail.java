package io.kestra.webserver.errors;

import java.net.URI;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An <a href="https://www.rfc-editor.org/rfc/rfc9457.html">RFC 9457</a> problem details document, and
 * the single error representation of the Kestra API. Always served as
 * {@code application/problem+json}.
 *
 * <p>{@code type} is the only member clients should branch on. {@code title} is stable for a given
 * {@code type} and never parameterised, so it is safe to display verbatim or to use as a translation
 * key; anything specific to one occurrence belongs in {@code detail}.
 *
 * @see ProblemType the closed set of {@code type} values
 * @see ProblemFactory the only supported way to build one
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(
    name = "ProblemDetail",
    description = "An RFC 9457 problem details document describing a failed request."
)
public record ProblemDetail(
    @Schema(
        description = "Stable, machine-readable identifier of the problem kind, resolving to its documentation. This is what clients branch on.",
        example = "https://kestra.io/docs/api-reference/problems/entity-already-exists"
    )
    URI type,

    @Schema(
        description = "Short, human-readable summary of the problem kind. Stable for a given type and never parameterised.",
        example = "Entity already exists"
    )
    String title,

    @Schema(description = "The HTTP status code, repeated here for convenience.", example = "409")
    int status,

    @Schema(
        description = "Human-readable explanation specific to this occurrence.",
        example = "A flow with id 'my-flow' already exists in namespace 'company.team'."
    )
    String detail,

    @Schema(description = "The path of the request that produced this problem.", example = "/api/v1/main/flows")
    String instance,

    @Schema(description = "Field-level errors, when several problems are reported at once.")
    List<ProblemError> errors,

    @Schema(
        description = "Correlation identifier for the matching server-side log entry. Present on server errors only.",
        example = "4bf92f3577b34da6a3ce929d0e0e4736"
    )
    String traceId
) {
}
