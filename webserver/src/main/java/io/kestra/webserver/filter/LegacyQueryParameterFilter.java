package io.kestra.webserver.filter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.webserver.errors.ProblemFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.ServerFilterPhase;

/**
 * Rejects a {@code by-query} request that passes a filter in the legacy flat form, for example
 * {@code DELETE /executions/by-query?namespace=x}, instead of {@code filters[namespace][EQUALS]=x}.
 *
 * <p>
 * By-query endpoints only bind {@code filters[...]}. A flat parameter is silently ignored, the filter list
 * arrives empty and the action runs on every matching object of the tenant: a stale client or a typo deletes,
 * kills or replays everything (kestra#18246). Rejecting the request is the only safe answer.
 *
 * <p>
 * A parameter is legacy when its name is a {@link QueryFilter.Field} value: those are exactly the names that
 * used to be accepted as flat parameters and are now only accepted inside {@code filters[...]}. Deriving them from
 * the enum keeps this filter in sync with the fields without listing endpoints or parameters by hand. None of the
 * by-query endpoints' own query values ({@code deleteLogs}, {@code newState}, {@code latestRevision}, ...) is a
 * field name.
 *
 * <p>
 * Only mutating requests are checked. The by-query exports ({@code GET}) are read-only, and the UI builds
 * their query from the browser URL, where an old bookmark may still carry a flat parameter.
 *
 * <p>
 * Runs after the security filters, so an unauthenticated caller still gets a 401, not a hint about the API.
 */
@ServerFilter(patternStyle = FilterPatternStyle.REGEX, value = "/api/v1/.*/by-query(/.*)?")
public class LegacyQueryParameterFilter implements Ordered {
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    static final Set<String> LEGACY_PARAMETERS = Arrays.stream(QueryFilter.Field.values())
        .map(QueryFilter.Field::value)
        .collect(Collectors.toUnmodifiableSet());

    private final ProblemFactory problems;

    public LegacyQueryParameterFilter(final ProblemFactory problems) {
        this.problems = Objects.requireNonNull(problems, "problems must not be null");
    }

    @RequestFilter
    @Nullable
    public HttpResponse<?> rejectLegacyParameters(@NonNull HttpRequest<?> request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return null;
        }
        List<String> legacy = request.getParameters().names().stream()
            .filter(LEGACY_PARAMETERS::contains)
            .sorted()
            .toList();
        if (legacy.isEmpty()) {
            return null;
        }

        return problems.response(
            request, new InvalidQueryFiltersException(
                legacy.stream()
                    .map(name -> "'%s' is not a supported query parameter on this endpoint, use filters[%s][EQUALS]=<value>".formatted(name, name))
                    .toList()
            )
        );
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.after();
    }
}
