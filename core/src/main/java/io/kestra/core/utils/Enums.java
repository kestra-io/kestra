package io.kestra.core.utils;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
                                                             final @NotNull Class<T> enumType,
                                                             final T defaultValue) {
        if (value == null) throw new IllegalArgumentException("Unsupported value 'null'");

        T[] values = enumType.getEnumConstants();
        return Arrays.stream(values)
            .filter(e -> e.name().equals(value.toUpperCase(Locale.ROOT)))
            .findFirst()
            .orElse(defaultValue);
    }

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

    /**
     * Gets all the enum values except the one to exclude.
     *
     * @param enumType The enum class type.
     * @param toExclude The enum values to exclude.
     * @param <T>      The enum type.
     * @return The Enum value.
     */
    public static <T extends Enum<T>> Set<T> allExcept(final @NotNull Class<T> enumType,  Set<T> toExclude) {
        T[] values = enumType.getEnumConstants();
        return Arrays.stream(values)
            .filter(Predicate.not(toExclude::contains))
            .collect(Collectors.toSet());
    }

    /**
     * Converts a string to its corresponding enum value based on a provided mapping.
     *
     * @param value    The string representation of the enum value.
     * @param mapping  A map of string values to enum constants.
     * @param typeName A descriptive name of the enum type (used in error messages).
     * @param <T>      The type of the enum.
     * @return The corresponding enum constant.
     * @throws IllegalArgumentException If the string does not match any enum value.
     */
    public static <T extends Enum<T>> T fromString(String value, Map<String, T> mapping, String typeName) {
        return Optional.ofNullable(mapping.get(value))
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported %s '%s'. Expected one of: %s".formatted(typeName, value, mapping.keySet())
            ));
    }

    /**
     * Convert an object to a list of a specific enum.
     * @param value the object to convert to list of enum.
     * @param enumClass the class of the enum to convert to.
     * @return A list of the corresponding enum type
     * @param <T> The type of the enum.
     * @throws IllegalArgumentException If the value does not match any enum value.
     */
    public static <T extends Enum<T>> List<T> fromList(Object value, Class<T> enumClass) {
        return switch (value) {
            case List<?> list when !list.isEmpty() && enumClass.isInstance(list.getFirst()) -> (List<T>) list;
            case List<?> list when !list.isEmpty() && list.getFirst() instanceof String ->
                list.stream().map(item -> Enum.valueOf(enumClass, item.toString().toUpperCase())).collect(Collectors.toList());
            case Enum<?> enumValue when enumClass.isInstance(enumValue) -> List.of(enumClass.cast(enumValue));
            case String stringValue -> List.of(Enum.valueOf(enumClass, stringValue.toUpperCase()));
            default -> throw new IllegalArgumentException("Field requires a " + enumClass.getSimpleName() + " or List<" + enumClass.getSimpleName() + "> value");
        };
    }

    private Enums() {
    }
}
