package io.kestra.core.exceptions;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * A {@link ConstraintViolationException} raised specifically when a flow references a task or
 * trigger type that is not registered locally.
 * <p>
 * The type itself is the signal: callers that treat a missing plugin type as recoverable (e.g.
 * the plugin auto-install flow demoting it to a warning) match on this class instead of parsing
 * the exception message.
 */
public class InvalidTypeConstraintViolationException extends ConstraintViolationException {

    public InvalidTypeConstraintViolationException(final String message, final Set<? extends ConstraintViolation<?>> constraintViolations) {
        super(message, constraintViolations);
    }
}
