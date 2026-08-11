package io.kestra.core.http.client.configurations;

import java.time.Duration;

import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class TimeoutConfiguration {
    @Schema(title = "The time allowed to establish a connection to the server before failing.")
    Property<Duration> connectTimeout;

    @Schema(title = "The time allowed for a read connection to remain idle before closing it.")
    @Builder.Default
    Property<Duration> readIdleTimeout = Property.ofValue(Duration.ofMinutes(5));
}
