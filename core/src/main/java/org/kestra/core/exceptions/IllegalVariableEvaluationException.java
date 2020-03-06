package org.kestra.core.exceptions;

public class IllegalVariableEvaluationException extends Exception {
    public IllegalVariableEvaluationException(Throwable e) {
        super(e);
    }

    public IllegalVariableEvaluationException(String message) {
        super(message);
    }
}
