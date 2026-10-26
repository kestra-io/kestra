package io.kestra.core.exceptions;

import java.util.*;
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
     * Where the offending value sits inside a structured input, as {@code id.a[0].b}, or null when the error is
     * about the input as a whole. A schema validation reports one error per location, so a form can show each
     * message against the field it belongs to instead of collecting them all above the input.
     */
    private final String path;

    private final List<InputOutputValidationException> exceptions;
    private final Map<String, List<String>> fieldErrors;
    private final Map<String, List<InputOutputValidationException>> violationsByField;

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
        this.exceptions = List.of();
        this.fieldErrors = Collections.emptyMap();
        this.violationsByField = Collections.emptyMap();
    }

    public InputOutputValidationException(
        String message,
        List<InputOutputValidationException> exceptions,
        Map<String, List<String>> fieldErrors,
        Map<String, List<InputOutputValidationException>> violationsByField
    ) {
        super(message);
        this.renderError = false;
        this.path = null;
        this.exceptions = exceptions != null ? List.copyOf(exceptions) : List.of();
        this.fieldErrors = fieldErrors != null ? Collections.unmodifiableMap(fieldErrors) : Collections.emptyMap();
        this.violationsByField = violationsByField != null ? Collections.unmodifiableMap(violationsByField) : Collections.emptyMap();
    }

    public boolean isRenderError() {
        return renderError;
    }

    public String getPath() {
        return path;
    }

    public List<InputOutputValidationException> getExceptions() {
        return exceptions != null && !exceptions.isEmpty() ? exceptions : List.of(this);
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    public Map<String, List<InputOutputValidationException>> getViolationsByField() {
        return violationsByField;
    }

    public static InputOutputValidationException of(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, false, input.getId());
    }

    /** As {@link #of(String, Input)}, but records where inside the value the error sits. */
    public static InputOutputValidationException of(String message, Input<?> input, String path) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, false, path != null ? path : input.getId());
    }

    /** As {@link #of(String, Input)}, but flags the error as a render/resolution failure (broken field). */
    public static InputOutputValidationException ofRenderError(String message, Input<?> input) {
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage, true, input.getId());
    }

    public static InputOutputValidationException of(String message, Output output) {
        String outputMessage = "Invalid value for output" + " `" + output.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(outputMessage, false, output.getId());
    }

    public static InputOutputValidationException of(String message) {
        return new InputOutputValidationException(message);
    }

    public static InputOutputValidationException ofViolations(Map<String, List<InputOutputValidationException>> violationsByField) {
        List<InputOutputValidationException> allExceptions = violationsByField.values().stream()
            .flatMap(Collection::stream)
            .toList();
        String combinedMessage = allExceptions.stream()
            .map(InputOutputValidationException::getMessage)
            .collect(Collectors.joining(System.lineSeparator()));

        Map<String, List<String>> fieldErrors = violationsByField.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(InputOutputValidationException::getMessage).toList(),
                (a, b) -> a,
                LinkedHashMap::new
            ));

        return new InputOutputValidationException(combinedMessage, allExceptions, fieldErrors, violationsByField);
    }

    public static InputOutputValidationException merge(Collection<InputOutputValidationException> exceptions) {
        if (exceptions == null || exceptions.isEmpty()) {
            return new InputOutputValidationException("");
        }
        String combinedMessage = exceptions.stream()
            .map(InputOutputValidationException::getMessage)
            .collect(Collectors.joining(System.lineSeparator()));

        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        Map<String, List<InputOutputValidationException>> violationsByField = new LinkedHashMap<>();
        for (InputOutputValidationException ex : exceptions) {
            String field = ex.getPath() != null ? ex.getPath() : "general";
            fieldErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(ex.getMessage());
            violationsByField.computeIfAbsent(field, k -> new ArrayList<>()).add(ex);
        }

        return new InputOutputValidationException(combinedMessage, new ArrayList<>(exceptions), fieldErrors, violationsByField);
    }

    public static InputOutputValidationException merge(Set<InputOutputValidationException> exceptions) {
        return merge((Collection<InputOutputValidationException>) exceptions);
    }
}
