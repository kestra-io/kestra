package io.kestra.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.controller.config.GrpcChannelConfiguration;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration.DiscoveryType;
import io.kestra.controller.config.WorkerControllersConfiguration.HealthCheck;
import io.kestra.controller.config.WorkerControllersConfiguration.LoadBalancing;
import io.kestra.controller.config.WorkerControllersConfiguration.StorageConfig;
import io.kestra.controller.discovery.ControllerRegistration;
import io.kestra.controller.discovery.ControllerRegistry;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.storage.local.LocalStorage;

import io.grpc.EquivalentAddressGroup;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcChannelManagerStorageTest {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @TempDir
    Path tempDir;

    private StorageInterface storage;
    private GrpcChannelManager channelManager;

    @BeforeEach
    void setUp() throws Exception {
        // Given — a local internal storage rooted in a JUnit-managed temp directory
        LocalStorage local = new LocalStorage();
        local.setBasePath(tempDir);
        local.init();
        storage = local;

        KestraContext ctx = Mockito.mock(KestraContext.class);
        Mockito.when(ctx.getVersion()).thenReturn("test");
        KestraContext.setContext(ctx);
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
    void shouldDiscoverRegisteredControllersFromStorage() throws Exception {
        // Given — two live controllers registered in storage
        writeRegistration("c1", "controller-1", 6001, Duration.ofMinutes(5));
        writeRegistration("c2", "controller-2", 6002, Duration.ofMinutes(5));

        channelManager = newManager();

        // When
        List<EquivalentAddressGroup> discovered = channelManager.discoverControllersFromStorage();

        // Then
        assertThat(discovered).hasSize(2);
        assertThat(discovered).extracting(g -> (InetSocketAddress) g.getAddresses().get(0))
            .extracting(InetSocketAddress::getHostString)
            .containsExactlyInAnyOrder("controller-1", "controller-2");
    }

    @Test
    void shouldFilterOutExpiredRegistrations() throws Exception {
        // Given — one live + one expired entry
        writeRegistration("live", "controller-live", 7001, Duration.ofMinutes(5));
        writeRegistration("stale", "controller-stale", 7002, Duration.ofMinutes(-1));

        channelManager = newManager();

        // When
        List<EquivalentAddressGroup> discovered = channelManager.discoverControllersFromStorage();

        // Then
        assertThat(discovered).hasSize(1);
        InetSocketAddress addr = (InetSocketAddress) discovered.get(0).getAddresses().get(0);
        assertThat(addr.getHostString()).isEqualTo("controller-live");
    }

    @Test
    void shouldFallBackToLastKnownGoodWhenPerEntryReadThrows() throws Exception {
        // Given — two live controllers registered, seeded into last-known-good via a first successful discovery
        writeRegistration("c1", "controller-1", 6001, Duration.ofMinutes(5));
        writeRegistration("c2", "controller-2", 6002, Duration.ofMinutes(5));

        StorageInterface spy = Mockito.spy(storage);
        storage = spy;
        channelManager = newManager();
        List<EquivalentAddressGroup> firstLoad = channelManager.discoverControllersFromStorage();
        assertThat(firstLoad).hasSize(2);

        // When — a subsequent per-entry read throws a transient IOException for c2
        URI c2Uri = ControllerRegistry.entryUri("c2");
        Mockito.doThrow(new IOException("transient")).when(spy).getInstanceResource(null, c2Uri);
        List<EquivalentAddressGroup> secondLoad = channelManager.discoverControllersFromStorage();

        // Then — the method returns the last known good list, not a shrunken pool
        assertThat(secondLoad).hasSize(2);
        assertThat(secondLoad).extracting(g -> (InetSocketAddress) g.getAddresses().get(0))
            .extracting(InetSocketAddress::getHostString)
            .containsExactlyInAnyOrder("controller-1", "controller-2");
    }

    @Test
    void shouldDropMalformedEntryButIncludeValidOnes() throws Exception {
        // Given — one valid controller and one malformed JSON file under the registry prefix
        writeRegistration("valid", "controller-valid", 7001, Duration.ofMinutes(5));
        storage.putInstanceResource(
            null,
            ControllerRegistry.entryUri("bad"),
            new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8))
        );

        channelManager = newManager();

        // When
        List<EquivalentAddressGroup> discovered = channelManager.discoverControllersFromStorage();

        // Then — the malformed entry is dropped, the valid one is kept
        assertThat(discovered).hasSize(1);
        InetSocketAddress addr = (InetSocketAddress) discovered.get(0).getAddresses().get(0);
        assertThat(addr.getHostString()).isEqualTo("controller-valid");
    }

    @Test
    void shouldReturnEmptyListWhenRegistryPrefixDoesNotExist() {
        // Given — no registration written
        channelManager = newManager();

        // When
        List<EquivalentAddressGroup> discovered = channelManager.discoverControllersFromStorage();

        // Then
        assertThat(discovered).isEmpty();
    }

    private void writeRegistration(String id, String host, int port, Duration remainingTtl) throws Exception {
        Instant now = Instant.now();
        ControllerRegistration registration = new ControllerRegistration(
            id,
            host,
            port,
            now,
            now.plus(remainingTtl)
        );
        byte[] payload = MAPPER.writeValueAsBytes(registration);
        storage.putInstanceResource(null, ControllerRegistry.entryUri(id), new ByteArrayInputStream(payload));
    }

    private GrpcChannelManager newManager() {
        GrpcChannelConfiguration channelConfig = new GrpcChannelConfiguration(
            1, Duration.ofMinutes(1), Duration.ofSeconds(3)
        );
        GrpcConfiguration grpcConfig = new GrpcConfiguration(false, Integer.MAX_VALUE);
        WorkerControllersConfiguration config = new WorkerControllersConfiguration(
            DiscoveryType.STORAGE,
            null,
            null,
            new StorageConfig(Duration.ofMinutes(1)),
            new LoadBalancing(LoadBalancing.Policy.ROUND_ROBIN),
            new HealthCheck(false),
            new WorkerControllersConfiguration.WaitForReady(true, Duration.ofSeconds(1))
        );
        GrpcChannelManager manager = new GrpcChannelManager(channelConfig, grpcConfig, config, () -> storage);
        // Intentionally skip init() — we only exercise discoverControllersFromStorage() here.
        return manager;
    }
}
