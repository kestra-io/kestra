package io.kestra.core.runners.pebble;

import java.io.Serial;

import io.pebbletemplates.pebble.error.PebbleException;

/**
 * Thrown while rendering a Pebble expression when a resource limit would be exceeded, such as a
 * function producing too many elements or an expression rendering an oversized output.
 * <p>
 * It extends {@link PebbleException} on purpose: {@code VariableRenderer.renderOnce} already catches
 * {@link RuntimeException} and maps every {@link PebbleException} to an
 * {@link io.kestra.core.exceptions.IllegalVariableEvaluationException}. Rendering therefore fails
 * gracefully as a validation error instead of letting an unbounded allocation escape as an
 * {@link OutOfMemoryError} — which, being an {@link Error} rather than a {@link RuntimeException},
 * would bypass that catch and crash the JVM.
 */
public class RenderLimitExceededException extends PebbleException {
    @Serial
    private static final long serialVersionUID = 1L;

    public RenderLimitExceededException(String message) {
        super(null, message);
    }
}
