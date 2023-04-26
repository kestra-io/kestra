package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class DateTimeInput extends Input<Instant> {
    @Override
    public void validate(Instant input) throws ConstraintViolationException {
        // no validation yet
    }
}
