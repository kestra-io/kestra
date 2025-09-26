package io.kestra.plugin.flink;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class FlinkTask extends Task {

    @Schema(
        title = "Flink REST API URL",
        description = "The base URL of the Flink cluster's REST API, e.g., 'http://flink-jobmanager:8081'"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    protected Property<String> restUrl;

    @Schema(
        title = "Connection timeout",
        description = "Timeout for connecting to the Flink REST API in seconds. Defaults to 30."
    )
    @Builder.Default
    @PluginProperty
    protected Property<Integer> connectionTimeout = Property.of(30);

    @Schema(
        title = "Request timeout",
        description = "Timeout for REST API requests in seconds. Defaults to 120."
    )
    @Builder.Default
    @PluginProperty
    protected Property<Integer> requestTimeout = Property.of(120);
}