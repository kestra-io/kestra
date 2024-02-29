package io.kestra.core.models.validations;

import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@Singleton
public class ModelValidator {
    @Inject
    Validator validator;

    public <T> void validate(T model) throws ConstraintViolationException {
        this.isValid(model)
            .ifPresent(s -> {
                throw s;
            });
    }

    public <T> Optional<ConstraintViolationException> isValid(T model) {
        Set<ConstraintViolation<T>> violations = validator.validate(model);

        if (violations.size() > 0) {
            return Optional.of(new ConstraintViolationException(violations));
        }

        return Optional.empty();
    }
}
