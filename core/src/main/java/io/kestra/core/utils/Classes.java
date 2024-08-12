package io.kestra.core.utils;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Utility class to manipulate {@link Class} objects.
 */
public interface Classes {

    static <T> T newInstance(final Class<T> c) {
        if (c == null)
            throw new IllegalArgumentException("class cannot be null");
        try {
            return c.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new KestraRuntimeException("Could not find a public no-argument constructor for " + c.getName(), e);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new KestraRuntimeException("Could not instantiate class " + c.getName(), e);
        }
    }
}
