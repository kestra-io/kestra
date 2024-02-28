package io.kestra.core.validations;

import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
class ScheduleTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void cronValidation()  {
        Schedule build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("* * * * *")
            .lateMaximumDelay(Duration.ofSeconds(10))
            .build();

        assertThat(modelValidator.isValid(build).isPresent(), is(false));
    }

    @Test
    void intervalValidation() {
        Schedule build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("* * * * *")
            .interval(Duration.ofSeconds(5))
            .build();


        assertThat(modelValidator.isValid(build).isPresent(), is(true));
        assertThat(modelValidator.isValid(build).get().getMessage(), containsString("interval: must be null"));

    }
}
