package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.plugins.endpoint.PluginEndpoint;
import io.kestra.core.plugins.endpoint.PluginEndpointRequest;
import io.kestra.core.plugins.endpoint.PluginEndpointResponse;
import io.kestra.core.utils.ListUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Produces(MediaType.ALL)
@Controller("/api/v1/{tenant}/plugins")
public class PluginEndpointController {
    private final PluginRegistry pluginRegistry;

    @Inject
    public PluginEndpointController(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @Get("/{group}/endpoints/{name}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<byte[]> get(HttpRequest<?> request, @PathVariable String group, @PathVariable String name) {
        return dispatch(request, group, name, null);
    }

    @Post("/{group}/endpoints/{name}")
    @Consumes(MediaType.ALL)
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<byte[]> post(HttpRequest<?> request, @PathVariable String group, @PathVariable String name, @Nullable @Body byte[] body) {
        return dispatch(request, group, name, body);
    }

    // EE overrides this to enforce RBAC before the plugin is invoked.
    protected void authorize(HttpRequest<?> request) {
    }

    private HttpResponse<byte[]> dispatch(HttpRequest<?> request, String group, String name, @Nullable byte[] body) {
        authorize(request);

        RegisteredPlugin plugin = pluginRegistry.plugins(p -> group.equals(p.group()))
            .stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("No plugin found for group '%s'.".formatted(group)));

        PluginEndpoint endpoint = ListUtils.emptyOnNull(plugin.getEndpoints()).stream()
            .filter(e -> e.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("No endpoint '%s' in plugin '%s'.".formatted(name, group)));

        PluginEndpointRequest pluginRequest = new PluginEndpointRequest(
            request.getParameters().asMap(),
            body != null ? body : new byte[0]
        );

        PluginEndpointResponse response;
        // TODO(kestra-ee#9994): handle() runs synchronously on the webserver IO thread with no
        // timeout; a hanging or CPU-spinning plugin can exhaust the pool. Run it off-thread with a
        // timeout (and catch Throwable, not just Exception) before this ships past the POC.
        try {
            response = endpoint.handle(pluginRequest);
        } catch (Exception e) {
            log.error("Plugin endpoint '{}/{}' failed.", group, name, e);
            PluginEndpointResponse error = PluginEndpointResponse.of(
                Map.of("message", "The plugin endpoint '%s/%s' failed to process the request.".formatted(group, name)));
            return harden(HttpResponse.serverError(error.body()), error.contentType());
        }

        return harden(HttpResponse.ok(response.body()), response.contentType());
    }

    // Plugins control the response bytes and content-type, so a response is served either as inert
    // JSON or, for anything else, forced to download — never rendered as an active document in Kestra's origin.
    private static HttpResponse<byte[]> harden(MutableHttpResponse<byte[]> response, @Nullable String contentType) {
        String type = contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM;

        response.contentType(type).header("X-Content-Type-Options", "nosniff");
        if (!type.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON)) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        }

        return response;
    }
}
