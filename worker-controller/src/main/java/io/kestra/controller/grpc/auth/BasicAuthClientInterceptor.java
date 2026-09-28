package io.kestra.controller.grpc.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import io.kestra.controller.config.GrpcBasicAuthConfiguration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Presents the configured HTTP Basic credentials on every call this worker makes to the controller.
 *
 * @see GrpcBasicAuthConfiguration
 */
@Singleton
@Requires(property = GrpcBasicAuthConfiguration.ENABLED_PROPERTY, value = "true")
public class BasicAuthClientInterceptor implements ClientInterceptor {

    private final String authorizationHeader;

    @Inject
    public BasicAuthClientInterceptor(final GrpcBasicAuthConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        this.authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(
            (configuration.username() + ":" + configuration.password()).getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        final MethodDescriptor<ReqT, RespT> method,
        final CallOptions callOptions,
        final Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(BasicAuthServerInterceptor.AUTHORIZATION_METADATA_KEY, authorizationHeader);
                super.start(responseListener, headers);
            }
        };
    }
}
