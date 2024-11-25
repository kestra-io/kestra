package io.kestra.core.validations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.models.validations.ModelValidator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class NoSystemLabelValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldReportAViolation() {
        var sla =  MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label("system.sla", "violated")))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);

        assertThat(valid.isPresent(), is(true));
        assertThat(valid.get().getMessage(), is("labels[0].<list element>: System labels can only be set by Kestra itself, offending label: system.sla=violated.\n"));
    }

    @Test
    void shouldSuccess() {
        var sla =  MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label("sla", "violated")))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);

        assertThat(valid.isEmpty(), is(true));
    }
}
