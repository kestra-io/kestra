package io.kestra.core.services;

import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.runners.FollowLogEvent;

/**
 * Predicate that decides whether a {@link FollowLogEvent} passes a list of {@link QueryFilter}s.
 * Used by {@link LogStreamingService} to filter events being fanned out to SSE subscribers.
 * <p>
 * The implementation lives in the webserver module (backed by {@code Searchable<FollowLogEvent>})
 * — this interface keeps {@code core} free of that dependency.
 */
@FunctionalInterface
public interface FollowLogEventMatcher {
    boolean matches(FollowLogEvent event, List<QueryFilter> filters);
}
