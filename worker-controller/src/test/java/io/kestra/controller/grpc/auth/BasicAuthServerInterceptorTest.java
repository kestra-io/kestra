package io.kestra.controller.grpc.auth;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.controller.config.GrpcBasicAuthConfiguration;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BasicAuthServerInterceptorTest {

    private static final String WORKER_METHOD = "io.kestra.controller.grpc.WorkerControllerService/streamWorkerJobs";
    private static final String HEALTH_METHOD = "grpc.health.v1.Health/Check";

    private static final GrpcBasicAuthConfiguration CONFIGURATION = new GrpcBasicAuthConfiguration(true, "worker", "Passw0rd");

    @Test
    void shouldRejectCallWhenAuthorizationHeaderIsMissing() {
        Status status = interceptAndCaptureClose(WORKER_METHOD, new Metadata());

        assertThat(status.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void shouldRejectCallWhenCredentialsDoNotMatch() {
        Status status = interceptAndCaptureClose(WORKER_METHOD, headers(basic("worker", "wrong")));

        assertThat(status.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void shouldRejectCallWhenSchemeIsNotBasic() {
        Status status = interceptAndCaptureClose(WORKER_METHOD, headers("Bearer " + basic("worker", "Passw0rd")));

        assertThat(status.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void shouldAcceptCallWhenCredentialsMatch() {
        assertThat(intercept(WORKER_METHOD, headers(basic("worker", "Passw0rd")))).isTrue();
    }

    @Test
    void shouldAcceptHealthCheckWithoutCredentials() {
        assertThat(intercept(HEALTH_METHOD, new Metadata())).isTrue();
    }

    /** Runs the interceptor and returns whether the call reached the handler. */
    private static boolean intercept(String fullMethodName, Metadata headers) {
        AtomicBoolean handlerCalled = new AtomicBoolean();
        ServerCallHandler<String, String> handler = (call, metadata) ->
        {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {
            };
        };

        new BasicAuthServerInterceptor(CONFIGURATION).interceptCall(serverCall(fullMethodName), headers, handler);

        return handlerCalled.get();
    }

    private static Status interceptAndCaptureClose(String fullMethodName, Metadata headers) {
        ServerCall<String, String> call = serverCall(fullMethodName);
        ServerCallHandler<String, String> handler = (ignoredCall, ignoredHeaders) -> new ServerCall.Listener<>() {
        };

        new BasicAuthServerInterceptor(CONFIGURATION).interceptCall(call, headers, handler);

        ArgumentCaptor<Status> captor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(captor.capture(), org.mockito.ArgumentMatchers.any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<String, String> serverCall(String fullMethodName) {
        ServerCall<String, String> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor(fullMethodName));
        return call;
    }

    private static MethodDescriptor<String, String> methodDescriptor(String fullMethodName) {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                return "";
            }
        };
        return MethodDescriptor.<String, String> newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethodName)
            .setRequestMarshaller(marshaller)
            .setResponseMarshaller(marshaller)
            .build();
    }

    private static Metadata headers(String authorization) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), authorization);
        return metadata;
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
