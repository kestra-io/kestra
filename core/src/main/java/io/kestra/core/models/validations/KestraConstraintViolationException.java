package io.kestra.core.models.validations;

import java.io.Serial;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

public class KestraConstraintViolationException extends ConstraintViolationException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KestraConstraintViolationException(Set<? extends ConstraintViolation<?>> constraintViolations) {
        super(constraintViolations);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Paths are rendered through {@link ViolationPaths#toFriendlyPath(ConstraintViolation)}, the same
     * function that produces the {@code path} member of a problem document's {@code errors} entries, so this
     * message and that field always agree.
     */
    @Override
    public String getMessage() {
        return getConstraintViolations()
            .stream()
            .map(violation -> ViolationPaths.toFriendlyPath(violation) + ": " + violation.getMessage())
            .collect(Collectors.joining("\n", "", "\n"));
    }
}
