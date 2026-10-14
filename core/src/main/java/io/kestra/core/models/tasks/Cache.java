package io.kestra.core.models.tasks;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Cache {
    @NotNull
    private Boolean enabled;

    @DurationMin(millis = 1, message = "must be a positive duration")
    private Duration ttl;
}
