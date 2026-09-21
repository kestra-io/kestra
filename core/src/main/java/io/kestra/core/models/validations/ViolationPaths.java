package io.kestra.core.models.validations;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.Task;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

/**
 * Renders the two locators a bean-validation violation can be reported with, because neither alone is enough.
 *
 * <p>{@code toJsonPointer} is a valid RFC 6901 JSON Pointer, which a client can resolve mechanically against
 * the document it submitted. {@code toFriendlyPath} names tasks and inputs by id rather than by array index,
 * which is what a human editing the YAML needs — and is precisely why it is not a valid JSON Pointer.
 */
public final class ViolationPaths {
    private ViolationPaths() {
    }

    /**
     * The RFC 6901 JSON Pointer addressing the offending value in the submitted document.
     *
     * <p>Nodes describing the Java call rather than the document — the method, its parameters, a constructor,
     * a return value — reset the pointer, so method-level validation of a request body produces a pointer
     * relative to that body instead of one prefixed with {@code /createFlow/flow}.
     *
     * <p>Handles both shapes of path Kestra produces. Bean validation supplies one node per property with the
     * index or map key held separately, whereas {@link ManualConstraintViolation} supplies a single node whose
     * name is the whole expression, e.g. {@code tasks[0].type} — so each node name is itself decomposed.
     */
    public static String toJsonPointer(final Path path) {
        StringBuilder pointer = new StringBuilder();
        for (Path.Node node : path) {
            if (isJavaCallNode(node)) {
                pointer.setLength(0);
                continue;
            }
            if (node.getName() != null) {
                for (String segment : segmentsOf(node.getName())) {
                    pointer.append('/').append(escape(segment));
                }
            }
            if (node.getIndex() != null) {
                pointer.append('/').append(node.getIndex());
            } else if (node.getKey() != null) {
                pointer.append('/').append(escape(String.valueOf(node.getKey())));
            }
        }
        return pointer.toString();
    }

    /**
     * Splits a path expression into pointer segments: {@code tasks[0].type} becomes
     * {@code [tasks, 0, type]}. A plain property name yields a single segment.
     */
    static List<String> segmentsOf(final String name) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ('[' == c && 0 == depth) {
                flush(segments, current);
                depth++;
            } else if (']' == c && 0 < depth) {
                flush(segments, current);
                depth--;
            } else if ('.' == c && 0 == depth) {
                flush(segments, current);
            } else {
                current.append(c);
            }
        }
        flush(segments, current);
        return segments;
    }

    private static void flush(final List<String> segments, final StringBuilder current) {
        if (0 < current.length()) {
            segments.add(current.toString());
            current.setLength(0);
        }
    }

    /**
     * The human-friendly path, e.g. {@code tasks[my-task].type} rather than {@code tasks[0].type}. Falls back
     * to the raw property path when the offending bean carries no id.
     */
    public static String toFriendlyPath(final ConstraintViolation<?> violation) {
        String raw = violation.getPropertyPath().toString();
        try {
            if (violation.getLeafBean() instanceof Task task) {
                return replaceIndexWithId(raw, "tasks", task.getId());
            }
            if (violation.getLeafBean() instanceof Input<?> input) {
                return replaceIndexWithId(raw, "inputs", input.getId());
            }
        } catch (Exception e) {
            // An id we cannot read is not worth failing an error response over.
        }
        return raw;
    }

    /** {@code tasks[0].type} becomes {@code tasks[my-task].type}. */
    static String replaceIndexWithId(final String path, final String collection, final String id) {
        return Pattern.compile(Pattern.quote(collection) + "\\[\\d+]")
            .matcher(path)
            .replaceAll(Matcher.quoteReplacement(collection + "[" + id + "]"));
    }

    private static boolean isJavaCallNode(final Path.Node node) {
        ElementKind kind = node.getKind();
        return ElementKind.METHOD == kind
            || ElementKind.PARAMETER == kind
            || ElementKind.CONSTRUCTOR == kind
            || ElementKind.RETURN_VALUE == kind
            || ElementKind.CROSS_PARAMETER == kind;
    }

    /** RFC 6901 §3: {@code ~} then {@code /}, in that order. */
    private static String escape(final String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
