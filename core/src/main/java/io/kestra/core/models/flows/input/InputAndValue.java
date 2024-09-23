package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;

/**
 * Represents a
 *
 * @param input     The flow's {@link Input}.
 * @param value     The flow's input value/data.
 * @param enabled   Specify whether the input is enabled.
 * @param exception The input validation exception.
 */
public record InputAndValue(
    Input<?> input,
    Object value,
    boolean enabled,
    ConstraintViolationException exception) {

    /**
     * Creates a new {@link InputAndValue} instance.
     *
     * @param input The {@link Input}
     * @param value The value.
     */
    public InputAndValue(@NotNull Input<?> input, @Nullable Object value) {
        this(input, value, true, null);
    }
}
