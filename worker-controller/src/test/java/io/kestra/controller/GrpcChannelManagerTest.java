package io.kestra.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import io.kestra.controller.grpc.ConnectControllerServiceGrpc;
import io.kestra.controller.grpc.ExecutionLogsServiceGrpc;
import io.kestra.controller.grpc.KVMetadataServiceGrpc;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc;
import io.kestra.controller.grpc.NamespaceFileMetadataServiceGrpc;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc;
import io.kestra.controller.grpc.WorkerReportingServiceGrpc;
import io.kestra.core.contexts.KestraContext;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcChannelManagerTest {

    /** Every service a worker calls, so the retry allow-list can be checked against all of them. */
    private static final List<ServiceDescriptor> WORKER_FACING_SERVICES = List.of(
        WorkerControllerServiceGrpc.getServiceDescriptor(),
        ConnectControllerServiceGrpc.getServiceDescriptor(),
        LivenessControllerServiceGrpc.getServiceDescriptor(),
        WorkerFlowMetaStoreServiceGrpc.getServiceDescriptor(),
        KVMetadataServiceGrpc.getServiceDescriptor(),
        NamespaceFileMetadataServiceGrpc.getServiceDescriptor(),
        ExecutionLogsServiceGrpc.getServiceDescriptor(),
        WorkerReportingServiceGrpc.getServiceDescriptor()
    );

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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
        channelManager.init();

        // Then
        Channel channel = channelManager.getDefaultChannel();
        assertThat(channel).isNotNull();
    }

    @Test
    void shouldCreateChannelWithDnsConfiguration() {
        // Given
        WorkerControllersConfiguration config = createDnsConfig("controllers.example.com", 9096);
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);

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
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);

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
            new DnsConfig("", 9096, Duration.ofSeconds(30)),
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);

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
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);

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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);

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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
            Duration.ofMinutes(30), // keepAliveTime
            Duration.ofSeconds(15), // shutdownTimeout
            defaultRetryConfig()
        );
        WorkerControllersConfiguration config = createStaticConfig(
            List.of(new Endpoint("localhost", 9096))
        );

        // When
        channelManager = new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
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
    void shouldSupportManagersWithOverlappingLifetimesAndDifferentEndpoints() {
        // Given - two channel managers with static configs pointing at different controllers,
        // alive at the same time in the same JVM (as happens with successive test contexts).
        // The shared, stateless name resolver must serve both channels from their own target URI.
        GrpcChannelConfiguration channelConfig = createDefaultChannelConfig();
        GrpcChannelManager manager1 = new GrpcChannelManager(
            channelConfig, createDefaultGrpcConfig(),
            createStaticConfig(List.of(new Endpoint("localhost", 9096))), null
        );
        GrpcChannelManager manager2 = new GrpcChannelManager(
            channelConfig, createDefaultGrpcConfig(),
            createStaticConfig(List.of(new Endpoint("localhost", 9097))), null
        );

        // When - both initialize, then the first one closes
        manager1.init();
        manager2.init();
        manager1.close();

        // Then - the surviving manager's channel is unaffected by the other's lifecycle
        assertThat(manager2.getDefaultChannel()).isNotNull();
        assertThat(((ManagedChannel) manager2.getDefaultChannel()).isShutdown()).isFalse();

        // And a manager created after a close still works (the resolver stays registered JVM-wide)
        GrpcChannelManager manager3 = new GrpcChannelManager(
            channelConfig, createDefaultGrpcConfig(),
            createStaticConfig(List.of(new Endpoint("localhost", 9098))), null
        );
        manager3.init();
        assertThat(manager3.getDefaultChannel()).isNotNull();

        // Cleanup
        manager2.close();
        manager3.close();
    }

    @Test
    void shouldInstallHealthCheckAndRetryPolicyInOneServiceConfig() {
        // Given
        channelManager = newManager(createDefaultChannelConfig(), new HealthCheck(true));

        // When
        Map<String, Object> serviceConfig = channelManager.serviceConfig();

        // Then — a second defaultServiceConfig() call would replace the first, so both must share one map
        assertThat(serviceConfig).containsKey("healthCheckConfig").containsKey("methodConfig");
        assertThat(retryPolicy(serviceConfig))
            .containsEntry("maxAttempts", 4.0)
            .containsEntry("initialBackoff", "0.5s")
            .containsEntry("maxBackoff", "5s")
            .containsEntry("backoffMultiplier", 2.0)
            .containsEntry("retryableStatusCodes", List.of("UNAVAILABLE"));
    }

    @Test
    void shouldRetryOnlyReplaySafeRpcs() {
        // Given
        channelManager = newManager(createDefaultChannelConfig(), new HealthCheck(true));

        // When
        Set<String> covered = coveredMethods(channelManager.serviceConfig());

        // Then — every entry names a single RPC, so a service can never be allow-listed wholesale
        assertThat(nameEntries(channelManager.serviceConfig())).allSatisfy(name -> assertThat(name).containsKey("method"));
        assertThat(covered).contains(
            KVMetadataServiceGrpc.getFindMethod().getFullMethodName(),
            NamespaceFileMetadataServiceGrpc.getFindByPathMethod().getFullMethodName(),
            ExecutionLogsServiceGrpc.getErrorLogsMethod().getFullMethodName(),
            WorkerFlowMetaStoreServiceGrpc.getIsNamespaceExistsMethod().getFullMethodName()
        );
        assertThat(covered).doesNotContain(
            KVMetadataServiceGrpc.getSaveMethod().getFullMethodName(),
            KVMetadataServiceGrpc.getDeleteByNameMethod().getFullMethodName(),
            NamespaceFileMetadataServiceGrpc.getSaveMethod().getFullMethodName(),
            WorkerControllerServiceGrpc.getSendWorkerLogEntriesMethod().getFullMethodName(),
            WorkerControllerServiceGrpc.getStreamWorkerJobsMethod().getFullMethodName(),
            ConnectControllerServiceGrpc.getConnectMethod().getFullMethodName(),
            // The fixed-rate liveness schedule is the retry for these; see GrpcChannelManager
            LivenessControllerServiceGrpc.getHeartbeatMethod().getFullMethodName(),
            LivenessControllerServiceGrpc.getGetMaintenanceModeMethod().getFullMethodName()
        );
    }

    /**
     * Guard against the allow-list decaying: every worker-facing RPC must be either retryable or explicitly
     * recorded as unsafe to replay. A newly added RPC fails here until someone classifies it.
     */
    @Test
    void shouldClassifyEveryRpcAsRetryableOrKnownUnsafe() {
        // Given
        Set<String> knownUnsafe = Set.of(
            KVMetadataServiceGrpc.getSaveMethod().getFullMethodName(),
            KVMetadataServiceGrpc.getDeleteByNameMethod().getFullMethodName(),
            NamespaceFileMetadataServiceGrpc.getSaveMethod().getFullMethodName(),
            WorkerControllerServiceGrpc.getStreamWorkerJobsMethod().getFullMethodName(),
            WorkerControllerServiceGrpc.getSendWorkerTaskResultsMethod().getFullMethodName(),
            WorkerControllerServiceGrpc.getSendWorkerTriggerResultsMethod().getFullMethodName(),
            WorkerControllerServiceGrpc.getSendWorkerMetricEntriesMethod().getFullMethodName(),
            WorkerControllerServiceGrpc.getSendWorkerLogEntriesMethod().getFullMethodName(),
            WorkerReportingServiceGrpc.getSendReportMethod().getFullMethodName(),
            ConnectControllerServiceGrpc.getConnectMethod().getFullMethodName(),
            LivenessControllerServiceGrpc.getHeartbeatMethod().getFullMethodName(),
            LivenessControllerServiceGrpc.getGetMaintenanceModeMethod().getFullMethodName()
        );
        channelManager = newManager(createDefaultChannelConfig(), new HealthCheck(true));
        Set<String> covered = coveredMethods(channelManager.serviceConfig());

        // When
        List<String> allRpcs = WORKER_FACING_SERVICES.stream()
            .flatMap(service -> service.getMethods().stream())
            .map(MethodDescriptor::getFullMethodName)
            .toList();

        // Then
        assertThat(allRpcs).allSatisfy(rpc ->
            assertThat(covered.contains(rpc) || knownUnsafe.contains(rpc))
                .withFailMessage("RPC %s is neither retryable nor recorded as unsafe to replay. Classify it in GrpcChannelManager.", rpc)
                .isTrue()
        );
        assertThat(covered).doesNotContainAnyElementsOf(knownUnsafe);
        // No stale entries: everything listed as unsafe must still exist
        assertThat(allRpcs).containsAll(knownUnsafe);
    }

    @Test
    void shouldOmitRetryPolicyWhenRetryDisabled() {
        // Given
        GrpcChannelConfiguration channelConfig = new GrpcChannelConfiguration(
            Duration.ofHours(1), Duration.ofSeconds(30),
            new GrpcChannelConfiguration.Retry(false, 4, Duration.ofMillis(500), Duration.ofSeconds(5), 2.0)
        );

        // When
        channelManager = newManager(channelConfig, new HealthCheck(true));

        // Then
        assertThat(channelManager.serviceConfig()).containsKey("healthCheckConfig").doesNotContainKey("methodConfig");
    }

    @Test
    void shouldOmitRetryPolicyWhenMaxAttemptsBelowSpecMinimum() {
        // Given — a single attempt is a legitimate way to ask for no retry, and the gRPC spec rejects it
        GrpcChannelConfiguration channelConfig = new GrpcChannelConfiguration(
            Duration.ofHours(1), Duration.ofSeconds(30),
            new GrpcChannelConfiguration.Retry(true, 1, Duration.ofMillis(500), Duration.ofSeconds(5), 2.0)
        );

        // When
        channelManager = newManager(channelConfig, new HealthCheck(true));

        // Then — retries stay off and the channel still builds, instead of failing the server at startup
        assertThat(channelManager.serviceConfig()).containsKey("healthCheckConfig").doesNotContainKey("methodConfig");
        channelManager.init();
        assertThat(channelManager.getDefaultChannel()).isNotNull();
    }

    @Test
    void shouldStillInstallRetryPolicyWhenHealthCheckDisabled() {
        // Given
        channelManager = newManager(createDefaultChannelConfig(), new HealthCheck(false));

        // When
        Map<String, Object> serviceConfig = channelManager.serviceConfig();

        // Then
        assertThat(serviceConfig).doesNotContainKey("healthCheckConfig").containsKey("methodConfig");
    }

    @Test
    void shouldBuildChannelWithAValidServiceConfig() {
        // Given
        channelManager = newManager(createDefaultChannelConfig(), new HealthCheck(true));

        // When — gRPC validates the service config as the channel is built, so a bad entry throws here
        channelManager.init();

        // Then
        assertThat(channelManager.getDefaultChannel()).isNotNull();
    }

    // Helper methods

    private static GrpcChannelManager newManager(GrpcChannelConfiguration channelConfig, HealthCheck healthCheck) {
        WorkerControllersConfiguration config = new WorkerControllersConfiguration(
            DiscoveryType.STATIC,
            new StaticConfig(List.of(new Endpoint("localhost", 9096))),
            null,
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            healthCheck,
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        return new GrpcChannelManager(channelConfig, createDefaultGrpcConfig(), config, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> retryPolicy(Map<String, Object> serviceConfig) {
        List<Map<String, Object>> methodConfig = (List<Map<String, Object>>) serviceConfig.get("methodConfig");
        assertThat(methodConfig).hasSize(1);
        return (Map<String, Object>) methodConfig.getFirst().get("retryPolicy");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> nameEntries(Map<String, Object> serviceConfig) {
        List<Map<String, Object>> methodConfig = (List<Map<String, Object>>) serviceConfig.get("methodConfig");
        return (List<Map<String, String>>) methodConfig.getFirst().get("name");
    }

    /** The full method names the service config's {@code name} entries cover. */
    private static Set<String> coveredMethods(Map<String, Object> serviceConfig) {
        return nameEntries(serviceConfig).stream()
            .map(name -> name.get("service") + "/" + name.get("method"))
            .collect(Collectors.toSet());
    }

    private static WorkerControllersConfiguration createStaticConfig(List<Endpoint> endpoints) {
        return new WorkerControllersConfiguration(
            DiscoveryType.STATIC,
            new StaticConfig(endpoints),
            null,
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
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(healthCheckEnabled),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
    }

    private static WorkerControllersConfiguration createDnsConfig(String hostname, int defaultPort) {
        return new WorkerControllersConfiguration(
            DiscoveryType.DNS,
            null,
            new DnsConfig(hostname, defaultPort, Duration.ofSeconds(30)),
            null,
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(true),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
    }

    private static GrpcChannelConfiguration createDefaultChannelConfig() {
        return new GrpcChannelConfiguration(
            Duration.ofHours(1), // keepAliveTime
            Duration.ofSeconds(30), // shutdownTimeout
            defaultRetryConfig()
        );
    }

    private static GrpcChannelConfiguration.Retry defaultRetryConfig() {
        return new GrpcChannelConfiguration.Retry(true, 4, Duration.ofMillis(500), Duration.ofSeconds(5), 2.0);
    }

    private static GrpcConfiguration createDefaultGrpcConfig() {
        return new GrpcConfiguration(false, Integer.MAX_VALUE);
    }
}
