package io.kestra.webserver.controllers.api;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Controller("/api/v1/{tenant}/blueprints/community")
public class BlueprintController {
    @Inject
    @Client("api")
    private HttpClient httpClient;
    @Inject
    protected VersionProvider versionProvider;

    @SuppressWarnings("unchecked")
    @ExecuteOn(TaskExecutors.IO)
    @Get("/{kind}")
    @Operation(
        tags = { "Blueprints" },
        summary = "List all blueprints",
        description = "Community blueprints are served by an external API, so complex query filters are not supported. "
            + "Only top-level `QUERY` and `TAGS` conditions are honored; logical (AND/OR) and nested filter groups are rejected with a 400."
    )
    public PagedResults<ApiBlueprintItem> searchBlueprints(
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") Optional<String> sort,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) Integer page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "1") @Min(1) Integer size,
        @Parameter(description = "The blueprint kind") Kind kind,
        @Parameter(description = "A list of query filters. Complex filters are not supported: only top-level QUERY and TAGS conditions are honored, and logical (AND/OR) or nested filter groups are rejected.") @Nullable @QueryFilterFormat(QueryFilter.Resource.BLUEPRINT) List<QueryFilter> filters,
        HttpRequest<?> httpRequest) throws URISyntaxException {

        Map<String, Object> extraParams = new LinkedHashMap<>(blueprintFilterQueryParams(filters));
        extraParams.put("ee", false);

        return fastForwardToKestraApi(httpRequest, getApiBasePath(kind), extraParams, Argument.of(PagedResults.class, ApiBlueprintItem.class));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(value = "/{kind}/{id}/source", produces = "application/yaml")
    @Operation(tags = { "Blueprints" }, summary = "Get a blueprint flow")
    public String getBlueprintSource(
        @Parameter(description = "The blueprint id") String id,
        @Parameter(description = "The blueprint kind") Kind kind,
        HttpRequest<?> httpRequest) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, getApiBasePath(id, kind) + "/source", Argument.of(String.class));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(value = "/{kind}/{id}/graph")
    @Operation(tags = { "Blueprints" }, summary = "Get a blueprint graph")
    public Map<String, Object> getBlueprintGraph(
        @Parameter(description = "The blueprint id") String id,
        @Parameter(description = "The blueprint kind") Kind kind,
        HttpRequest<?> httpRequest) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, getApiBasePath(id, kind) + "/graph", Argument.mapOf(String.class, Object.class));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(value = "/{kind}/{id}")
    @Operation(tags = { "Blueprints" }, summary = "Get a blueprint")
    public ApiBlueprintItemWithSource getBlueprint(
        @Parameter(description = "The blueprint id") String id,
        @Parameter(description = "The blueprint kind") Kind kind,
        HttpRequest<?> httpRequest) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, getApiBasePath(id, kind), Argument.of(ApiBlueprintItemWithSource.class));
    }

    @SuppressWarnings("unchecked")
    @ExecuteOn(TaskExecutors.IO)
    @Get("/{kind}/tags")
    @Operation(
        tags = { "Blueprint Tags" },
        summary = "List blueprint tags matching the filter",
        description = "Community blueprints are served by an external API, so complex query filters are not supported. "
            + "Only top-level `QUERY` and `TAGS` conditions are honored; logical (AND/OR) and nested filter groups are rejected with a 400."
    )
    public List<ApiBlueprintTagItem> listBlueprintTags(
        @Parameter(description = "The blueprint kind") Kind kind,
        @Parameter(description = "A list of query filters. Complex filters are not supported: only top-level QUERY and TAGS conditions are honored, and logical (AND/OR) or nested filter groups are rejected.") @Nullable @QueryFilterFormat(QueryFilter.Resource.BLUEPRINT) List<QueryFilter> filters,
        HttpRequest<?> httpRequest) throws URISyntaxException {

        return fastForwardToKestraApi(httpRequest, getApiBasePath(kind) + "/tags", blueprintFilterQueryParams(filters), Argument.of(List.class, ApiBlueprintTagItem.class));
    }

    /**
     * Translates {@link QueryFilter} entries for the BLUEPRINT resource into the legacy
     * {@code q} / {@code tags} query string params the downstream community API expects.
     * Validates field/operation pairs against {@link QueryFilter.Resource#BLUEPRINT}.
     * Returns an empty map when no relevant filters are present.
     */
    protected static Map<String, Object> blueprintFilterQueryParams(@Nullable List<QueryFilter> filters) {
        QueryFilter.validateQueryFilters(filters, QueryFilter.Resource.BLUEPRINT);
        rejectUnsupportedComplexFilters(filters);
        String q = parseQueryFiltersForBlueprint(filters);
        List<String> tags = parseTagsFromQueryFiltersForBlueprint(filters);
        Map<String, Object> params = new LinkedHashMap<>();
        if (q != null && !q.isEmpty()) {
            params.put("q", q);
        }
        if (tags != null && !tags.isEmpty()) {
            params.put("tags", tags);
        }
        return params;
    }

    /**
     * Blueprints proxy to the community API, which only understands flat {@code q} / {@code tags}
     * params. Logical (AND/OR) or nested filter groups cannot be translated, so reject them with a
     * clear error instead of silently dropping them — they would otherwise be ignored by the
     * top-level field scans in {@link #parseQueryFiltersForBlueprint} / {@link #parseTagsFromQueryFiltersForBlueprint}.
     */
    private static void rejectUnsupportedComplexFilters(@Nullable List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        if (filters.stream().anyMatch(QueryFilter::isNode)) {
            throw new InvalidQueryFiltersException(
                "Resource: BLUEPRINT does not support logical (AND/OR) or nested filter groups; only top-level QUERY and TAGS conditions are supported"
            );
        }
    }

    protected static String parseQueryFiltersForBlueprint(@Nullable List<QueryFilter> queryFilters) {
        if (queryFilters == null || queryFilters.isEmpty()) {
            return null;
        }

        List<QueryFilter> queryFieldFilters = queryFilters.stream()
            .filter(f -> f.field() == QueryFilter.Field.QUERY)
            .toList();;

        if (queryFieldFilters.size() > 1) {
            throw new InvalidQueryFiltersException("Resource: BLUEPRINT does not support multiple conditions on QUERY Field");
        }

        return queryFieldFilters.isEmpty() ? null : queryFieldFilters.getFirst().value().toString();
    }

    protected static List<String> parseTagsFromQueryFiltersForBlueprint(@Nullable List<QueryFilter> queryFilters) {
        if (queryFilters == null || queryFilters.isEmpty()) {
            return null;
        }

        return queryFilters.stream()
            .filter(f -> f.field() == QueryFilter.Field.TAGS)
            .findFirst()
            .map(f -> {
                Object value = f.value();
                if (value instanceof List<?> list) {
                    return list.stream().map(Object::toString).toList();
                }
                return value == null ? null : List.of(value.toString());
            })
            .orElse(null);
    }

    private String getApiBasePath(final Kind kind) {
        return "/v1/blueprints/kinds/" + kind.val() + "/versions/" + versionProvider.getVersion();
    }

    private String getApiBasePath(final String id, final Kind kind) {
        return "/v1/blueprints/kinds/" + kind.val() + "/" + id + "/versions/" + versionProvider.getVersion();
    }

    protected <T> T fastForwardToKestraApi(HttpRequest<?> originalRequest, String newPath, Argument<T> returnType) throws URISyntaxException {
        return this.fastForwardToKestraApi(originalRequest, newPath, null, returnType);
    }

    protected <T> T fastForwardToKestraApi(HttpRequest<?> originalRequest, String newPath, Map<String, Object> additionalQueryParams, Argument<T> returnType) throws URISyntaxException {
        UriBuilder uriBuilder = UriBuilder.of(originalRequest.getUri())
            .replacePath(originalRequest.getUri().getPath().toString().replaceAll("^[^?]*", newPath));

        if (additionalQueryParams != null) {
            additionalQueryParams.forEach((name, value) -> {
                if (value instanceof List<?> list) {
                    uriBuilder.queryParam(name, list.toArray());
                } else {
                    uriBuilder.queryParam(name, value);
                }
            });
        }

        return httpClient
            .toBlocking()
            .exchange(
                HttpRequest.create(
                    originalRequest.getMethod(),
                    uriBuilder
                        .build()
                        .toString()
                ),
                returnType
            )
            .body();
    }

    @Value
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class ApiBlueprintItemWithSource extends ApiBlueprintItem {
        String source;
        Kind kind;
    }

    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class ApiBlueprintItem {
        String id;
        String title;
        String description;
        List<String> includedTasks;
        @JsonInclude
        List<String> tags;
        @Builder.Default
        Instant publishedAt = Instant.now();
    }

    @Value
    @Builder
    @Jacksonized
    public static class ApiBlueprintTagItem {
        String id;
        String name;
        @Builder.Default
        Instant publishedAt = Instant.now();
    }

    public enum Kind {
        APP,
        DASHBOARD,
        FLOW;

        public String val() {
            return name().toLowerCase();
        }

        @JsonCreator
        public Kind from(String s) {
            return Enums.getForNameIgnoreCase(s, Kind.class);
        }
    }
}
