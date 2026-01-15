package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.Regex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.regex.Pattern;

@SuperBuilder
@Getter
@NoArgsConstructor
public class SecretInput extends Input<Object> {
    @Schema(
        title = "Regular expression validating the value."
    )
    @Regex
    String validator;

    @Override
    public void validate(Object input) throws ConstraintViolationException {
        String value = input instanceof EncryptedString ? ((EncryptedString) input).getValue(): (String) input;
        if (validator != null && !Pattern.matches(validator, value)) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must match the pattern `" + validator + "`",
                this,
                SecretInput.class,
                getId(),
                input
            );
        }
    }
}
