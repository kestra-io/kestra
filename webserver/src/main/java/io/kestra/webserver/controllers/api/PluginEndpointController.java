package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.plugins.endpoint.PluginEndpointExecutionException;
import io.kestra.core.plugins.endpoint.PluginEndpointResponse;
import io.kestra.core.plugins.endpoint.PluginEndpointService;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Produces(MediaType.ALL)
@Controller("/api/v1/{tenant}/plugins")
public class PluginEndpointController {
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final PluginEndpointService pluginEndpointService;
    private final TenantService tenantService;

    @Inject
    public PluginEndpointController(PluginEndpointService pluginEndpointService, TenantService tenantService) {
        this.pluginEndpointService = pluginEndpointService;
        this.tenantService = tenantService;
    }

    @Get("/{group}/endpoints/{name}/{executionId}/{taskRunId}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<byte[]> get(
        HttpRequest<?> request,
        @PathVariable String group,
        @PathVariable String name,
        @PathVariable String executionId,
        @PathVariable String taskRunId
    ) {
        return dispatch(request, group, name, executionId, taskRunId, Map.of());
    }

    @Post("/{group}/endpoints/{name}/{executionId}/{taskRunId}")
    @Consumes(MediaType.ALL)
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<byte[]> post(
        HttpRequest<?> request,
        @PathVariable String group,
        @PathVariable String name,
        @PathVariable String executionId,
        @PathVariable String taskRunId,
        @Nullable @Body byte[] body
    ) {
        return dispatch(request, group, name, executionId, taskRunId, parseBody(body));
    }

    private static Map<String, Object> parseBody(@Nullable byte[] body) {
        if (body == null || new String(body, StandardCharsets.UTF_8).isBlank()) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A JSON object body is required.");
        }
        Map<String, Object> parsed;
        try {
            parsed = JacksonMapper.ofJson().readValue(body, JSON_OBJECT);
        } catch (IOException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "The request body must be a JSON object.");
        }
        if (parsed == null) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "The request body must be a JSON object.");
        }
        return parsed;
    }

    private HttpResponse<byte[]> dispatch(HttpRequest<?> request, String group, String name, String executionId, String taskRunId, Map<String, Object> body) {
        PluginEndpointResponse response;
        // TODO(kestra-ee#9994): handle() runs synchronously on the webserver IO thread with no
        // timeout; a hanging or CPU-spinning plugin can exhaust the pool. Run it off-thread with a
        // timeout (and catch Throwable, not just Exception) before this ships past the POC.
        try {
            response = pluginEndpointService.dispatch(
                tenantService.resolveTenant(),
                group,
                name,
                executionId,
                taskRunId,
                request.getParameters().asMap(),
                body
            );
        } catch (PluginEndpointExecutionException e) {
            log.error("Plugin endpoint '{}/{}' failed.", group, name, e.getCause());
            PluginEndpointResponse error = PluginEndpointResponse.of(Map.of("message", e.getMessage()));
            return harden(HttpResponse.serverError(error.body()), error);
        }

        return harden(HttpResponse.ok(response.body()), response);
    }

    // Plugins control the response bytes and content-type, so a response is served either as inert
    // JSON or, for anything else, forced to download — never rendered as an active document in Kestra's origin.
    private static HttpResponse<byte[]> harden(MutableHttpResponse<byte[]> response, PluginEndpointResponse plugin) {
        String type = plugin.contentType() != null ? plugin.contentType() : MediaType.APPLICATION_OCTET_STREAM;

        response.contentType(type).header("X-Content-Type-Options", "nosniff");
        if (!type.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON)) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(plugin.fileName()));
        }

        return response;
    }

    // The file name is plugin-controlled, so it is sanitized to inert characters before entering the header.
    private static String contentDisposition(@Nullable String fileName) {
        if (fileName == null) {
            return "attachment";
        }
        String safe = fileName.replaceAll("[\\p{Cntrl}\"\\\\/]", "_").strip();
        return safe.isEmpty() ? "attachment" : "attachment; filename=\"" + safe + "\"";
    }
}
