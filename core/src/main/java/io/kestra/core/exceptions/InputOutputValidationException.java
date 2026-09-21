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

    /**
     * The path of the offending value inside a structured input, e.g. {@code disks[2].size_gb} for a cell of a
     * {@code TABLE}, or {@code null} for an input whose value has no inner structure.
     */
    private final String path;

    public InputOutputValidationException(String message) {
        this(message, false);
    }

    public InputOutputValidationException(String message, boolean renderError) {
        this(message, renderError, null);
    }

    public InputOutputValidationException(String message, boolean renderError, String path) {
        super(message);
        this.renderError = renderError;
        this.path = path;
    }

    public boolean isRenderError() {
        return renderError;
    }

    public String getPath() {
        return path;
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

    /** As {@link #of(String, Input)}, but locates the error at {@code path} inside a structured input's value. */
    public static InputOutputValidationException ofPath(String message, String path) {
        String inputMessage = "Invalid value for input" + " `" + path + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, false, path);
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
