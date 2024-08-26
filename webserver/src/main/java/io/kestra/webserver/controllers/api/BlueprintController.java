package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.annotation.Introspected;
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
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Validated
@Controller("/api/v1/blueprints/community")
public class BlueprintController {
    @Inject
    @Client("api")
    private HttpClient httpClient;

    @SuppressWarnings("unchecked")
    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Blueprints"}, summary = "List all blueprints")
    public PagedResults<BlueprintItem> blueprints(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") Optional<String> q,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") Optional<String> sort,
        @Parameter(description = "A tags filter") @Nullable @QueryValue(value = "tags") Optional<List<String>> tags,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") Integer page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "1") Integer size,
        HttpRequest<?> httpRequest
    ) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, "/v1/blueprints", Map.of("ee", false), Argument.of(PagedResults.class, BlueprintItem.class));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(value = "{id}/flow", produces = "application/yaml")
    @Operation(tags = {"Blueprints"}, summary = "Get a blueprint flow")
    public String blueprintFlow(
        @Parameter(description = "The blueprint id") String id,
        HttpRequest<?> httpRequest
    ) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, "/v1/blueprints/" + id + "/flow", Argument.of(String.class));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(value = "{id}/graph")
    @Operation(tags = {"Blueprints"}, summary = "Get a blueprint graph")
    public Map<String, Object> blueprintGraph(
        @Parameter(description = "The blueprint id") String id,
        HttpRequest<?> httpRequest
    ) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, "/v1/blueprints/" + id + "/graph", Argument.mapOf(String.class, Object.class));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(value = "{id}")
    @Operation(tags = {"Blueprints"}, summary = "Get a blueprint")
    public BlueprintItemWithFlow blueprint(
        @Parameter(description = "The blueprint id") String id,
        HttpRequest<?> httpRequest
    ) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, "/v1/blueprints/" + id, Argument.of(BlueprintItemWithFlow.class));
    }

    @SuppressWarnings("unchecked")
    @ExecuteOn(TaskExecutors.IO)
    @Get("tags")
    @Operation(tags = {"Blueprint Tags"}, summary = "List blueprint tags matching the filter")
    public List<BlueprintTagItem> blueprintTags(
        @Parameter(description = "A string filter to get tags with matching blueprints only") @Nullable @QueryValue(value = "q") Optional<String> q,
        HttpRequest<?> httpRequest
    ) throws URISyntaxException {
        return fastForwardToKestraApi(httpRequest, "/v1/blueprints/tags", Argument.of(List.class, BlueprintTagItem.class));
    }

    protected  <T> T fastForwardToKestraApi(HttpRequest<?> originalRequest, String newPath, Argument<T> returnType) throws URISyntaxException {
        return this.fastForwardToKestraApi(originalRequest, newPath, null, returnType);
    }

    private <T> T fastForwardToKestraApi(HttpRequest<?> originalRequest, String newPath, Map<String, Object> additionalQueryParams, Argument<T> returnType) throws URISyntaxException {
        UriBuilder uriBuilder = UriBuilder.of(originalRequest.getUri())
            .replacePath(originalRequest.getUri().getPath().toString().replaceAll("^[^?]*", newPath));

        if (additionalQueryParams != null) {
            additionalQueryParams.forEach(uriBuilder::queryParam);
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
    @SuperBuilder
    @Jacksonized
    @Introspected
    public static class BlueprintItemWithFlow extends BlueprintItem {
        String flow;
    }

    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @Getter
    @SuperBuilder
    @Jacksonized
    @Introspected
    public static class BlueprintItem {
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
    @Introspected
    public static class BlueprintTagItem {
        String id;
        String name;
        @Builder.Default
        Instant publishedAt = Instant.now();
    }
}
