package io.kestra.core.models.validations;

import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.Optional;
import java.util.Set;

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
            return Optional.of(new KestraConstraintViolationException(violations));
        }

        return Optional.empty();
    }
}
