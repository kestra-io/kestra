package io.kestra.core.models.dashboards;

import java.util.Set;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class ChartOptionTest {

    @Inject
    private Validator validator;

    @Test
    void shouldDefaultRowSpanToOne() {
        ChartOption option = ChartOption.builder()
            .displayName("KPI")
            .build();

        assertThat(option.getRowSpan()).isEqualTo(1);
        assertThat(validator.validate(option)).isEmpty();
    }

    @Test
    void shouldAcceptRowSpanWithinAllowedRange() {
        for (int rowSpan = 1; rowSpan <= 4; rowSpan++) {
            ChartOption option = ChartOption.builder()
                .displayName("Errors")
                .rowSpan(rowSpan)
                .build();

            Set<ConstraintViolation<ChartOption>> violations = validator.validate(option);
            assertThat(violations)
                .as("rowSpan=%d should be valid", rowSpan)
                .isEmpty();
        }
    }

    @Test
    void shouldRejectRowSpanBelowMinimum() {
        ChartOption option = ChartOption.builder()
            .displayName("Errors")
            .rowSpan(0)
            .build();

        assertThat(validator.validate(option))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("rowSpan"));
    }

    @Test
    void shouldRejectRowSpanAboveMaximum() {
        ChartOption option = ChartOption.builder()
            .displayName("Errors")
            .rowSpan(5)
            .build();

        assertThat(validator.validate(option))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("rowSpan"));
    }
}
