package io.kestra.core.exceptions;

import java.io.Serial;
import java.util.List;

/**
 * General exception that can be throws when a Kestra entity field is query, but is not valid or existing.
 */
public class InvalidQueryFiltersException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final String INVALID_QUERY_FILTER_MESSAGE = "Provided query filters are invalid: %s";

    /**
     * Creates a new {@link InvalidQueryFiltersException} instance.
     *
     * @param invalids the invalid filters.
     */
    public InvalidQueryFiltersException(final List<String> invalids) {
        super(INVALID_QUERY_FILTER_MESSAGE.formatted(String.join(", ", invalids)));
    }

    /**
     * Creates a new {@link InvalidQueryFiltersException} instance.
     *
     * @param invalid the invalid filter.
     */
    public InvalidQueryFiltersException(final String invalid) {
        super(INVALID_QUERY_FILTER_MESSAGE.formatted(invalid));
    }
}
