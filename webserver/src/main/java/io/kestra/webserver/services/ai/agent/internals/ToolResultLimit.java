package io.kestra.webserver.services.ai.agent.internals;

import io.micronaut.core.annotation.Nullable;

/**
 * Caps a tool result persisted before the dispatch-side limit existed, so replaying its thread stays under the provider's input limit.
 */
public final class ToolResultLimit {

    /** Maximum characters of one tool result handed to the model (about 25k tokens). */
    public static final int MAX_CHARS = 100_000;

    private ToolResultLimit() {
    }

    /**
     * @param text the raw tool result text, may be {@code null}
     * @return the text unchanged when it fits, otherwise its first {@link #MAX_CHARS} characters followed by a notice
     */
    @Nullable
    public static String cap(@Nullable final String text) {
        if (text == null || text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS)
            + "\n[truncated: the tool result was " + text.length() + " characters; only the first " + MAX_CHARS
            + " are shown. Narrow the request (a specific item or a smaller page) to see the rest.]";
    }
}
