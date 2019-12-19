package org.floworc.core.serializers;

import org.floworc.core.exceptions.InvalidFlowException;
import org.floworc.core.models.flows.Flow;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import java.util.Set;

public class Validator {
    private static final javax.validation.Validator validator = Validation.byDefaultProvider()
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .buildValidatorFactory()
        .getValidator();

    public static Set<ConstraintViolation<Flow>> validate(Flow flow) {
        return validator.validate(flow);
    }

    public static boolean isValid(Flow flow) throws InvalidFlowException {
        Set<ConstraintViolation<Flow>> violations = Validator.validate(flow);

        if (violations.size() > 0) {
            throw InvalidFlowException.of(violations);
        }

        return true;
    }
}

