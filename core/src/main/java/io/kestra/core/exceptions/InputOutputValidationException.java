package io.kestra.core.exceptions;

import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.input.TableInput;

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
     * Where the offending value sits inside a structured input, as {@code id.a[0].b}, or null when the error is
     * about the input as a whole. A schema validation reports one error per location, so a form can show each
     * message against the field it belongs to instead of collecting them all above the input.
     */
    private final String path;

    /**
     * The cause alone, without the input and the place inside the value that {@link #path} already points to, so a
     * form showing the message next to that place does not repeat it. Null when the error is not about an input.
     */
    private final String detail;

    public InputOutputValidationException(String message) {
        this(message, false);
    }

    public InputOutputValidationException(String message, boolean renderError) {
        this(message, renderError, null);
    }

    public InputOutputValidationException(String message, boolean renderError, String path) {
        this(message, renderError, path, null);
    }

    public InputOutputValidationException(String message, boolean renderError, String path, String detail) {
        super(message);
        this.renderError = renderError;
        this.path = path;
        this.detail = detail;
    }

    public boolean isRenderError() {
        return renderError;
    }

    public String getPath() {
        return path;
    }

    public String getDetail() {
        return detail;
    }

    public static InputOutputValidationException of(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, false, null, message);
    }

    /**
     * As {@link #of(String, Input)}, but records where inside the value the error sits. The message names that place,
     * e.g. the row and column of a {@code TABLE} cell, while the detail leaves it to the path.
     */
    public static InputOutputValidationException of(String message, Input<?> input, String path) {
        String located = input instanceof TableInput table ? table.locationOf(path) + message : message;
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + located;
        return new InputOutputValidationException(inputMessage, false, path, message);
    }

    /** As {@link #of(String, Input)}, but flags the error as a render/resolution failure (broken field). */
    public static InputOutputValidationException ofRenderError(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, true, null, message);
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
