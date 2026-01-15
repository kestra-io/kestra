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
public class SecretInput extends Input<EncryptedString> {
    @Schema(
        title = "Regular expression validating the value."
    )
    @Regex
    String validator;

    @Override
    public void validate(EncryptedString input) throws ConstraintViolationException {
        if (validator != null && !Pattern.matches(validator, input.getValue())) {
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
