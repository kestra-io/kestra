package io.kestra.core.exceptions;

public class IllegalVariableEvaluationException extends InternalException {
    private static final long serialVersionUID = 1L;

    public IllegalVariableEvaluationException(Throwable e) {
        super(e);
    }

    public IllegalVariableEvaluationException(String message) {
        super(message);
    }
}
