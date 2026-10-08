package io.kestra.core.exceptions;

import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Output;

/**
 * Exception that can be thrown when Inputs/Outputs have validation problems.
 */
public class InputOutputValidationException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Whether this error comes from rendering/resolving the input (e.g. a SELECT's {@code expression}
     * or an input's {@code defaults} using a Pebble function that failed) rather than from validating a
     * provided value (e.g. a required input left empty). A render error means the field itself is broken.
     */
    private final boolean renderError;
    private final String inputId;
    private final List<InputOutputValidationException> exceptions;
    private final Map<String, List<String>> fieldErrors;

    public InputOutputValidationException(String message) {
        this(message, null, false, List.of(), Map.of());
    }

    public InputOutputValidationException(String message, boolean renderError) {
        this(message, null, renderError, List.of(), Map.of());
    }

    public InputOutputValidationException(String message, String inputId) {
        this(message, inputId, false, List.of(), Map.of());
    }

    public InputOutputValidationException(String message, String inputId, boolean renderError) {
        this(message, inputId, renderError, List.of(), Map.of());
    }

    public InputOutputValidationException(
        String message,
        String inputId,
        boolean renderError,
        List<InputOutputValidationException> exceptions,
        Map<String, List<String>> fieldErrors
    ) {
        super(message);
        this.inputId = inputId;
        this.renderError = renderError;
        this.exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
        this.fieldErrors = fieldErrors == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public boolean isRenderError() {
        return renderError;
    }

    public String getInputId() {
        return inputId;
    }

    public List<InputOutputValidationException> getExceptions() {
        return exceptions;
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    public static InputOutputValidationException of(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, input.getId(), false);
    }

    /** As {@link #of(String, Input)}, but flags the error as a render/resolution failure (broken field). */
    public static InputOutputValidationException ofRenderError(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, input.getId(), true);
    }

    public static InputOutputValidationException of(String message, Output output) {
        String outputMessage = "Invalid value for output" + " `" + output.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(outputMessage, output.getId(), false);
    }

    public static InputOutputValidationException of(String message) {
        return new InputOutputValidationException(message);
    }

    public static InputOutputValidationException of(String message, String inputId) {
        return new InputOutputValidationException(message, inputId);
    }

    public static InputOutputValidationException merge(Collection<InputOutputValidationException> exceptions) {
        if (exceptions == null || exceptions.isEmpty()) {
            return new InputOutputValidationException("");
        }

        List<InputOutputValidationException> flattened = exceptions.stream()
            .filter(Objects::nonNull)
            .flatMap(e -> e.getExceptions() != null && !e.getExceptions().isEmpty() ? e.getExceptions().stream() : Stream.of(e))
            .toList();

        String combinedMessage = flattened.stream()
            .map(InputOutputValidationException::getMessage)
            .collect(Collectors.joining(System.lineSeparator()));

        boolean renderError = flattened.stream().anyMatch(InputOutputValidationException::isRenderError);

        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (InputOutputValidationException ex : flattened) {
            String id = ex.getInputId();
            if (id != null) {
                fieldErrors.computeIfAbsent(id, _ -> new ArrayList<>()).add(ex.getMessage());
            }
        }

        return new InputOutputValidationException(
            combinedMessage,
            flattened.size() == 1 ? flattened.getFirst().getInputId() : null,
            renderError,
            flattened,
            fieldErrors
        );
    }

    public static InputOutputValidationException merge(Set<InputOutputValidationException> exceptions) {
        return merge((Collection<InputOutputValidationException>) exceptions);
    }

}
