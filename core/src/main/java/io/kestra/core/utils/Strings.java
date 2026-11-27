package io.kestra.core.utils;

import jakarta.annotation.Nullable;

public final class Strings {

    private Strings() {
    }

    public static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns {@code null} when the value is null, empty or whitespace only, and the value unchanged otherwise.
     */
    @Nullable
    public static String nullIfBlank(@Nullable String value) {
        return isBlank(value) ? null : value;
    }

    /**
     * Returns the value cut to {@code maxLength} characters followed by an ellipsis when it is longer, and the value
     * unchanged otherwise, including {@code null}.
     */
    @Nullable
    public static String truncate(@Nullable String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }
}
