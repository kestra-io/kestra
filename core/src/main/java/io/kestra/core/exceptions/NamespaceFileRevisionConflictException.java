package io.kestra.core.exceptions;

/**
 * Thrown when a namespace file write claims a revision that another write has already recorded for the same path.
 */
public class NamespaceFileRevisionConflictException extends ConflictException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@link NamespaceFileRevisionConflictException} instance.
     *
     * @param namespace the namespace of the file.
     * @param path the path of the file.
     * @param revision the revision that was already recorded.
     */
    public NamespaceFileRevisionConflictException(final String namespace, final String path, final int revision) {
        super("Revision %d of file '%s' in namespace '%s' was already recorded by a concurrent write.".formatted(revision, path, namespace));
    }

    /**
     * Creates a new {@link NamespaceFileRevisionConflictException} instance.
     *
     * @param message the error message.
     */
    public NamespaceFileRevisionConflictException(final String message) {
        super(message);
    }
}
