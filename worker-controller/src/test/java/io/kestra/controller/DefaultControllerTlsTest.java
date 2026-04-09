package io.kestra.controller;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.kestra.controller.config.ControllerConfiguration;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.config.GrpcTlsConfiguration;
import io.kestra.controller.config.GrpcTlsConfiguration.ClientAuth;
import io.kestra.controller.config.GrpcTlsConfiguration.KeyStoreConfig;
import io.kestra.controller.config.GrpcTlsConfiguration.TrustStoreConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.micronaut.context.event.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.net.ssl.TrustManagerFactory;
import io.grpc.TlsChannelCredentials;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultControllerTlsTest {

    private static final String TEST_KEYSTORE = testResource("tls/server-keystore.p12");
    private static final String TEST_TRUSTSTORE = testResource("tls/server-truststore.p12");
    private static final String KEYSTORE_PASSWORD = "testpass";
    private static final String TRUSTSTORE_PASSWORD = "trustpass";

    private DefaultController controller;

    private static String testResource(String name) {
        return DefaultControllerTlsTest.class.getClassLoader().getResource(name).getPath();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void shouldFailWhenTlsEnabledWithoutKeyStore() {
        // Given
        var tlsConfig = new GrpcTlsConfiguration(true, null, null, ClientAuth.NONE, false, null);
        var controller = createController(tlsConfig);

        // When/Then
        assertThatThrownBy(controller::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("key-store is required");
    }

    @ParameterizedTest
    @EnumSource(value = ClientAuth.class, names = {"OPTIONAL", "REQUIRE"})
    void shouldFailWhenClientAuthWithoutTrustStore(ClientAuth clientAuth) {
        // Given
        var keyStore = new KeyStoreConfig(TEST_KEYSTORE, "PKCS12", KEYSTORE_PASSWORD, null);
        var tlsConfig = new GrpcTlsConfiguration(true, keyStore, null, clientAuth, false, null);
        var controller = createController(tlsConfig);

        // When/Then
        assertThatThrownBy(controller::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trust-store is required")
            .hasMessageContaining(clientAuth.name());
    }

    @Test
    void shouldStartAndServeOverTls() throws Exception {
        // Given
        var keyStore = new KeyStoreConfig(TEST_KEYSTORE, "PKCS12", KEYSTORE_PASSWORD, null);
        var tlsConfig = new GrpcTlsConfiguration(true, keyStore, null, ClientAuth.NONE, false, null);
        controller = createController(tlsConfig, 0); // port 0 = random available port

        // When
        controller.start();

        // Then - connect with TLS and perform a health check RPC
        int port = controller.getServer().getPort();
        TrustManagerFactory tmf = GrpcTlsConfiguration.loadTrustManagerFactory(
            new TrustStoreConfig(TEST_TRUSTSTORE, "PKCS12", TRUSTSTORE_PASSWORD)
        );
        ChannelCredentials credentials = TlsChannelCredentials.newBuilder()
            .trustManager(tmf.getTrustManagers())
            .build();

        ManagedChannel channel = Grpc.newChannelBuilder("localhost:" + port, credentials).build();
        try {
            HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
                .check(HealthCheckRequest.newBuilder().setService("").build());
            assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldStartAndServeOverTlsWithMutualAuth() throws Exception {
        // Given - server requires client cert; use the same keystore as client cert for simplicity
        var keyStore = new KeyStoreConfig(TEST_KEYSTORE, "PKCS12", KEYSTORE_PASSWORD, null);
        var trustStore = new TrustStoreConfig(TEST_TRUSTSTORE, "PKCS12", TRUSTSTORE_PASSWORD);
        var tlsConfig = new GrpcTlsConfiguration(true, keyStore, trustStore, ClientAuth.REQUIRE, false, null);
        controller = createController(tlsConfig, 0);

        // When
        controller.start();

        // Then - connect with mTLS (client provides its certificate)
        int port = controller.getServer().getPort();
        TrustManagerFactory tmf = GrpcTlsConfiguration.loadTrustManagerFactory(trustStore);
        var kmf = GrpcTlsConfiguration.loadKeyManagerFactory(keyStore);
        ChannelCredentials credentials = TlsChannelCredentials.newBuilder()
            .trustManager(tmf.getTrustManagers())
            .keyManager(kmf.getKeyManagers())
            .build();

        ManagedChannel channel = Grpc.newChannelBuilder("localhost:" + port, credentials).build();
        try {
            HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
                .check(HealthCheckRequest.newBuilder().setService("").build());
            assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldConnectWithAuthorityOverride() throws Exception {
        // Given - server has SAN=dns:kestra-controller, client connects to localhost
        // but overrides authority to "kestra-controller" for TLS verification
        var keyStore = new KeyStoreConfig(TEST_KEYSTORE, "PKCS12", KEYSTORE_PASSWORD, null);
        var tlsConfig = new GrpcTlsConfiguration(true, keyStore, null, ClientAuth.NONE, false, null);
        controller = createController(tlsConfig, 0);
        controller.start();

        // When - connect with authority override
        int port = controller.getServer().getPort();
        TrustManagerFactory tmf = GrpcTlsConfiguration.loadTrustManagerFactory(
            new TrustStoreConfig(TEST_TRUSTSTORE, "PKCS12", TRUSTSTORE_PASSWORD)
        );
        ChannelCredentials credentials = TlsChannelCredentials.newBuilder()
            .trustManager(tmf.getTrustManagers())
            .build();

        ManagedChannel channel = Grpc.newChannelBuilder("localhost:" + port, credentials)
            .overrideAuthority("kestra-controller")
            .build();
        try {
            // Then - handshake succeeds because "kestra-controller" is in the cert's SANs
            HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
                .check(HealthCheckRequest.newBuilder().setService("").build());
            assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("unchecked")
    private DefaultController createController(GrpcTlsConfiguration tlsConfig) {
        return createController(tlsConfig, 9096);
    }

    @SuppressWarnings("unchecked")
    private DefaultController createController(GrpcTlsConfiguration tlsConfig, int port) {
        var grpcConfig = new GrpcConfiguration(false, Integer.MAX_VALUE);
        var controllerConfig = new ControllerConfiguration(
            port,
            Duration.ofMinutes(5),
            Duration.ofSeconds(10)
        );
        var eventPublisher = (ApplicationEventPublisher<ServiceStateChangeEvent>) event -> {};
        return new DefaultController(List.of(), grpcConfig, tlsConfig, controllerConfig, eventPublisher);
    }
}
