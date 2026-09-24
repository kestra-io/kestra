package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * Thrown while deserializing a model that refuses unknown properties, such as
 * {@link io.kestra.core.models.triggers.AbstractTrigger}.
 *
 * <p>
 * It extends {@link IllegalArgumentException} on purpose: Jackson wraps it into a
 * {@code JsonMappingException}, which every flow read path already handles — the flow repositories turn it
 * into a {@code FlowWithException}, {@code YamlParser} into a {@code ConstraintViolationException}. A runtime
 * exception of another kind would escape those handlers.
 * </p>
 */
public class UnknownPropertyException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnknownPropertyException(String message) {
        super(message);
    }
}
