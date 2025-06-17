package io.kestra.core.exceptions;

import java.io.Serial;
import java.util.List;

/**
 * General exception that can be throws when a Kestra entity field is query, but is not valid or existing.
 */
public class InvalidQueryFiltersException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final String INVALID_QUERY_FILTER_MESSAGE = "Provided query filters are invalid";

    private transient final List<String> invalids;

    /**
     * Creates a new {@link InvalidQueryFiltersException} instance.
     *
     * @param invalids the invalid entity.
     * @param message the error message.
     */
    public InvalidQueryFiltersException(final List<String> invalids, final String message) {
        super(message);
        this.invalids = invalids;
    }

    /**
     * Creates a new {@link InvalidQueryFiltersException} instance.
     *
     * @param invalids the invalid entity.
     */
    public InvalidQueryFiltersException(final List<String> invalids) {
        super(INVALID_QUERY_FILTER_MESSAGE);
        this.invalids = invalids;
    }

    /**
     * Gets the invalid objects.
     *
     * @return the invalid objects.
     */
    public List<String> invalidObjects() {
        return invalids;
    }
}
