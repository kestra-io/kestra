package io.kestra.core.models.flows.input;

import java.util.regex.Pattern;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;

import jakarta.validation.ConstraintViolationException;

public class EmailInput extends Input<String> {

    private static final String EMAIL_PATTERN = "^$|^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";

    @Override
    public void validate(String input) throws ConstraintViolationException {
        if (!Pattern.matches(EMAIL_PATTERN, input)) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "The input must be a valid email",
                this,
                EmailInput.class,
                getId(),
                input
            );
        }
    }
}
