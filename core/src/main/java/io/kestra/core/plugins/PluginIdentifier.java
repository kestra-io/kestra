package io.kestra.core.plugins;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Represents the fully qualify identifier of a Kestra's plugin.
 */
public interface PluginIdentifier {

    /**
     * Helper method for parsing a string plugin identifier to extract a type and version.
     *
     * @param identifier    a string type identifier.
     * @return  a {@link Pair} of (type, version).
     */
    static Pair<String, String> parseIdentifier(final String identifier) {
        int index = identifier.indexOf(':');
        if (index == -1) {
            return Pair.of(identifier, null);
        } else {
            return Pair.of(identifier.substring(0, index), identifier.substring(index + 1));
        }
    }
}
