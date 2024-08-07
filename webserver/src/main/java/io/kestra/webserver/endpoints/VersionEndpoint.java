package io.kestra.webserver.endpoints;

import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.controllers.domain.ServerInfo;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * The 'version' management endpoint.
 */
@Endpoint(value = VersionEndpoint.NAME, defaultSensitive = VersionEndpoint.DEFAULT_SENSITIVE)
public class VersionEndpoint {
    /**
     * Constant for this endpoint.
     */
    public static final String NAME = "version";
    /**
     * If the endpoint is sensitive if no configuration is provided.
     */
    public static final boolean DEFAULT_SENSITIVE = false;
    private static final String VERSION_SUFFIX = "-oss";

    @Value("${kestra.server-type}")
    private String serverType;

    @Inject
    private VersionProvider version;

    @Read
    @SingleResult
    public Publisher<ServerInfo> version() {
        return Mono.just(new ServerInfo(
            version.getVersion() + VERSION_SUFFIX,
            version.getRevision(),
            version.getDate(),
            serverType
        ));
    }
}
