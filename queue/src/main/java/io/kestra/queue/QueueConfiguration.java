package io.kestra.queue;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(value = "kestra.queue")
public record QueueConfiguration(
    @NotNull
    String type
) {
}
