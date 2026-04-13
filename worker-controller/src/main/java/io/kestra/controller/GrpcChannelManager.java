package io.kestra.controller;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ChannelCredentials;
import io.grpc.Channel;
import io.grpc.EquivalentAddressGroup;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import io.grpc.TlsChannelCredentials;
import io.kestra.controller.config.GrpcChannelConfiguration;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.config.GrpcTlsConfiguration;
import io.kestra.controller.config.WorkerControllersConfiguration;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.kestra.controller.grpc.resolver.StaticNameResolverProvider;
import io.kestra.core.contexts.KestraContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages gRPC channels for worker-to-controller communication.
 * <p>
 * Supports two service discovery strategies:
 * <ul>
 * <li>STATIC: Explicit list of controller endpoints with gRPC load-balancing</li>
 * <li>DNS: DNS SRV/A record resolution with gRPC load-balancing</li>
 * </ul>
 * <p>
 */
@Singleton
@Slf4j
public class GrpcChannelManager {

    private static final AtomicBoolean STATIC_RESOLVER_REGISTERED = new AtomicBoolean(false);
    // Reference to the registered resolver provider for cleanup
    private static final AtomicReference<StaticNameResolverProvider> REGISTERED_RESOLVER_PROVIDER = new AtomicReference<>();
    
    private volatile ManagedChannel defaultChannel;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final GrpcChannelConfiguration grpcChannelConfiguration;
    private final GrpcConfiguration grpcConfiguration;
    private final GrpcTlsConfiguration grpcTlsConfiguration;
    private final WorkerControllersConfiguration controllersConfig;

    // ExecutorService shared across channels
    private final ExecutorService sharedExecutorService;

    /**
     * Creates a new {@link GrpcChannelManager} instance.
     *
     * @param grpcChannelConfiguration the gRPC channel configuration.
     * @param grpcConfiguration        the global gRPC configuration.
     * @param grpcTlsConfiguration     the gRPC TLS configuration.
     * @param controllersConfig        the multi-endpoint controllers configuration.
     */
    @Inject
    public GrpcChannelManager(GrpcChannelConfiguration grpcChannelConfiguration, GrpcConfiguration grpcConfiguration, GrpcTlsConfiguration grpcTlsConfiguration, WorkerControllersConfiguration controllersConfig) {
        this.grpcChannelConfiguration = grpcChannelConfiguration;
        this.grpcConfiguration = grpcConfiguration;
        this.grpcTlsConfiguration = grpcTlsConfiguration;
        this.controllersConfig = controllersConfig;
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
            REGISTERED_RESOLVER_PROVIDER.set(resolverProvider);
        }
        return Grpc.newChannelBuilder("static:///controllers", createChannelCredentials());
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
     * Creates channel credentials based on TLS configuration.
     * Returns plaintext credentials when TLS is disabled, TLS credentials when enabled.
     */
    private ChannelCredentials createChannelCredentials() {
        if (!grpcTlsConfiguration.enabled()) {
            return InsecureChannelCredentials.create();
        }

        try {
            TlsChannelCredentials.Builder builder = TlsChannelCredentials.newBuilder();

            // Trust manager: truststore for verifying server certificate
            if (grpcTlsConfiguration.insecureTrustAllCertificates()) {
                log.warn("gRPC TLS is configured to trust all certificates - this should only be used for development");
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers());
            } else if (grpcTlsConfiguration.trustStore() != null) {
                TrustManagerFactory tmf = GrpcTlsConfiguration.loadTrustManagerFactory(grpcTlsConfiguration.trustStore());
                builder.trustManager(tmf.getTrustManagers());
            }

            // Key manager: client keystore for mTLS
            if (grpcTlsConfiguration.keyStore() != null) {
                KeyManagerFactory kmf = GrpcTlsConfiguration.loadKeyManagerFactory(grpcTlsConfiguration.keyStore());
                builder.keyManager(kmf.getKeyManagers());
            }

            log.info("gRPC TLS enabled for channel");
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to configure gRPC TLS channel credentials", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to configure gRPC TLS channel credentials", e);
        }
    }

    /**
     * Configures common channel settings.
     */
    private ManagedChannelBuilder<?> configureChannel(ManagedChannelBuilder<?> builder) {
        builder.enableRetry()
            .maxRetryAttempts(grpcChannelConfiguration.maxRetryAttempts())
            .userAgent(getUserAgent())
            .keepAliveTime(grpcChannelConfiguration.keepAliveTime().toSeconds(), TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .maxInboundMessageSize(grpcConfiguration.maxInboundMessageSize())
            .executor(sharedExecutorService);

        // Override TLS authority if configured (required for static discovery)
        if (grpcTlsConfiguration.enabled() && grpcTlsConfiguration.authorityOverride() != null) {
            log.info("Overriding TLS authority to: {}", grpcTlsConfiguration.authorityOverride());
            builder.overrideAuthority(grpcTlsConfiguration.authorityOverride());
        }

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
        StaticNameResolverProvider provider = REGISTERED_RESOLVER_PROVIDER.getAndSet(null);
        if (provider != null) {
            STATIC_RESOLVER_REGISTERED.set(false);
            try {
                NameResolverRegistry.getDefaultRegistry().deregister(provider);
                log.debug("Unregistered static name resolver provider");
            } catch (Exception e) {
                log.debug("Error while unregistering static name resolver provider", e);
            }
        }

        // Shutdown executor service
        if (this.sharedExecutorService != null) {
            try {
                this.sharedExecutorService.shutdown();
                if (!this.sharedExecutorService.awaitTermination(grpcChannelConfiguration.shutdownTimeout().toSeconds(), TimeUnit.SECONDS)) {
                    log.warn("Executor service did not terminate within timeout, forcing shutdown");
                    this.sharedExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.debug("Interrupted while shutting down executor service", e);
                this.sharedExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void shutdownChannelAndWait() throws InterruptedException {
        this.defaultChannel.shutdown().awaitTermination(grpcChannelConfiguration.shutdownTimeout().toSeconds(), TimeUnit.SECONDS);
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
