package io.kestra.core.models.executions.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder(toBuilder = true)
public class LogStatistics {
    @NotNull
    protected Instant timestamp;

    @Builder.Default
    @JsonInclude
    private Map<Level, Long> counts = new HashMap<>(ImmutableMap.<Level, Long>builder()
        .put(Level.TRACE, 0L)
        .put(Level.DEBUG, 0L)
        .put(Level.INFO, 0L)
        .put(Level.WARN, 0L)
        .put(Level.ERROR, 0L)
        .build()
    );

    private String groupBy;
}
