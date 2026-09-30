package io.kestra.core.models.validations;

import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.exceptions.InvalidTypeConstraintViolationException;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * One validation error, shaped as an entry of the RFC 9457 {@code errors} member so a validation result and a
 * problem document locate errors the same way.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(
    name = "ValidationError",
    description = "A single validation error, located in the submitted source."
)
public record ValidationError(
    @Schema(description = "What is wrong.", example = "must not be null")
    String detail,

    @Schema(description = "RFC 6901 JSON Pointer locating the error in the submitted document.", example = "/tasks/0/message")
    String pointer,

    @Schema(
        description = "Human-friendly path locating the error, naming tasks and inputs by id. Not a JSON Pointer.",
        example = "tasks[my-task].message"
    )
    String path
) {
    public static ValidationError of(String detail) {
        return new ValidationError(detail, null, null);
    }

    /** One error per violation of {@code e}, skipping the duplicate Jackson message an invalid type carries. */
    public static List<ValidationError> ofException(ConstraintViolationException e) {
        if (e.getConstraintViolations() == null) {
            return List.of();
        }
        Stream<? extends ConstraintViolation<?>> violations = e instanceof InvalidTypeConstraintViolationException
            ? e.getConstraintViolations().stream().filter(v -> v.getMessage().equals(e.getMessage()))
            : e.getConstraintViolations().stream();
        return violations.map(ValidationError::ofViolation).toList();
    }

    public static ValidationError ofViolation(ConstraintViolation<?> violation) {
        return new ValidationError(
            violation.getMessage(),
            ViolationPaths.toJsonPointer(violation.getPropertyPath()),
            ViolationPaths.toFriendlyPath(violation)
        );
    }
}
