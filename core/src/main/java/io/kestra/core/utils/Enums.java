package io.kestra.core.utils;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility method for Enums.
 */
public final class Enums {


    /**
     * Gets the enum for specified string name.
     *
     * @param value    The enum raw value.
     * @param enumType The enum class type.
     * @param <T>      The enum type.
     * @return The Enum.
     * @throws IllegalArgumentException if no enum exists for the specified value.
     */
    public static <T extends Enum<T>> T getForNameIgnoreCase(final @Nullable String value,
                                                             final @NotNull Class<T> enumType) {
        return getForNameIgnoreCase(value, enumType, Map.of());

    }

    /**
     * Gets the enum for specified string name.
     *
     * @param value    The enum raw value.
     * @param enumType The enum class type.
     * @param fallback The fallback map for unknown string.
     * @param <T>      The enum type.
     * @return The Enum.
     * @throws IllegalArgumentException if no enum exists for the specified value.
     */
    public static <T extends Enum<T>> T getForNameIgnoreCase(final @Nullable String value,
                                                             final @NotNull Class<T> enumType,
                                                             final @NotNull Map<String, T> fallback) {
        if (value == null) throw new IllegalArgumentException("Unsupported value 'null'");

        final Map<String, T> fallbackMap = fallback.entrySet()
            .stream()
            .collect(Collectors.toMap(entry -> entry.getKey().toUpperCase(Locale.ROOT), Map.Entry::getValue));

        T[] values = enumType.getEnumConstants();
        return Arrays.stream(values)
            .filter(e -> e.name().equals(value.toUpperCase(Locale.ROOT)))
            .findFirst()
            .or(() -> Optional.ofNullable(fallbackMap.get(value.toUpperCase(Locale.ROOT))))
            .orElseThrow(() -> new IllegalArgumentException(String.format(
                "Unsupported enum value '%s'. Expected one of: %s",
                value,
                Arrays.stream(values)
                    .map(Enum::name)
                    .collect(Collectors.joining(", ", "[", "]"))
            )));
    }


    private Enums() {
    }
}
