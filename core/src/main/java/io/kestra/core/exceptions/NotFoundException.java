package io.kestra.core.exceptions;

import java.util.Optional;

/**
 * General exception that can be throws when a Kestra resource or entity is not found.
 */
public class NotFoundException extends KestraRuntimeException {
    private static final long serialVersionUID = 1L;

    private final String entity;

    /**
     * Creates a new {@link NotFoundException} instance.
     */
    public NotFoundException() {
        super();
        this.entity = null;
    }

    /**
     * Creates a new {@link NotFoundException} instance.
     *
     * @param message the error message.
     */
    public NotFoundException(final String message) {
        super(message);
        this.entity = null;
    }

    /**
     * Creates a new {@link NotFoundException} instance tagged with the entity that was not found.
     *
     * @param entity  the kind of resource, e.g. a module-local {@code Resource} enum constant.
     * @param message the error message.
     */
    public NotFoundException(final Enum<?> entity, final String message) {
        super(message);
        this.entity = entity != null ? entity.name() : null;
    }

    /**
     * Returns the kind of resource that was looked up and not found, if this exception was created with one.
     */
    public Optional<String> entity() {
        return Optional.ofNullable(entity);
    }
}
