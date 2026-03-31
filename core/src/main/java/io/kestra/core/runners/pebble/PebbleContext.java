package io.kestra.core.runners.pebble;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Holds the Pebble filters and functions available in the current Kestra instance,
 * pre-formatted as strings suitable for injection into AI copilot prompts.
 *
 * @param filters   the raw sorted list of filter names.
 * @param functions the raw sorted list of {@link PebbleFunction}s.
 */
public record PebbleContext(
    List<String> filters,
    List<PebbleFunction> functions,
    String filtersString,
    String functionsString
) {
    /**
     * Creates a {@link PebbleContext} from raw filter names and function descriptors.
     * The stringified representations are computed once here.
     */
    public static PebbleContext of(List<String> filters, List<PebbleFunction> functions) {
        String filtersString = String.join(", ", filters);

        String functionsString = functions.stream()
            .map(PebbleContext::formatFunction)
            .collect(Collectors.joining(", "));

        return new PebbleContext(filters, functions, filtersString, functionsString);
    }

    /**
     * Formats a {@link PebbleFunction} as {@code name(arg1, arg2)} using argument defaults when available.
     */
    static String formatFunction(PebbleFunction fn) {
        if (fn.arguments().isEmpty()) {
            return fn.name() + "()";
        }

        String args = fn.arguments().stream()
            .map(arg -> arg.defaultValue() != null ? arg.defaultValue() : arg.name())
            .collect(Collectors.joining(", "));

        return fn.name() + "(" + args + ")";
    }
}
