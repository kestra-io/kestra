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
public class Usage {
    @NotNull
    private final String uuid;

    @NotNull
    private final String startUuid;

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
    private final HostUsage host;

    @Valid
    private final List<PluginUsage> plugins;

    @Valid
    private final FlowUsage flows;

    @Valid
    private final ExecutionUsage executions;
}
