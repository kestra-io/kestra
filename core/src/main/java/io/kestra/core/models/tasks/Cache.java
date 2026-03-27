package io.kestra.core.models.tasks;

import java.time.Duration;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class Cache {
    @NotNull
    private Boolean enabled;

    private Duration ttl;
}
