package io.kestra.core.exceptions;

import java.io.Serial;

public class IllegalConditionEvaluation extends InternalException {
    @Serial
    private static final long serialVersionUID = 1L;

    public IllegalConditionEvaluation(Throwable e) {
        super(e);
    }

    public IllegalConditionEvaluation(String message) {
        super(message);
    }
}
