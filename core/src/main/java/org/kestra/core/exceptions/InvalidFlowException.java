package org.kestra.core.exceptions;

import lombok.Getter;
import org.kestra.core.models.flows.Flow;

import javax.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class InvalidFlowException extends IllegalArgumentException {
    private Set<ConstraintViolation<Flow>> violations;

    private InvalidFlowException(String message, Set<ConstraintViolation<Flow>> violations) {
        super(message);
        this.violations = violations;
    }

    public static InvalidFlowException of(Set<ConstraintViolation<Flow>> violations) {
        List<String> errors = violations.stream().map(Object::toString).collect(Collectors.toList());

        return new InvalidFlowException(
            "Invalid Flow definitions with errors: \n- " + String.join("\n- ", errors),
            violations
        );
    }
}
