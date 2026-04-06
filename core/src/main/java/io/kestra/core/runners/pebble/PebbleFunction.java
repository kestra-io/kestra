package io.kestra.core.runners.pebble;

import java.util.List;

import io.micronaut.core.annotation.Nullable;

/**
 * Represents a Pebble function with its name, arguments and their autocompletion defaults.
 * Argument order matches the function's positional argument order.
 */
public record PebbleFunction(String name, List<Argument> arguments) {

    /**
     * A function argument with an optional autocompletion default value.
     */
    public record Argument(String name, @Nullable String defaultValue) {
    }
}
