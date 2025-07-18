package io.kestra.core.exceptions;

import java.io.Serial;
import java.util.List;
import lombok.Getter;

/**
 * General exception that can be throws when a resource fail validation.
 */
public class ValidationErrorException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final String VALIDATION_ERROR_MESSAGE = "Resource fails validation";

    @Getter
    private transient final List<String> invalids;

    /**
     * Creates a new {@link ValidationErrorException} instance.
     *
     * @param invalids the invalid filters.
     */
    public ValidationErrorException(final List<String> invalids) {
        super(VALIDATION_ERROR_MESSAGE);
        this.invalids = invalids;
    }


    public String formatedInvalidObjects(){
        if (invalids == null || invalids.isEmpty()){
            return VALIDATION_ERROR_MESSAGE;
        }
        return String.join(", ", invalids);
    }
}
