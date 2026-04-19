package io.kestra.controller.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.kestra.controller.config.ControllerConfiguration.DEFAULT_GRPC_PORT;
import static io.kestra.controller.config.ControllerConfiguration.DEFAULT_GRPC_PORT_STRING;

/**
 * Configuration for worker-to-controller service discovery.
 * <p>
 * Supports three discovery strategies:
 * <ul>
 * <li>STATIC: Explicit list of controller endpoints with gRPC load-balancing</li>
 * <li>DNS: DNS SRV/A record resolution with gRPC load-balancing</li>
 * <li>STORAGE: Dynamic discovery via Kestra internal storage (controllers self-register)</li>
 * </ul>
 * <p>
 */
@ConfigurationProperties("kestra.worker.controllers")
public record WorkerControllersConfiguration(
    @NotNull
    @Bindable(defaultValue = "STATIC") DiscoveryType type,

    @Nullable StaticConfig staticConfig,

    @Nullable DnsConfig dnsConfig,

    @Nullable StorageConfig storageConfig,

    @Valid LoadBalancing loadBalancing,

    @Valid HealthCheck healthCheck,

    @Valid WaitForReady waitForReady) {
    /**
     * Service discovery type.
     */
    public enum DiscoveryType {
        /**
         * Explicit list of controller endpoints.
         */
        STATIC,
        /**
         * DNS-based discovery using SRV or A records.
         */
        DNS,
        /**
         * Dynamic discovery via Kestra internal storage; controllers self-register their endpoint.
         */
        STORAGE
    }

    @ConfigurationProperties("static")
    @Requires(property = "kestra.worker.controllers.type", value = "STATIC")
    public record StaticConfig(
        List<Endpoint> endpoints) {
    }

    /**
     * A single controller endpoint.
     */
    public record Endpoint(
        @NotBlank(message = "Host is required") String host,
        Integer port) {

        @Override
        public Integer port() {
            return Optional.ofNullable(port).orElse(DEFAULT_GRPC_PORT);
        }
    }

    /**
     * DNS-based discovery configuration.
     */
    @ConfigurationProperties("dns")
    @Requires(property = "kestra.worker.controllers.type", value = "DNS")
    public record DnsConfig(
        String hostname,

        @Bindable(defaultValue = DEFAULT_GRPC_PORT_STRING) int defaultPort,

        @Bindable(defaultValue = "SRV") DnsRecordType recordType,

        @Bindable(defaultValue = "PT30S") Duration refreshInterval) {
        /**
         * DNS record type for discovery.
         */
        public enum DnsRecordType {
            /**
             * SRV records (includes port information).
             */
            SRV,
            /**
             * A records (requires default port).
             */
            A
        }
    }

    /**
     * Internal storage-based discovery configuration.
     */
    @ConfigurationProperties("storage")
    @Requires(property = "kestra.worker.controllers.type", value = "STORAGE")
    public record StorageConfig(
        @Bindable(defaultValue = "PT30S") Duration refreshInterval) {
    }

    /**
     * Load balancing configuration.
     */
    @ConfigurationProperties("load-balancing")
    public record LoadBalancing(
        @Bindable(defaultValue = "ROUND_ROBIN") Policy policy) {
        /**
         * Load balancing policy.
         */
        public enum Policy {
            /**
             * Round-robin load balancing (default gRPC policy).
             */
            ROUND_ROBIN("round_robin"),
            /**
             * Pick first available endpoint.
             */
            PICK_FIRST("pick_first");

            private final String grpcName;

            Policy(String grpcName) {
                this.grpcName = grpcName;
            }

            public String getGrpcName() {
                return grpcName;
            }
        }

    }

    /**
     * Health check configuration for dead controller detection.
     */
    @ConfigurationProperties("health-check")
    public record HealthCheck(
        @Bindable(defaultValue = "true") boolean enabled) {
    }

    /**
     * Wait-for-ready configuration for gRPC calls. When enabled, workers will wait for the controller to be ready before making gRPC calls.
     * 
     * @param enabled
     */
    @ConfigurationProperties("wait-for-ready")
    public record WaitForReady(
        @Bindable(defaultValue = "true") boolean enabled,
        @Bindable(defaultValue = "PT30S") Duration deadline) {
    }
}
