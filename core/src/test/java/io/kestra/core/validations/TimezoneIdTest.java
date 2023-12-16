package io.kestra.core.validations;

import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class TimezoneIdTest {
    @Inject
    private ModelValidator modelValidator;

    @AllArgsConstructor
    @Introspected
    @Getter
    public static class TimezoneIdCls {
        @TimezoneId
        String timezone;
    }

    @Test
    void inputValidation() {
        final TimezoneIdCls existingTimezone = new TimezoneIdCls("Europe/Paris");

        assertThat(modelValidator.isValid(existingTimezone).isEmpty(), is(true));

        final TimezoneIdCls invalidTimezone = new TimezoneIdCls("Foo/Bar");

        assertThat(modelValidator.isValid(invalidTimezone).isPresent(), is(true));
        assertThat(modelValidator.isValid(invalidTimezone).get().getMessage(), allOf(
            startsWith("timezone"),
            containsString("is not a valid time-zone ID")
        ));
    }
}
