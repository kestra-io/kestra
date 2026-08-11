package io.kestra.core.runners.pebble;

import java.util.List;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.Nullable;

/**
 * Represents a Pebble function with its name, arguments and their autocompletion defaults.
 * Argument order matches the function's positional argument order.
 */
public record PebbleFunction(String name, List<Argument> arguments) {

    /**
     * Returns a human-readable representation, e.g. {@code secret(key='MY_SECRET')} or {@code now()}.
     */
    @Override
    public String toString() {
        if (arguments.isEmpty()) {
            return name + "()";
        }
        String args = arguments.stream()
            .map(arg -> arg.defaultValue() != null ? arg.name() + "=" + arg.defaultValue() : arg.name())
            .collect(Collectors.joining(", "));
        return name + "(" + args + ")";
    }

    /**
     * A function argument with an optional autocompletion default value.
     */
    public record Argument(String name, @Nullable String defaultValue) {
    }
}
