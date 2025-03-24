package io.kestra.core.http.client.configurations;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

@Builder(toBuilder = true)
@Getter
public class TimeoutConfiguration {
    @Schema(title = "The time allowed to establish a connection to the server before failing.")
    Property<Duration> connectTimeout;

    @Schema(title = "The time allowed for a read connection to remain idle before closing it.")
    @Builder.Default
    Property<Duration> readIdleTimeout = Property.of(Duration.ofMinutes(5));
}
