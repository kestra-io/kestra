package io.kestra.core.models.flows.input;

import io.kestra.core.exceptions.InputOutputValidationException;
import io.kestra.core.models.flows.Input;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Represents an input along with its associated value and validation state.
 *
 * @param input     The {@link Input} definition of the flow.
 * @param value     The provided value for the input.
 * @param enabled   {@code true} if the input is enabled; {@code false} otherwise.
 * @param isDefault {@code true} if the provided value is the default; {@code false} otherwise.
 * @param exceptions The validation exceptions, if the input value is invalid; {@code null} otherwise.
 */
public record InputAndValue(
    Input<?> input,
    Object value,
    boolean enabled,
    boolean isDefault,
    Set<InputOutputValidationException> exceptions) {

    /**
     * Creates a new {@link InputAndValue} instance.
     *
     * @param input The {@link Input}
     * @param value The value.
     */
    public InputAndValue(@NotNull Input<?> input, @Nullable Object value) {
        this(input, value, true, false, null);
    }
}
