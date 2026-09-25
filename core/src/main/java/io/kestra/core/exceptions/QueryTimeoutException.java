package io.kestra.core.exceptions;

import java.io.Serial;
import java.time.Duration;

import io.micronaut.core.annotation.Nullable;

/** A dashboard query ran past the limit set for it. */
public class QueryTimeoutException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public QueryTimeoutException(Duration timeout, @Nullable Throwable cause) {
        super("The dashboard query did not complete within %d seconds. Narrow the time range or the filters.".formatted(Math.max(1, timeout.toSeconds())), cause);
    }
}
