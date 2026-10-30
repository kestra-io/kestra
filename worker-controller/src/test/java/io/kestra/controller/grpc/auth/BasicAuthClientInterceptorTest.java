package io.kestra.controller.grpc.auth;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.controller.config.GrpcBasicAuthConfiguration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

class BasicAuthClientInterceptorTest {

    @Test
    void shouldAttachBasicAuthorizationHeaderToOutgoingCalls() {
        AtomicReference<Metadata> sentHeaders = new AtomicReference<>();
        GrpcBasicAuthConfiguration configuration = new GrpcBasicAuthConfiguration(true, "worker", "Passw0rd");

        new BasicAuthClientInterceptor(configuration)
            .interceptCall(methodDescriptor(), CallOptions.DEFAULT, channelCapturing(sentHeaders))
            .start(new ClientCall.Listener<>() {
            }, new Metadata());

        assertThat(sentHeaders.get().get(BasicAuthServerInterceptor.AUTHORIZATION_METADATA_KEY))
            .isEqualTo("Basic " + Base64.getEncoder().encodeToString("worker:Passw0rd".getBytes(StandardCharsets.UTF_8)));
    }

    private static Channel channelCapturing(AtomicReference<Metadata> sentHeaders) {
        return new Channel() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                return new ClientCall<>() {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        sentHeaders.set(headers);
                    }

                    @Override
                    public void request(int numMessages) {
                    }

                    @Override
                    public void cancel(String message, Throwable cause) {
                    }

                    @Override
                    public void halfClose() {
                    }

                    @Override
                    public void sendMessage(ReqT message) {
                    }
                };
            }

            @Override
            public String authority() {
                return "test";
            }
        };
    }

    private static MethodDescriptor<String, String> methodDescriptor() {
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
            .setFullMethodName("io.kestra.controller.grpc.WorkerControllerService/streamWorkerJobs")
            .setRequestMarshaller(marshaller)
            .setResponseMarshaller(marshaller)
            .build();
    }
}
