package io.kestra.core.exceptions;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

public class DeserializationException extends RuntimeException {
    public DeserializationException(InvalidTypeIdException cause) {
        super(cause);
    }

    public DeserializationException(Throwable cause) {
        super(cause);
    }
}
