package io.kestra.core.serializers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.kestra.core.models.validations.ValidationError;
import io.kestra.core.models.validations.ViolationPaths;

import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolation;

/**
 * What a tolerant scan of a source found: every problem that stops a strict parse, located in the source,
 * plus the source with those problems removed when the rest can still be deserialized and validated.
 */
public final class ParseReport {
    private final List<ValidationError> errors;
    private final List<InvalidType> invalidTypes;
    @Nullable
    private final String recoveredSource;
    private final List<Removal> removals;

    ParseReport(List<ValidationError> errors, List<InvalidType> invalidTypes, @Nullable String recoveredSource, List<Removal> removals) {
        this.errors = List.copyOf(errors);
        this.invalidTypes = List.copyOf(invalidTypes);
        this.recoveredSource = recoveredSource;
        this.removals = List.copyOf(removals);
    }

    public record InvalidType(ValidationError error, String typeId) {
    }

    /** A node dropped from a list at {@code index}, or from a property when {@code index} is -1. */
    record Removal(List<String> parent, int index) {
    }

    /** Problems other than unknown plugin types, which {@link #invalidTypes()} lists apart for auto-install. */
    public List<ValidationError> errors() {
        return errors;
    }

    public List<InvalidType> invalidTypes() {
        return invalidTypes;
    }

    public boolean hasProblems() {
        return !errors.isEmpty() || !invalidTypes.isEmpty();
    }

    /** The source without its problems, or empty when what is left still cannot be deserialized. */
    public Optional<String> recoveredSource() {
        return Optional.ofNullable(recoveredSource);
    }

    /**
     * Locates a violation of the recovered source in the original one, or returns empty when it only exists
     * because a node was removed (a list left empty, a required property now missing).
     */
    public Optional<ValidationError> locate(ConstraintViolation<?> violation) {
        List<String> segments = segments(ViolationPaths.toJsonPointer(violation.getPropertyPath()));
        if (removals.stream().anyMatch(removal -> segments.equals(removal.parent()))) {
            return Optional.empty();
        }
        String pointer = toPointer(shift(segments, removals, removals.size()));
        return Optional.of(new ValidationError(violation.getMessage(), pointer, ViolationPaths.toFriendlyPath(violation)));
    }

    /** Undoes the index shifts of the first {@code count} removals, latest first. */
    static List<String> shift(List<String> segments, List<Removal> removals, int count) {
        List<String> shifted = new ArrayList<>(segments);
        for (int i = count - 1; i >= 0; i--) {
            Removal removal = removals.get(i);
            int depth = removal.parent().size();
            if (removal.index() < 0 || shifted.size() <= depth || !shifted.subList(0, depth).equals(removal.parent())
                || !isIndex(shifted.get(depth))) {
                continue;
            }
            int index = Integer.parseInt(shifted.get(depth));
            if (index >= removal.index()) {
                shifted.set(depth, String.valueOf(index + 1));
            }
        }
        return shifted;
    }

    /** A list index segment, short enough to fit an int. */
    static boolean isIndex(String segment) {
        return !segment.isEmpty() && segment.length() <= 9 && segment.chars().allMatch(Character::isDigit);
    }

    static List<String> segments(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(pointer.substring(1).split("/", -1))
            .map(segment -> segment.replace("~1", "/").replace("~0", "~"))
            .toList();
    }

    /** The friendly form of a path: {@code tasks[0].type}. */
    static String toPath(List<String> segments) {
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            if (isIndex(segment)) {
                path.append('[').append(segment).append(']');
            } else {
                path.append(path.isEmpty() ? "" : ".").append(segment);
            }
        }
        return path.toString();
    }

    static String toPointer(List<String> segments) {
        return segments.stream()
            .map(segment -> "/" + segment.replace("~", "~0").replace("/", "~1"))
            .collect(Collectors.joining());
    }
}
