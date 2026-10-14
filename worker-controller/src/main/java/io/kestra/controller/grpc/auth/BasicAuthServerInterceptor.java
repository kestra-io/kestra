package io.kestra.controller.grpc.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import io.kestra.controller.RequiresControllerServer;
import io.kestra.controller.config.GrpcBasicAuthConfiguration;
import io.kestra.core.utils.AuthUtils;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.health.v1.HealthGrpc;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Rejects any gRPC call that does not carry the configured HTTP Basic credentials.
 * <p>
 * The credentials authenticate the fleet rather than an individual worker: they carry no identity and
 * are not tied to a worker group.
 *
 * @see GrpcBasicAuthConfiguration
 */
@Singleton
@Requires(property = GrpcBasicAuthConfiguration.ENABLED_PROPERTY, value = "true")
@RequiresControllerServer
@Slf4j
public class BasicAuthServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final String BASIC_PREFIX = "Basic ";

    /**
     * Liveness probes are exempt: they are issued by orchestrators that hold no Kestra configuration, and
     * the health service exposes nothing beyond the serving status. Reflection is deliberately not exempt.
     * Derived from the generated service name so it cannot drift, and anchored with a trailing {@code /} so
     * an unrelated service whose name merely starts with it is not exempted too.
     */
    private static final String HEALTH_SERVICE_METHOD_PREFIX = HealthGrpc.SERVICE_NAME + "/";

    private final String expectedCredentials;

    @Inject
    public BasicAuthServerInterceptor(final GrpcBasicAuthConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        this.expectedCredentials = configuration.username() + ":" + configuration.password();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        if (methodName.startsWith(HEALTH_SERVICE_METHOD_PREFIX) || isAuthenticated(headers)) {
            return next.startCall(call, headers);
        }

        log.debug("Rejecting unauthenticated gRPC call to {}", methodName);
        call.close(
            Status.UNAUTHENTICATED.withDescription(
                "Missing or invalid basic authentication credentials. Configure %s.username and %s.password on this worker with the values expected by the controller."
                    .formatted(GrpcBasicAuthConfiguration.PREFIX, GrpcBasicAuthConfiguration.PREFIX)
            ),
            new Metadata()
        );
        return new ServerCall.Listener<>() {
        };
    }

    private boolean isAuthenticated(final Metadata headers) {
        String authorization = headers.get(AUTHORIZATION_METADATA_KEY);
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            return false;
        }
        try {
            String presented = new String(
                Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length()).trim()),
                StandardCharsets.UTF_8
            );
            return AuthUtils.constantTimeEquals(expectedCredentials, presented);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
