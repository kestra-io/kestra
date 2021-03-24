package io.kestra.core.models.validations;

import io.micronaut.validation.validator.Validator;

import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

@Singleton
public class ModelValidator {
    @Inject
    Validator validator;

    public <T> Set<ConstraintViolation<T>> validate(T flow) {
        return validator.validate(flow);
    }

    public <T> Optional<ConstraintViolationException> isValid(T flow) {
        Set<ConstraintViolation<T>> violations = this.validate(flow);

        if (violations.size() > 0) {
            return Optional.of(new ConstraintViolationException(violations));
        }

        return Optional.empty();
    }
}
