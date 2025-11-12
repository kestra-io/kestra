package io.kestra.core.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.Result;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

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

    private final ServerType serverType;

    @Setter
    @Value("${kestra.anonymous-usage-report.uri:'https://api.kestra.io/v1/reports/server-events'}")
    protected URI url;

    public ServerEventSender( ) {
        this.serverType = KestraContext.getContext().getServerType();
    }

    public void send(final Instant now, final Type type, Object event) {
        ServerEvent serverEvent = ServerEvent
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
        try {
            MutableHttpRequest<ServerEvent> request = this.request(serverEvent, type);

            if (log.isTraceEnabled()) {
                log.trace("Report anonymous usage: '{}'", OBJECT_MAPPER.writeValueAsString(serverEvent));
            }

            this.handleResponse(client.toBlocking().retrieve(request, Argument.of(Result.class), Argument.of(JsonError.class)));
        } catch (HttpClientResponseException t) {
            log.trace("Unable to report anonymous usage with body '{}'", t.getResponse().getBody(String.class), t);
        } catch (Exception t) {
            log.trace("Unable to handle anonymous usage", t);
        }
    }

    private void handleResponse (Result result){

    }

    protected MutableHttpRequest<ServerEvent> request(ServerEvent event, Type type) throws Exception {
        URI baseUri = URI.create(this.url.toString().endsWith("/") ? this.url.toString() : this.url + "/");
        URI resolvedUri = baseUri.resolve(type.name().toLowerCase());
        return HttpRequest.POST(resolvedUri, event)
            .header("User-Agent", "Kestra/" + versionProvider.getVersion());
    }
}
