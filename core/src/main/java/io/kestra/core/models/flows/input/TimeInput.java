package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalTime;
import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class TimeInput extends Input<LocalTime> {
    @Override
    public void validate(LocalTime input) throws ConstraintViolationException {
        // no validation yet
    }
}
