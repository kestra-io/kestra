package io.kestra.core.models.dashboards;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.validations.ModelValidator;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class DashboardQueryTimeoutValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldAcceptATimeoutWithinTheMaximum() {
        assertThat(modelValidator.isValid(dashboard(Duration.ofMinutes(1)))).isEmpty();
    }

    @Test
    void shouldRejectATimeoutOverTheMaximum() {
        assertThat(modelValidator.isValid(dashboard(Duration.ofMinutes(10))))
            .hasValueSatisfying(violation -> assertThat(violation.getMessage()).contains("configured maximum of 300 seconds"));
    }

    @Test
    void shouldRejectATimeoutThatIsNotPositive() {
        assertThat(modelValidator.isValid(dashboard(Duration.ZERO)))
            .hasValueSatisfying(violation -> assertThat(violation.getMessage()).contains("must be positive"));
    }

    private static Dashboard dashboard(Duration queryTimeout) {
        return Dashboard.builder().id("dashboard").title("Dashboard").queryTimeout(queryTimeout).build();
    }
}
