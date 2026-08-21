package io.kestra.core.exceptions;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * A {@link ConstraintViolationException} raised specifically when a flow references a task or
 * trigger type that is not registered locally. Callers that treat a missing plugin type as
 * recoverable (the plugin auto-install flow) match on this class and read {@link #getTypeId()}
 * instead of parsing the message.
 */
public class InvalidTypeConstraintViolationException extends ConstraintViolationException {

    private final String typeId;

    public InvalidTypeConstraintViolationException(final String message, final String typeId, final Set<? extends ConstraintViolation<?>> constraintViolations) {
        super(message, constraintViolations);
        this.typeId = typeId;
    }

    /** Returns the unresolved type FQCN, may be {@code null}. */
    public String getTypeId() {
        return typeId;
    }
}
