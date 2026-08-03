package io.kestra.core.exceptions;

import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Output;

/**
 * Exception that can be thrown when Inputs/Outputs have validation problems.
 */
public class InputOutputValidationException extends KestraRuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Whether this error comes from rendering/resolving the input (e.g. a SELECT's {@code expression}
     * or an input's {@code defaults} using a Pebble function that failed) rather than from validating a
     * provided value (e.g. a required input left empty). A render error means the field itself is broken.
     */
    private final boolean renderError;

    public InputOutputValidationException(String message) {
        this(message, false);
    }

    public InputOutputValidationException(String message, boolean renderError) {
        super(message);
        this.renderError = renderError;
    }

    public boolean isRenderError() {
        return renderError;
    }

    public static InputOutputValidationException of(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage);
    }

    /** As {@link #of(String, Input)}, but flags the error as a render/resolution failure (broken field). */
    public static InputOutputValidationException ofRenderError(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, true);
    }

    public static InputOutputValidationException of(String message, Output output) {
        String outputMessage = "Invalid value for output" + " `" + output.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(outputMessage);
    }

    public static InputOutputValidationException of(String message) {
        return new InputOutputValidationException(message);
    }

    public static InputOutputValidationException merge(Set<InputOutputValidationException> exceptions) {
        String combinedMessage = exceptions.stream()
            .map(InputOutputValidationException::getMessage)
            .collect(Collectors.joining(System.lineSeparator()));
        throw new InputOutputValidationException(combinedMessage);
    }

}
