package io.kestra.core.reporter;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.Result;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ServerEventSender {

    private static final String SESSION_UUID = IdUtils.create();
    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson();

    @Inject
    @Client
    private ReactorHttpClient client;

    @Inject
    private VersionProvider versionProvider;

    @Inject
    private InstanceService instanceService;

    @Inject
    @Setter
    private UsageReportConfig usageReportConfig;

    private final ServerType serverType;

    public ServerEventSender() {
        this.serverType = KestraContext.getContext().getServerType();
    }

    public void send(final Instant now, final Type type, Object event) {
        ServerEvent serverEvent = buildServerEvent(now, event);
        try {
            MutableHttpRequest<ServerEvent> request = this.request(serverEvent, type);

            if (log.isTraceEnabled()) {
                log.trace("Report anonymous usage: '{}'", OBJECT_MAPPER.writeValueAsString(serverEvent));
            }

            client.toBlocking().retrieve(request, Argument.of(Result.class), Argument.of(JsonError.class));
        } catch (HttpClientResponseException t) {
            log.trace("Unable to report anonymous usage with body '{}'", t.getResponse().getBody(String.class), t);
        } catch (Exception t) {
            log.trace("Unable to handle anonymous usage", t);
        }
    }

    /**
     * Builds a {@link ServerEvent} wrapping the given payload.
     *
     * @param now the current time
     * @param event the event payload
     * @return the built server event
     */
    protected ServerEvent buildServerEvent(final Instant now, Object event) {
        return ServerEvent
            .builder()
            .uuid(UUID.randomUUID().toString())
            .sessionUuid(SESSION_UUID)
            .instanceUuid(instanceService.fetch())
            .serverType(serverType)
            .serverVersion(versionProvider.getVersion())
            .reportedAt(now.atZone(ZoneId.systemDefault()))
            .payload(event)
            .zoneId(ZoneId.systemDefault())
            .build();
    }

    protected MutableHttpRequest<ServerEvent> request(ServerEvent event, Type type) throws Exception {
        String uri = this.usageReportConfig.uri().toString();
        URI baseUri = URI.create(uri.endsWith("/") ? uri : uri + "/");
        URI resolvedUri = baseUri.resolve(type.name().toLowerCase());
        return HttpRequest.POST(resolvedUri, event)
            .header("User-Agent", "Kestra/" + versionProvider.getVersion());
    }

    /**
     * Sends a pre-serialized event payload (raw JSON bytes) to the reporting endpoint.
     * <p>
     * This is used by the controller to relay telemetry reports received from workers
     * without deserializing and re-serializing the event.
     *
     * @param payload the raw JSON bytes of the serialized {@link ServerEvent}
     * @param eventType the event type name
     */
    public void sendRaw(final byte[] payload, final String eventType) {
        try {
            MutableHttpRequest<byte[]> request = this.requestRaw(payload, eventType);
            client.toBlocking().exchange(request);
        } catch (HttpClientResponseException t) {
            log.trace("Unable to relay telemetry report with body '{}'", t.getResponse().getBody(String.class), t);
        } catch (Exception t) {
            log.trace("Unable to relay telemetry report", t);
        }
    }

    /**
     * Builds an HTTP request for raw pre-serialized bytes.
     * <p>
     * Subclasses can override this to add custom headers (e.g., license headers).
     *
     * @param payload the raw JSON bytes
     * @param eventType the event type name
     * @return the mutable HTTP request
     */
    protected MutableHttpRequest<byte[]> requestRaw(byte[] payload, String eventType) throws Exception {
        URI baseUri = URI.create(this.usageReportConfig.uri().toString().endsWith("/") ? this.usageReportConfig.uri().toString() : this.usageReportConfig.uri() + "/");
        URI resolvedUri = baseUri.resolve(eventType.toLowerCase());
        return HttpRequest.POST(resolvedUri, payload)
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header("User-Agent", "Kestra/" + versionProvider.getVersion());
    }
}
