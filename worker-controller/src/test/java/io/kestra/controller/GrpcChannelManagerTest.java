package io.kestra.controller;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.controller.config.GrpcChannelConfiguration;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration.DiscoveryType;
import io.kestra.controller.config.WorkerControllersConfiguration.DnsConfig;
import io.kestra.controller.config.WorkerControllersConfiguration.Endpoint;
import io.kestra.controller.config.WorkerControllersConfiguration.HealthCheck;
import io.kestra.controller.config.WorkerControllersConfiguration.LoadBalancing;
import io.kestra.controller.config.WorkerControllersConfiguration.StaticConfig;
import io.kestra.core.contexts.KestraContext;

import io.grpc.Channel;
import io.grpc.ManagedChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcChannelManagerTest {

    private GrpcChannelManager channelManager;

    @BeforeEach
    void setUp() {
        // Set up a test KestraContext for getUserAgent()
        KestraContext testContext = Mockito.mock(KestraContext.class);
        Mockito.when(testContext.getVersion()).thenReturn("1.0.0-test");
        KestraContext.setContext(testContext);
    }

    @AfterEach
    void tearDown() {
        if (channelManager != null) {
            channelManager.close();
            channelManager = null;
        }
        KestraContext.setContext(null);
    }

    @Test
    void shouldCreateChannelWithStaticConfiguration() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then
        Channel channel = channelManager.getDefaultChannel();
        assertThat(channel).isNotNull();
        assertThat(channel).isInstanceOf(ManagedChannel.class);
    }

    @Test
    void shouldCreateChannelWithMultipleStaticEndpoints() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(
                new Endpoint("controller-1.example.com", 9096),
                new Endpoint("controller-2.example.com", 9097),
                new Endpoint("controller-3.example.com", 9098)
            )
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then
        Channel channel = channelManager.getDefaultChannel();
        assertThat(channel).isNotNull();
    }

    @Test
    void shouldCreateChannelWithDnsSrvConfiguration() {
        // Given
        WorkerControllersConfiguration config = createDnsSrvConfig("kestra-controller.default.svc.cluster.local");
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then
        Channel channel = channelManager.getDefaultChannel();
        assertThat(channel).isNotNull();
    }

    @Test
    void shouldCreateChannelWithDnsARecordConfiguration() {
        // Given
        WorkerControllersConfiguration config = createDnsARecordConfig("controllers.example.com", 9096);
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then
        Channel channel = channelManager.getDefaultChannel();
        assertThat(channel).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenStaticConfigHasNoEndpoints() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(List.of());
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);

        // When/Then
        assertThatThrownBy(() -> channelManager.init())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Static configuration requires at least one endpoint");
    }

    @Test
    void shouldThrowExceptionWhenStaticConfigIsNull() {
        // Given - static type but null config
        WorkerControllersConfiguration config = new WorkerControllersConfiguration(
            DiscoveryType.STATIC,
            null,
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);

        // When/Then
        assertThatThrownBy(() -> channelManager.init())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Static configuration requires at least one endpoint");
    }

    @Test
    void shouldThrowExceptionWhenDnsHostnameIsBlank() {
        // Given
        WorkerControllersConfiguration config = new WorkerControllersConfiguration(
            DiscoveryType.DNS,
            null,
            new DnsConfig("", 9096, DnsConfig.DnsRecordType.SRV, Duration.ofSeconds(30)),
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);

        // When/Then
        assertThatThrownBy(() -> channelManager.init())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DNS configuration requires a hostname");
    }

    @Test
    void shouldThrowExceptionWhenDnsConfigIsNull() {
        // Given
        WorkerControllersConfiguration config = new WorkerControllersConfiguration(
            DiscoveryType.DNS,
            null,
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);

        // When/Then
        assertThatThrownBy(() -> channelManager.init())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DNS configuration requires a hostname");
    }

    @Test
    void shouldReturnSameChannelOnMultipleGetCalls() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // When
        Channel channel1 = channelManager.getDefaultChannel();
        Channel channel2 = channelManager.getDefaultChannel();

        // Then
        assertThat(channel1).isSameAs(channel2);
    }

    @Test
    void shouldInitializeChannelOnlyOnce() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);

        // When - call init multiple times
        channelManager.init();
        Channel channel1 = channelManager.getDefaultChannel();
        channelManager.init();
        Channel channel2 = channelManager.getDefaultChannel();

        // Then - should return the same channel
        assertThat(channel1).isSameAs(channel2);
    }

    @Test
    void shouldShutdownChannelOnClose() throws InterruptedException {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();
        ManagedChannel channel = (ManagedChannel) channelManager.getDefaultChannel();

        // When
        channelManager.close();

        // Then
        assertThat(channel.isShutdown()).isTrue();
        // Wait for termination
        boolean terminated = channel.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(terminated).isTrue();
    }

    @Test
    void shouldHandleMultipleCloseCalls() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // When - call close multiple times
        channelManager.close();
        channelManager.close();

        // Then - should not throw
        ManagedChannel channel = (ManagedChannel) channelManager.getDefaultChannel();
        assertThat(channel.isShutdown()).isTrue();
    }

    @Test
    void shouldCloseGracefullyWithoutInitialization() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        // Note: init() is NOT called

        // When/Then - should not throw
        channelManager.close();
    }

    @Test
    void shouldCreateNewManagedChannel() {
        // Given
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // When
        ManagedChannel newChannel = channelManager.createNewManagedChannel();

        // Then
        assertThat(newChannel).isNotNull();
        assertThat(newChannel).isNotSameAs(channelManager.getDefaultChannel());

        // Cleanup the new channel
        newChannel.shutdownNow();
    }

    @Test
    void shouldUseRoundRobinLoadBalancing() {
        // Given
        WorkerControllersConfiguration config = createStaticConfigWithLoadBalancing(
            List.of(new Endpoint("localhost", 9096)),
            LoadBalancing.Policy.ROUND_ROBIN
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then - channel is created successfully (load balancing is internal to gRPC)
        assertThat(channelManager.getDefaultChannel()).isNotNull();
    }

    @Test
    void shouldUsePickFirstLoadBalancing() {
        // Given
        WorkerControllersConfiguration config = createStaticConfigWithLoadBalancing(
            List.of(new Endpoint("localhost", 9096)),
            LoadBalancing.Policy.PICK_FIRST
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then - channel is created successfully
        assertThat(channelManager.getDefaultChannel()).isNotNull();
    }

    @Test
    void shouldConfigureHealthCheckWhenEnabled() {
        // Given
        WorkerControllersConfiguration config = createStaticConfigWithHealthCheck(
            List.of(new Endpoint("localhost", 9096)),
            true
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then - channel is created with health check config
        assertThat(channelManager.getDefaultChannel()).isNotNull();
    }

    @Test
    void shouldNotConfigureHealthCheckWhenDisabled() {
        // Given
        WorkerControllersConfiguration config = createStaticConfigWithHealthCheck(
            List.of(new Endpoint("localhost", 9096)),
            false
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then - channel is created without health check
        assertThat(channelManager.getDefaultChannel()).isNotNull();
    }

    @Test
    void shouldUseDefaultPortWhenNotSpecified() {
        // Given - endpoint without explicit port
        Endpoint endpoint = new Endpoint("localhost", null);

        // Then - should use default port 9096
        assertThat(endpoint.port()).isEqualTo(50051);
    }

    @Test
    void shouldApplyChannelConfiguration() {
        // Given
        GrpcChannelConfiguration channelConfig = new GrpcChannelConfiguration(
            5, // maxRetryAttempts
            Duration.ofMinutes(30), // keepAliveTime
            Duration.ofSeconds(15) // shutdownTimeout
        );
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        channelManager.init();

        // Then - channel is created (configuration is applied internally)
        assertThat(channelManager.getDefaultChannel()).isNotNull();
    }

    @Test
    void shouldGenerateUserAgent() {
        // Given - test context is already set in setUp()

        // When
        String userAgent = GrpcChannelManager.getUserAgent();

        // Then
        assertThat(userAgent).isEqualTo("Kestra/1.0.0-test");
    }

    @Test
    void shouldUnregisterResolverOnClose() {
        // Given - create two channel managers with static config
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // First manager registers the resolver
        GrpcChannelManager manager1 = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        manager1.init();

        // Close first manager - should unregister resolver
        manager1.close();

        // Second manager should be able to register a new resolver
        GrpcChannelManager manager2 = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config);
        manager2.init();

        // Then - both should work without conflict
        assertThat(manager2.getDefaultChannel()).isNotNull();

        // Cleanup
        manager2.close();
    }

    // Helper methods

    private static WorkerControllersConfiguration createStaticConfig(List<Endpoint> endpoints) {
        return new WorkerControllersConfiguration(
            DiscoveryType.STATIC,
            new StaticConfig(endpoints),
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
    }

    private static WorkerControllersConfiguration createStaticConfigWithLoadBalancing(
        List<Endpoint> endpoints,
        LoadBalancing.Policy policy) {
        return new WorkerControllersConfiguration(
            DiscoveryType.STATIC,
            new StaticConfig(endpoints),
            null,
            new LoadBalancing(policy),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
    }

    private static WorkerControllersConfiguration createStaticConfigWithHealthCheck(
        List<Endpoint> endpoints,
        boolean healthCheckEnabled) {
        return new WorkerControllersConfiguration(
            DiscoveryType.STATIC,
            new StaticConfig(endpoints),
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(healthCheckEnabled),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
    }

    private static WorkerControllersConfiguration createDnsSrvConfig(String hostname) {
        return new WorkerControllersConfiguration(
            DiscoveryType.DNS,
            null,
            new DnsConfig(hostname, 9096, DnsConfig.DnsRecordType.SRV, Duration.ofSeconds(30)),
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
    }

    private static WorkerControllersConfiguration createDnsARecordConfig(String hostname, int defaultPort) {
        return new WorkerControllersConfiguration(
            DiscoveryType.DNS,
            null,
            new DnsConfig(hostname, defaultPort, DnsConfig.DnsRecordType.A, Duration.ofSeconds(30)),
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
    }

    private static GrpcChannelConfiguration createDefaultChannelConfig() {
        return new GrpcChannelConfiguration(
            10, // maxRetryAttempts
            Duration.ofHours(1), // keepAliveTime
            Duration.ofSeconds(30) // shutdownTimeout
        );
    }

    private static GrpcConfiguration createDefaultGrpcConfig() {
        return new GrpcConfiguration(false, Integer.MAX_VALUE);
    }
}
