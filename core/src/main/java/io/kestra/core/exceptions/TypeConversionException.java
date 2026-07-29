package io.kestra.core.exceptions;

import io.kestra.core.utils.TypeConverter;

/**
 * Exception thrown by {@link TypeConverter} when a value cannot be converted to the target type.
 *
 * <p>
 * Extends {@link IllegalArgumentException} so that existing {@code IllegalArgumentException}
 * handling (e.g. the webserver's HTTP 400 mapping and repository filter wrapping) applies to
 * conversion failures without additional wiring. The original parse failure is preserved as the cause.
 */
public class TypeConversionException extends IllegalArgumentException {

    public TypeConversionException(String message) {
        super(message);
    }

    public TypeConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
