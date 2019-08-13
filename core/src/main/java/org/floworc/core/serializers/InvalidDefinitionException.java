package org.floworc.core.serializers;

import lombok.Getter;
import org.floworc.core.flows.Flow;

import javax.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class InvalidDefinitionException extends IllegalArgumentException {
    private Set<ConstraintViolation<Flow>> violations;

    private InvalidDefinitionException(String message, Set<ConstraintViolation<Flow>> violations) {
        super(message);
        this.violations = violations;
    }

    public static InvalidDefinitionException of(Set<ConstraintViolation<Flow>> violations) {
        List<String> errors = violations.stream().map(Object::toString).collect(Collectors.toList());

        return new InvalidDefinitionException(
            "Invalid Flow definitions with errors: \n- " + String.join("\n- ", errors),
            violations
        );
    }
}
