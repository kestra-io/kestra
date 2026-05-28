package io.kestra.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.grpc.ChannelCredentials;
import io.grpc.Channel;
import io.grpc.EquivalentAddressGroup;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.kestra.controller.config.GrpcChannelConfiguration;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration;
import io.kestra.controller.discovery.ControllerRegistration;
import io.kestra.controller.discovery.ControllerRegistry;
import io.kestra.controller.grpc.resolver.StaticNameResolverProvider;
import io.kestra.controller.grpc.resolver.StorageNameResolverProvider;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.ExecutorsUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Manages gRPC channels for worker-to-controller communication.
 * <p>
 * Supports three service discovery strategies:
 * <ul>
 * <li>STATIC: Explicit list of controller endpoints with gRPC load-balancing</li>
 * <li>DNS: DNS SRV/A record resolution with gRPC load-balancing</li>
 * <li>STORAGE: Dynamic discovery via Kestra internal storage (controllers self-register)</li>
 * </ul>
 * <p>
 */
@Singleton
@Slf4j
public class GrpcChannelManager {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private static final AtomicBoolean STATIC_RESOLVER_REGISTERED = new AtomicBoolean(false);
    // Reference to the registered resolver provider for cleanup
    private static final AtomicReference<StaticNameResolverProvider> REGISTERED_STATIC_RESOLVER_PROVIDER = new AtomicReference<>();

    private static final AtomicBoolean STORAGE_RESOLVER_REGISTERED = new AtomicBoolean(false);
    private static final AtomicReference<StorageNameResolverProvider> REGISTERED_STORAGE_RESOLVER_PROVIDER = new AtomicReference<>();

    private volatile ManagedChannel defaultChannel;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final GrpcChannelConfiguration grpcChannelConfiguration;
    private final GrpcConfiguration grpcConfiguration;
    private final WorkerControllersConfiguration controllersConfig;
    private final Provider<StorageInterface> storageInterface;

    // Last known good list of storage-discovered endpoints — used to ride out transient storage failures.
    private volatile List<EquivalentAddressGroup> lastKnownStorageAddresses = List.of();

    // One-shot guard for the "first endpoints loaded from storage" log line.
    private final AtomicBoolean firstStorageLoadLogged = new AtomicBoolean(false);

    // ExecutorService shared across channels
    private final ExecutorService sharedExecutorService;

