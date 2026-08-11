package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.utils.RegexUtils;
import io.kestra.core.validations.Regex;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class StringInput extends Input<String> {
    @Schema(
        title = "Regular expression validating the value."
    )
    @Regex
    String validator;

    @Override
    public void validate(String input) throws ConstraintViolationException {
        if (validator != null) {
            try {
                if (!RegexUtils.matches(validator, input)) {
                    throw ManualConstraintViolation.toConstraintViolationException(
                        "it must match the pattern `" + validator + "`",
                        this,
                        StringInput.class,
                        getId(),
                        input
                    );
                }
            } catch (RegexUtils.RegexTimeoutException e) {
                throw ManualConstraintViolation.toConstraintViolationException(
                    "the validator pattern `" + validator + "` timed out — it may be too complex",
                    this,
                    StringInput.class,
                    getId(),
                    input
                );
            }
        }
    }
}
