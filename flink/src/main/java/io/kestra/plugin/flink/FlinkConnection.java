package io.kestra.plugin.flink;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

/**
 * Configuration for connecting to a Flink cluster.
 */
@Builder
@Getter
@Jacksonized
public class FlinkConnection {

    @Schema(
        title = "Flink REST API URL",
        description = "The base URL of the Flink REST API, typically http://localhost:8081 for local clusters"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> url;

    @Schema(
        title = "Request timeout",
        description = "Maximum time to wait for API requests to complete"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Duration> timeout = Property.of(Duration.ofMinutes(5));

    @Schema(
        title = "Additional HTTP headers",
        description = "Custom headers to include in requests to the Flink REST API"
    )
    @PluginProperty(dynamic = true)
    private Property<Map<String, String>> headers;

    @Schema(
        title = "Authentication token",
        description = "Bearer token for authentication (if required by your Flink setup)"
    )
    @PluginProperty(dynamic = true)
    private Property<String> authToken;

    @Schema(
        title = "Trust all SSL certificates",
        description = "Whether to disable SSL certificate validation (not recommended for production)"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> trustAllCerts = Property.of(false);
}