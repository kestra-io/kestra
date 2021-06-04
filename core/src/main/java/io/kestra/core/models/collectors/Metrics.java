package io.kestra.core.models.collectors;

import io.kestra.core.models.ServerType;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuperBuilder(toBuilder = true)
@Getter
@Jacksonized
@Introspected
public class Metrics {
    @NotNull
    private final String uuid;

    @NotNull
    private final ServerType serverType;

    @NotNull
    private final String version;

    @NotNull
    private final ZoneId zoneId;

    @Nullable
    private final String uri;

    @Nullable
    private final Set<String> environments;

    @NotNull
    private final Instant startTime;

    @Valid
    private final HostMetrics host;

    @Valid
    private final FlowMetrics flowMetrics;

    @Valid
    private final ExecutionMetrics executionMetrics;

    @Valid
    private final List<PluginMetrics> pluginMetrics;
}
