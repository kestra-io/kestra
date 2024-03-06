package io.kestra.webserver.controllers.api;

import io.kestra.webserver.controllers.domain.MarketplaceRequestType;
import io.kestra.webserver.services.MarketplaceRequestMapper;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import jakarta.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Controller("/api/v1/editor")
public class EditorController {
    @Inject
    @Client("remote-api")
    private HttpClient httpClient;

    @Inject
    private HttpHostResolver httpHostResolver;

    @Inject
    private MarketplaceRequestMapper marketplaceRequestMapper;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/marketplace/{type}{/path:/.*}")
    @Operation(tags = {"Marketplace"}, summary = "Marketplace extensions operations")
    public HttpResponse<String> marketplaceGet(
        @Parameter(description = "Type of request") @PathVariable MarketplaceRequestType type,
        @Parameter(description = "Additional path") @PathVariable @Nullable String path
    ) {
        // proxied
        return null;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/marketplace/{type}{/path:/.*}")
    @Operation(tags = {"Marketplace"}, summary = "Marketplace extensions operations")
    public HttpResponse<String> marketplacePost(
        @Parameter(description = "Type of request") @PathVariable MarketplaceRequestType type,
        @Parameter(description = "Additional path") @PathVariable @Nullable String path
    ) throws URISyntaxException {
        // proxied
        return null;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/marketplace/resource/{publisher}/{extension}/{version}{/path:/.*}")
    @Operation(tags = {"Marketplace"}, summary = "Marketplace extensions resources operations")
    public Publisher<HttpResponse<String>> marketplaceResource(
        @Parameter(description = "Publisher id") @PathVariable String publisher,
        @Parameter(description = "Extension name") @PathVariable String extension,
        @Parameter(description = "Extension version") @PathVariable String version,
        @Parameter(description = "Path of the resource") @PathVariable String path,
        HttpRequest<?> httpRequest
    ) {
        String localhost = httpHostResolver.resolve(httpRequest);
        String resourceBaseUrl = marketplaceRequestMapper.resourceBaseUrl(publisher);

        return Publishers.map(
            httpClient.exchange(
                httpRequest.mutate()
                    .uri(URI.create(resourceBaseUrl + "/" + publisher + "/" + extension + "/" + version + path))
                    .headers(headers -> headers.set("Host", resourceBaseUrl.replaceFirst("https?://([^/]*).*", "$1").toLowerCase())),
                String.class
            ), response -> {
                String body = response.body();
                if (body == null) {
                    return response;
                }

                MutableHttpResponse<String> newResponse = HttpResponse.ok(
                    path.equals("/extension")
                        ? body.replace(resourceBaseUrl, localhost + "/api/v1/editor/marketplace/resource")
                        : body
                );
                return Optional.ofNullable(response.header("Content-Type"))
                    .map(contentType -> newResponse.header("Content-Type", contentType))
                    .orElse(newResponse);
            }
        );
    }
}
