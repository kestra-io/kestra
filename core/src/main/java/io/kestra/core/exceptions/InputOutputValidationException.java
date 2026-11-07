package io.kestra.core.exceptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        this(message, renderError, path, null);
    }

    public InputOutputValidationException(String message, boolean renderError, String path, String detail) {
        super(message);
        this.renderError = renderError;
        this.path = path;
        this.detail = detail;
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
        this.detail = null;
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

    public String getDetail() {
        return detail;
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
