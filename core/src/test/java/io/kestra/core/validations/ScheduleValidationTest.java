package io.kestra.core.validations;

import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ScheduleValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void cronValidation() throws Exception {
        Schedule build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("* * * * *")
            .build();

        assertThat(modelValidator.isValid(build).isEmpty()).isEqualTo(true);

        build = Schedule.builder()
            .type(Schedule.class.getName())
            .cron("$ome Inv@lid Cr0n")
            .build();

        assertThat(modelValidator.isValid(build).isPresent()).isEqualTo(true);
        assertThat(modelValidator.isValid(build).get().getMessage()).contains("invalid cron expression");
    }

    @Test
    void nicknameValidation() throws Exception {
        Schedule build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("@hourly")
            .build();

        assertThat(modelValidator.isValid(build).isEmpty()).isEqualTo(true);
    }

    @Test
    void withSecondsValidation() throws Exception {
        Schedule build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .withSeconds(true)
            .cron("* * * * * *")
            .build();

        assertThat(modelValidator.isValid(build).isEmpty()).isEqualTo(true);

        build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("* * * * * *")
            .build();

        assertThat(modelValidator.isValid(build).isPresent()).isEqualTo(true);
        assertThat(modelValidator.isValid(build).get().getMessage()).contains("invalid cron expression");
    }

    @Test
    void lateMaximumDelayValidation()  {
        Schedule build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("* * * * *")
            .lateMaximumDelay(Duration.ofSeconds(10))
            .build();

        assertThat(modelValidator.isValid(build).isPresent()).isEqualTo(false);
    }

    @Test
    void intervalValidation() {
        Schedule build = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("* * * * *")
            .interval(Duration.ofSeconds(5))
            .build();


        assertThat(modelValidator.isValid(build).isPresent()).isEqualTo(true);
        assertThat(modelValidator.isValid(build).get().getMessage()).contains("interval: must be null");

    }
}
