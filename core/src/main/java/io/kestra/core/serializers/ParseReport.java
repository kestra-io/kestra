package io.kestra.core.serializers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.kestra.core.models.validations.ValidateConstraintViolation.Violation;

import jakarta.annotation.Nullable;

/**
 * What a tolerant scan of a source found: unknown properties and unknown plugin types, each removed from
 * {@link #cleaned()} so the rest can still be deserialized and validated.
 */
public record ParseReport(
    List<Violation> unknownProperties,
    List<InvalidType> invalidTypes,
    @Nullable Map<String, Object> cleaned,
    List<Removal> removals
) {
    public record InvalidType(Violation violation, String typeId) {
    }

    /** A plugin dropped from a list at {@code index}, or from a property when {@code index} is -1. */
    record Removal(List<String> parent, int index) {
    }

    public boolean isClean() {
        return unknownProperties.isEmpty() && invalidTypes.isEmpty();
    }

    /**
     * Maps a pointer into {@link #cleaned()} back to the source, or returns empty when the violation only
     * exists because a plugin was removed (a list left empty, a required plugin property now missing).
     */
    public Optional<String> toSourcePointer(String pointer) {
        List<String> segments = segments(pointer);
        if (removals.stream().anyMatch(removal -> segments.equals(removal.parent()))) {
            return Optional.empty();
        }
        return Optional.of(toPointer(shift(segments, removals, removals.size())));
    }

    /** Undoes the index shifts of the first {@code count} removals, latest first. */
    static List<String> shift(List<String> segments, List<Removal> removals, int count) {
        List<String> shifted = new ArrayList<>(segments);
        for (int i = count - 1; i >= 0; i--) {
            Removal removal = removals.get(i);
            int depth = removal.parent().size();
            if (removal.index() < 0 || shifted.size() <= depth || !shifted.subList(0, depth).equals(removal.parent())) {
                continue;
            }
            try {
                int index = Integer.parseInt(shifted.get(depth));
                if (index >= removal.index()) {
                    shifted.set(depth, String.valueOf(index + 1));
                }
            } catch (NumberFormatException e) {
                // A map key under the same parent is not shifted.
            }
        }
        return shifted;
    }

    static List<String> segments(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(pointer.substring(1).split("/", -1))
            .map(segment -> segment.replace("~1", "/").replace("~0", "~"))
            .toList();
    }

    static String toPointer(List<String> segments) {
        return segments.stream()
            .map(segment -> "/" + segment.replace("~", "~0").replace("/", "~1"))
            .collect(Collectors.joining());
    }
}
