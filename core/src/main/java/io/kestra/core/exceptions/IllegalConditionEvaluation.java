package io.kestra.core.exceptions;

public class IllegalConditionEvaluation extends InternalException {
    private static final long serialVersionUID = 1L;

    public IllegalConditionEvaluation(Throwable e) {
        super(e);
    }

    public IllegalConditionEvaluation(String message) {
        super(message);
    }
}
