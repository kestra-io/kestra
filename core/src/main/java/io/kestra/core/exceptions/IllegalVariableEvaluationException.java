package io.kestra.core.exceptions;

import java.io.Serial;

public class IllegalVariableEvaluationException extends InternalException {
    @Serial
    private static final long serialVersionUID = 1L;

    public IllegalVariableEvaluationException(Throwable e) {
        super(e);
    }

    public IllegalVariableEvaluationException(String message) {
        super(message);
    }

    public IllegalVariableEvaluationException(String message, Throwable e) {
        super(message, e);
    }
}