    /**
     * Creates a new {@link GrpcChannelManager} instance.
     *
     * @param grpcChannelConfiguration the gRPC channel configuration.
     * @param grpcConfiguration        the global gRPC configuration.
     * @param controllersConfig        the multi-endpoint controllers configuration.
     * @param storageInterface         the internal storage used for STORAGE discovery. May be {@code null}
     *                                 when Kestra is started without storage (only STATIC/DNS are then usable).
     */
    @Inject
    public GrpcChannelManager(
        GrpcChannelConfiguration grpcChannelConfiguration,
        GrpcConfiguration grpcConfiguration,
        WorkerControllersConfiguration controllersConfig,
        Provider<StorageInterface> storageInterface) {
        this.grpcChannelConfiguration = grpcChannelConfiguration;
        this.grpcConfiguration = grpcConfiguration;
        this.controllersConfig = controllersConfig;
        this.storageInterface = storageInterface;
        this.sharedExecutorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("grpc-channel-", 0).factory());
    }

    /**
     * Initializes the default gRPC channel.
     */
    @PostConstruct
    public void init() {
        if (this.defaultChannel == null) {
            defaultChannel = createNewManagedChannel();
        }
    }

    /**
     * Return the shared gRPC Channel.
     *
     * @return the {@link Channel}
     */
    public Channel getDefaultChannel() {
        return defaultChannel;
    }

    /**
     * Create a new gRPC Channel.
     * <p>
     * Uses the new {@link WorkerControllersConfiguration} if available,
     * otherwise falls back to legacy single-endpoint mode.
     *
     * @return the {@link ManagedChannel}
     */
    @VisibleForTesting
    ManagedChannel createNewManagedChannel() {
        log.info("Using controllers configuration with discovery type: {}", controllersConfig.type());
        return createChannelWithControllersConfig();
    }

    /**
     * Creates a channel using the controller's configuration.
     */
    private ManagedChannel createChannelWithControllersConfig() {
        ManagedChannelBuilder<?> builder = switch (controllersConfig.type()) {
            case STATIC -> createStaticChannelBuilder();
            case DNS -> createDnsChannelBuilder();
            case STORAGE -> createStorageChannelBuilder();
        };
        return configureChannel(builder).build();
    }

    /**
     * Creates a channel builder for the static endpoint list.
     */
    private ManagedChannelBuilder<?> createStaticChannelBuilder() {
        WorkerControllersConfiguration.StaticConfig staticConfig = controllersConfig.staticConfig();
        if (staticConfig == null || staticConfig.endpoints() == null || staticConfig.endpoints().isEmpty()) {
            throw new IllegalStateException("Static configuration requires at least one endpoint");
        }

        List<EquivalentAddressGroup> addresses = staticConfig.endpoints().stream()
            .map(e ->
            {
                log.debug("Adding static controller endpoint: {}:{}", e.host(), e.port());
                return new EquivalentAddressGroup(new InetSocketAddress(e.host(), e.port()));
            })
            .toList();

        log.info("Configuring static discovery with {} controller endpoint(s)", addresses.size());

        // Register the static name resolver provider only once, store reference for cleanup
        if (STATIC_RESOLVER_REGISTERED.compareAndSet(false, true)) {
            StaticNameResolverProvider resolverProvider = new StaticNameResolverProvider(addresses);
            NameResolverRegistry.getDefaultRegistry().register(resolverProvider);
            REGISTERED_STATIC_RESOLVER_PROVIDER.set(resolverProvider);
        }
        return Grpc.newChannelBuilder("static:///controllers", createChannelCredentials());
    }

    /**
     * Creates a channel builder for internal-storage-based discovery.
     */
    private ManagedChannelBuilder<?> createStorageChannelBuilder() {
        WorkerControllersConfiguration.StorageConfig storageConfig = controllersConfig.storageConfig();
        if (storageConfig == null) {
            throw new IllegalStateException("Storage configuration is required when kestra.worker.controllers.type=STORAGE");
        }
        if (storageInterface == null) {
            throw new IllegalStateException("StorageInterface bean is not available; cannot use STORAGE discovery");
        }
        log.info("Configuring storage discovery with refresh interval {}", storageConfig.refreshInterval());

        if (STORAGE_RESOLVER_REGISTERED.compareAndSet(false, true)) {
            StorageNameResolverProvider resolverProvider = new StorageNameResolverProvider(
                this::discoverControllersFromStorage,
                storageConfig.refreshInterval()
            );
            NameResolverRegistry.getDefaultRegistry().register(resolverProvider);
            REGISTERED_STORAGE_RESOLVER_PROVIDER.set(resolverProvider);
        }
        return Grpc.newChannelBuilder("storage:///controllers", createChannelCredentials());
    }

    /**
     * Lists live controllers from internal storage. Entries whose {@code expiresAt} is in the past
     * are filtered out. Malformed entries are dropped individually, but a transient storage error on
     * any list or read call causes us to return the last known good list unchanged, so that a single
     * failed round-trip does not flap the LB pool.
     */
    @VisibleForTesting
    List<EquivalentAddressGroup> discoverControllersFromStorage() {
        List<FileAttributes> files;
        try {
            files = storageInterface.get().listInstanceResource(null, ControllerRegistry.REGISTRY_PREFIX);
        } catch (FileNotFoundException e) {
            // Registry prefix does not exist yet — no controllers registered.
            lastKnownStorageAddresses = List.of();
            return lastKnownStorageAddresses;
        } catch (IOException e) {
            log.warn("Failed to list controller registry from storage; returning last known good list ({} entries)",
                lastKnownStorageAddresses.size(), e);
            return lastKnownStorageAddresses;
        }

        Instant now = Instant.now();
        List<EquivalentAddressGroup> addresses = new ArrayList<>(files.size());
        for (FileAttributes attr : files) {
            if (attr.getType() != FileAttributes.FileType.File) {
                continue;
            }
            URI entryUri = ControllerRegistry.REGISTRY_PREFIX.resolve(attr.getFileName());
            try (InputStream in = storageInterface.get().getInstanceResource(null, entryUri)) {
                ControllerRegistration registration = MAPPER.readValue(in, ControllerRegistration.class);
                if (registration.isExpired(now)) {
                    log.debug("Skipping expired controller registration [{}] at {}", registration.id(), entryUri);
                    continue;
                }
                addresses.add(new EquivalentAddressGroup(new InetSocketAddress(registration.host(), registration.port())));
            } catch (JsonProcessingException e) {
                // Malformed JSON — drop just this entry, likely from a stale or partial writer.
                log.warn("Skipping malformed controller registration at {}", entryUri, e);
            } catch (IOException e) {
                // Transient storage failure on a per-entry read — treat as we would a list() failure
                // and preserve the last known good list rather than committing a shrunken pool.
                log.warn("Transient storage error reading controller registration at {}; returning last known good list ({} entries)",
                    entryUri, lastKnownStorageAddresses.size(), e);
                return lastKnownStorageAddresses;
            }
        }
        lastKnownStorageAddresses = List.copyOf(addresses);
        if (!addresses.isEmpty() && firstStorageLoadLogged.compareAndSet(false, true)) {
            log.info("Discovered {} controller endpoint(s) from internal storage: {}",
                addresses.size(),
                addresses.stream()
                    .flatMap(group -> group.getAddresses().stream())
                    .map(Object::toString)
                    .toList()
            );
        }
        return lastKnownStorageAddresses;
    }

    /**
     * Creates a channel builder for DNS-based discovery.
     */
    private ManagedChannelBuilder<?> createDnsChannelBuilder() {
        WorkerControllersConfiguration.DnsConfig dnsConfig = controllersConfig.dnsConfig();
        if (dnsConfig == null || dnsConfig.hostname() == null || dnsConfig.hostname().isBlank()) {
            throw new IllegalStateException("DNS configuration requires a hostname");
        }

        String target = switch (dnsConfig.recordType()) {
            case SRV -> {
                log.info("Configuring DNS discovery with SRV records for: {}", dnsConfig.hostname());
                yield "dns:///" + dnsConfig.hostname();
            }
            case A -> {
                log.info("Configuring DNS discovery with A records for: {}:{}", dnsConfig.hostname(), dnsConfig.defaultPort());
                yield "dns:///" + dnsConfig.hostname() + ":" + dnsConfig.defaultPort();
            }
        };
        return Grpc.newChannelBuilder(target, createChannelCredentials());
    }

    /**
     * Creates channel credentials for gRPC communication.
     * Returns plaintext (insecure) credentials by default. EE overrides this to add TLS support.
     *
     * @return the channel credentials.
     */
    protected ChannelCredentials createChannelCredentials() {
        return InsecureChannelCredentials.create();
    }

    /**
     * Configures common channel settings. EE overrides this to add TLS authority override.
     *
     * @param builder the channel builder to configure.
     * @return the configured channel builder.
     */
    protected ManagedChannelBuilder<?> configureChannel(ManagedChannelBuilder<?> builder) {
        builder.enableRetry()
            .maxRetryAttempts(grpcChannelConfiguration.maxRetryAttempts())
            .userAgent(getUserAgent())
            .keepAliveTime(grpcChannelConfiguration.keepAliveTime().toSeconds(), TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .maxInboundMessageSize(grpcConfiguration.maxInboundMessageSize())
            .executor(sharedExecutorService);

        // Configure load balancing policy
        String loadBalancingPolicy = controllersConfig.loadBalancing().policy().getGrpcName();
        log.debug("Using load balancing policy: {}", loadBalancingPolicy);
        builder.defaultLoadBalancingPolicy(loadBalancingPolicy);

        // Configure health checking if enabled
        if (controllersConfig.healthCheck().enabled()) {
            builder.defaultServiceConfig(generateHealthConfig());
        }
        return builder;
    }

    private static Map<String, Object> generateHealthConfig() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> serviceMap = new HashMap<>();

        config.put("healthCheckConfig", serviceMap);
        serviceMap.put("serviceName", ""); // The empty string ("") service, signifying the health of the whole server
        return config;
    }

    @PreDestroy
    public void close() {
        if (!stopped.compareAndSet(false, true)) {
            return; // Method called twice
        }
        log.info("Closing gRPC channel manager");
        // Shutdown channel first
        if (this.defaultChannel != null && !this.defaultChannel.isShutdown()) {
            try {
                shutdownChannelAndWait();
            } catch (Exception e) {
                log.debug("Error while stopping default gRPC channel", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Unregister the static name resolver provider to prevent memory leaks
        // and allow clean re-initialization in tests.
        // Use getAndSet to atomically retrieve and clear, preventing double-deregister races.
        StaticNameResolverProvider staticProvider = REGISTERED_STATIC_RESOLVER_PROVIDER.getAndSet(null);
        if (staticProvider != null) {
            STATIC_RESOLVER_REGISTERED.set(false);
            deregisterSilently(staticProvider, "static");
        }

        StorageNameResolverProvider storageProvider = REGISTERED_STORAGE_RESOLVER_PROVIDER.getAndSet(null);
        if (storageProvider != null) {
            STORAGE_RESOLVER_REGISTERED.set(false);
            deregisterSilently(storageProvider, "storage");
        }

        // Shutdown executor service
        if (this.sharedExecutorService != null) {
            ExecutorsUtils.closeExecutorService(
                "grpc-channel",
                this.sharedExecutorService,
                grpcChannelConfiguration.shutdownTimeout()
            );
        }
    }

    private void shutdownChannelAndWait() throws InterruptedException {
        this.defaultChannel.shutdown().awaitTermination(grpcChannelConfiguration.shutdownTimeout().toSeconds(), TimeUnit.SECONDS);
    }

    private static void deregisterSilently(NameResolverProvider provider, String name) {
        try {
            NameResolverRegistry.getDefaultRegistry().deregister(provider);
            log.debug("Unregistered {} name resolver provider", name);
        } catch (Exception e) {
            log.debug("Error while unregistering {} name resolver provider", name, e);
        }
    }

    /**
     * Returns the user agent string for gRPC requests.
     * Falls back to "unknown" version if KestraContext is not initialized.
     *
     * @return the user agent string
     */
    protected static String getUserAgent() {
        try {
            String version = KestraContext.getContext().getVersion();
            return "Kestra/" + (version != null ? version : "unknown");
        } catch (IllegalStateException e) {
            // KestraContext not initialized (e.g., in unit tests)
            return "Kestra/unknown";
        }
    }
}
