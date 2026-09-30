package io.kestra.webserver.errors;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.validations.ViolationPaths;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;

/**
 * A single field-level error inside a {@link ProblemDetail}, reported through the {@code errors}
 * extension member of RFC 9457 §3.
 *
 * <p>Two locators are carried because neither alone is sufficient: {@code pointer} is a valid RFC 6901
 * JSON Pointer for machine use, while {@code path} is the friendlier Kestra form that names tasks and
 * inputs by id rather than by array index — and is therefore not a valid JSON Pointer.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(
    name = "ProblemError",
    description = "A single field-level error inside a problem details document."
)
public record ProblemError(
    @Schema(description = "What is wrong with this field.", example = "must not be null")
    String detail,

    @Schema(description = "RFC 6901 JSON Pointer locating the field in the submitted document.", example = "/tasks/0/type")
    String pointer,

    @Schema(
        description = "Human-friendly path locating the field, naming tasks and inputs by id. Not a JSON Pointer.",
        example = "tasks[my-task].type"
    )
    String path,

    @Schema(
        description = "Problem type of this individual error, when it differs per item. Follows the same URI scheme as the enclosing document.",
        example = "https://kestra.io/docs/api-reference/problems/not-found"
    )
    URI type
) {
    public static ProblemError of(String detail) {
        return new ProblemError(detail, null, null, null);
    }

    /** One entry per violated constraint, ordered so the same input always produces the same document. */
    public static List<ProblemError> ofViolations(final Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
            .map(violation -> of(
                violation.getMessage(),
                ViolationPaths.toJsonPointer(violation.getPropertyPath()),
                ViolationPaths.toFriendlyPath(violation)
            ))
            .sorted(Comparator.comparing(ProblemError::pointer, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    public static ProblemError of(String detail, String pointer, String path) {
        return new ProblemError(detail, pointer, path, null);
    }

    /** One rejected item of a bulk operation, identified by path and by its own problem type. */
    public static ProblemError ofItem(String detail, String path, ProblemType type) {
        return new ProblemError(detail, null, path, type.uri());
    }
}
