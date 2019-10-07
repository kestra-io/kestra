package org.floworc.core.exceptions;

public class MissingRequiredInput extends IllegalArgumentException {
    public MissingRequiredInput(String message) {
        super(message);
    }
}
