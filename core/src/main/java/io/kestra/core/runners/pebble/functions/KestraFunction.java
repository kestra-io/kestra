package io.kestra.core.runners.pebble.functions;

import java.util.Map;

import io.pebbletemplates.pebble.extension.Function;

/**
 * Extends Pebble's {@link Function} with autocompletion defaults for each argument.
 * All Kestra-provided functions must implement this interface.
 */
public interface KestraFunction extends Function {
    /**
     * Returns a map of argument names to their autocompletion default values.
     * Use {@code null} for arguments that have no meaningful default.
     * Ordering is not required — callers use {@link #getArgumentNames()} to determine order.
     */
    Map<String, String> getArgumentDefaults();
}
