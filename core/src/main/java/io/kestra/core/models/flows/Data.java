package io.kestra.core.models.flows;

import io.kestra.core.models.validations.ManualConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Interface for defining an identifiable and typed data.
 */
public interface Data {

    /**
     * The ID for this data.
     *
     * @return a string id.
     */
    String getId();

    /**
     * The Type for this data.
     *
     * @return a type.
     */
    Type getType();

    @SuppressWarnings("unchecked")
    default ConstraintViolationException toConstraintViolationException(String message, Object value) {
        Class<Data> cls = (Class<Data>) this.getClass();

        return ManualConstraintViolation.toConstraintViolationException(
            "Invalid " + (this instanceof Output ? "output" : "input") + " for `" + getId() + "`, " + message + ", but received `" + value + "`",
            this,
            cls,
            this.getId(),
            value
        );
    }
}
