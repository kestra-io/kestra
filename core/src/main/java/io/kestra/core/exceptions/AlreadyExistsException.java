package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * Thrown when creating an entity whose identity is already taken. This is the only way to report that
 * conflict, so a client can tell a duplicate from a genuine validation failure by problem type alone.
 *
 * <p>Extends {@link ConflictException} so handlers catching that type also catch this one.
 *
 */
public class AlreadyExistsException extends ConflictException {
    @Serial
    private static final long serialVersionUID = 1L;

    public AlreadyExistsException(final String message) {
        super(message);
    }

    /**
     * @param entity the kind of entity, e.g. {@code "Flow"}
     * @param id     the identity that is already taken
     */
    public static AlreadyExistsException of(final String entity, final String id) {
        return new AlreadyExistsException("%s '%s' already exists.".formatted(entity, id));
    }

    /**
     * @param entity    the kind of entity, e.g. {@code "Flow"}
     * @param id        the identity that is already taken
     * @param namespace the namespace the entity belongs to
     */
    public static AlreadyExistsException of(final String entity, final String id, final String namespace) {
        return new AlreadyExistsException("%s '%s' already exists in namespace '%s'.".formatted(entity, id, namespace));
    }
}
