package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import javax.validation.ConstraintViolationException;

@SuperBuilder
@Getter
@NoArgsConstructor
public class DurationInput extends Input<Duration> {
    @Override
    public void validate(Duration input) throws ConstraintViolationException {
        // no validation yet
    }
}
