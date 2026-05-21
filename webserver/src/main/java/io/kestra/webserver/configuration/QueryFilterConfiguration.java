package io.kestra.webserver.configuration;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurable safety caps on the shape of {@link QueryFilter} trees produced by the binder.
 * <p>
 * Two caps are enforced at parse time:
 * <ul>
 *   <li>{@code max-depth} — how deeply nested {@code [and|or][N]} groups may go in a single URL param.</li>
 *   <li>{@code max-width} — how many children any single tree node may have.</li>
 * </ul>
 * Both caps default to a hard floor ({@value #FLOOR_DEPTH}, {@value #FLOOR_WIDTH}). Configured values
 * below the floor are clamped at startup with a WARN-level log; the bean never exposes a value below the floor.
 * <p>
 * Per-Resource overrides are independent of the global value — they may be tighter or looser than the global cap,
 * but still subject to the same floor.
 */
@Slf4j
@Getter
@Setter
@ConfigurationProperties("kestra.query-filter")
public class QueryFilterConfiguration {
    public static final int FLOOR_DEPTH = 3;
    public static final int FLOOR_WIDTH = 20;

    private int maxDepth = FLOOR_DEPTH;
    private int maxWidth = FLOOR_WIDTH;
    private Map<String, ResourceLimits> resources = Collections.emptyMap();

    @PostConstruct
    void clampToFloor() {
        if (maxDepth < FLOOR_DEPTH) {
            log.warn("kestra.query-filter.max-depth ({}) is below the floor of {} - clamping to {}",
                maxDepth, FLOOR_DEPTH, FLOOR_DEPTH);
            maxDepth = FLOOR_DEPTH;
        }
        if (maxWidth < FLOOR_WIDTH) {
            log.warn("kestra.query-filter.max-width ({}) is below the floor of {} - clamping to {}",
                maxWidth, FLOOR_WIDTH, FLOOR_WIDTH);
            maxWidth = FLOOR_WIDTH;
        }
        resources.forEach((key, limits) -> {
            if (limits.getMaxDepth() != null && limits.getMaxDepth() < FLOOR_DEPTH) {
                log.warn("kestra.query-filter.resources.{}.max-depth ({}) is below the floor of {} - clamping to {}",
                    key, limits.getMaxDepth(), FLOOR_DEPTH, FLOOR_DEPTH);
                limits.setMaxDepth(FLOOR_DEPTH);
            }
            if (limits.getMaxWidth() != null && limits.getMaxWidth() < FLOOR_WIDTH) {
                log.warn("kestra.query-filter.resources.{}.max-width ({}) is below the floor of {} - clamping to {}",
                    key, limits.getMaxWidth(), FLOOR_WIDTH, FLOOR_WIDTH);
                limits.setMaxWidth(FLOOR_WIDTH);
            }
        });
    }

    public int maxDepthFor(QueryFilter.Resource resource) {
        return lookup(resource).map(ResourceLimits::getMaxDepth).orElse(maxDepth);
    }

    public int maxWidthFor(QueryFilter.Resource resource) {
        return lookup(resource).map(ResourceLimits::getMaxWidth).orElse(maxWidth);
    }

    private Optional<ResourceLimits> lookup(QueryFilter.Resource resource) {
        if (resource == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resources.get(resource.name()))
            .or(() -> Optional.ofNullable(resources.get(resource.name().toLowerCase())));
    }

    @Getter
    @Setter
    public static class ResourceLimits {
        private Integer maxDepth;
        private Integer maxWidth;
    }
}
