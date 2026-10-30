package io.kestra.webserver.exceptions;

import java.io.Serial;
import java.util.List;
import java.util.Set;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.webserver.errors.ProblemError;

import jakarta.validation.ConstraintViolation;

/**
 * Thrown by a bulk endpoint when one or more of the submitted items is invalid.
 *
 * <p>Bulk endpoints validate every item up front and mutate nothing if any item is invalid, so this is a
 * failed request rather than a partial success: each item's problem is reported as one entry of the
 * resulting problem document's {@code errors} array.
 *
 * @see io.kestra.webserver.errors.ProblemTypes#BULK_VALIDATION_FAILED
 */
public class BulkValidationException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<ProblemError> errors;

    /**
     * @param message a sentence describing the failed operation as a whole
     * @param errors  one entry per invalid item
     */
    public BulkValidationException(final String message, final List<ProblemError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public BulkValidationException(final String message, final Set<? extends ConstraintViolation<?>> violations) {
        this(message, ProblemError.ofViolations(violations));
    }

    public List<ProblemError> errors() {
        return this.errors;
    }
}
