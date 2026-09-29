package io.kestra.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.controller.config.ControllerConfiguration;
import io.kestra.controller.config.GrpcBasicAuthConfiguration;
import io.kestra.controller.config.GrpcChannelConfiguration;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration.DiscoveryType;
import io.kestra.controller.config.WorkerControllersConfiguration.Endpoint;
import io.kestra.controller.config.WorkerControllersConfiguration.HealthCheck;
import io.kestra.controller.config.WorkerControllersConfiguration.LoadBalancing;
import io.kestra.controller.config.WorkerControllersConfiguration.StaticConfig;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.grpc.auth.BasicAuthClientInterceptor;
import io.kestra.controller.grpc.auth.BasicAuthServerInterceptor;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.metrics.MetricRegistry;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.micronaut.context.event.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks that the credentials are actually required on the wire, which is what a missing
 * {@code intercept(...)} call on either the server or the channel would silently break.
 */
class GrpcBasicAuthWireTest {

    private static final GrpcBasicAuthConfiguration ENABLED = new GrpcBasicAuthConfiguration(true, "worker", "Passw0rd");

    private Server server;
    private ManagedChannel plainChannel;
    private GrpcChannelManager channelManager;

    @BeforeEach
    void setUp() {
        KestraContext context = Mockito.mock(KestraContext.class);
        Mockito.when(context.getVersion()).thenReturn("test");
        KestraContext.setContext(context);
    }

    @AfterEach
    void tearDown() {
        if (channelManager != null) {
            channelManager.close();
        }
        if (plainChannel != null) {
            plainChannel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
        KestraContext.setContext(null);
    }

    @Test
    void shouldRejectCallFromChannelWithoutCredentials() throws IOException {
        startServer();
        plainChannel = Grpc.newChannelBuilderForAddress("localhost", server.getPort(), InsecureChannelCredentials.create()).build();

        assertThatThrownBy(() -> echo(plainChannel))
            .isInstanceOf(StatusRuntimeException.class)
            .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
            .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void shouldAcceptCallFromChannelBuiltWithCredentials() throws IOException {
        startServer();
        channelManager = new GrpcChannelManager(
            channelConfiguration(),
            grpcConfiguration(),
            staticControllersConfiguration(server.getPort()),
            null,
            new BasicAuthClientInterceptor(ENABLED)
        );
        channelManager.init();

        assertThat(echo(channelManager.getDefaultChannel())).isEqualTo("pong");
    }

    private void startServer() throws IOException {
        server = controller(new BasicAuthServerInterceptor(ENABLED)).buildServer(0).build().start();
    }

    private static DefaultController controller(BasicAuthServerInterceptor interceptor) {
        return new DefaultController(
            List.of(echoService()),
            grpcConfiguration(),
            new ControllerConfiguration(0, Duration.ZERO, Duration.ZERO),
            Mockito.mock(MetricRegistry.class),
            Mockito.mock(ApplicationEventPublisher.class),
            interceptor
        );
    }

    private static String echo(Channel channel) {
        return ClientCalls.blockingUnaryCall(channel, ECHO_METHOD, CallOptions.DEFAULT, "ping");
    }

    private static WorkerControllerService echoService() {
        return () -> ServerServiceDefinition.builder("kestra.test.EchoService")
            .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall((request, observer) ->
            {
                observer.onNext("pong");
                observer.onCompleted();
            }))
            .build();
    }

    private static GrpcConfiguration grpcConfiguration() {
        return new GrpcConfiguration(false, 10485760);
    }

    private static GrpcChannelConfiguration channelConfiguration() {
        return new GrpcChannelConfiguration(
            Duration.ofHours(1),
            Duration.ofSeconds(5),
            new GrpcChannelConfiguration.Retry(false, 4, Duration.ofMillis(500), Duration.ofSeconds(5), 2.0)
        );
    }

    private static WorkerControllersConfiguration staticControllersConfiguration(int port) {
        return new WorkerControllersConfiguration(
            DiscoveryType.STATIC,
            new StaticConfig(List.of(new Endpoint("localhost", port))),
            null,
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(false),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(5))
        );
    }

    private static final MethodDescriptor<String, String> ECHO_METHOD = MethodDescriptor.<String, String> newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("kestra.test.EchoService/echo")
        .setRequestMarshaller(stringMarshaller())
        .setResponseMarshaller(stringMarshaller())
        .build();

    private static MethodDescriptor.Marshaller<String> stringMarshaller() {
        return new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}
