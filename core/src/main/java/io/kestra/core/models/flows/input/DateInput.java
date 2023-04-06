package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class DateInput extends Input<LocalDate> {
    @Override
    public void validate(LocalDate input) throws ConstraintViolationException {
        // no validation yet
    }
}
