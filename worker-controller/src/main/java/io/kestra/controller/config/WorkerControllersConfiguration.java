package io.kestra.controller.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.kestra.controller.config.ControllerConfiguration.DEFAULT_GRPC_PORT;
import static io.kestra.controller.config.ControllerConfiguration.DEFAULT_GRPC_PORT_STRING;

/**
 * Configuration for worker-to-controller service discovery.
 * <p>
 * Supports two discovery strategies:
 * <ul>
 *   <li>STATIC: Explicit list of controller endpoints with gRPC load-balancing</li>
 *   <li>DNS: DNS SRV/A record resolution with gRPC load-balancing</li>
 * </ul>
 * <p>
 * Example configuration:
 * <pre>
 * kestra:
 *   worker:
 *     controllers:
 *       type: STATIC
 *       static:
 *         endpoints:
 *           - host: controller-1.example.com
 *             port: 9096
 *           - host: controller-2.example.com
 *             port: 9096
 *       load-balancing:
 *         policy: ROUND_ROBIN
 *       health-check:
 *         enabled: true
 *         interval: PT30S
 * </pre>
 */
@ConfigurationProperties("kestra.worker.controllers")
public record WorkerControllersConfiguration(
    @NotNull
    @Bindable(defaultValue = "STATIC")
    DiscoveryType type,

    @Nullable
    StaticConfig staticConfig,

    @Nullable
    DnsConfig dnsConfig,

    @Valid
    LoadBalancing loadBalancing,

    @Valid
    HealthCheck healthCheck
) {
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
        DNS
    }

    @ConfigurationProperties("static")
    @Requires(property = "kestra.worker.controllers.type", value = "STATIC")
    public record StaticConfig(
        List<Endpoint> endpoints
    ) {
    }

    /**
     * A single controller endpoint.
     */
    public record Endpoint(
        @NotBlank(message = "Host is required")
        String host,
        Integer port
    ) {

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

        @Bindable(defaultValue = DEFAULT_GRPC_PORT_STRING)
        int defaultPort,

        @Bindable(defaultValue = "SRV")
        DnsRecordType recordType,

        @Bindable(defaultValue = "PT30S")
        Duration refreshInterval
    ) {
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
     * Load balancing configuration.
     */
    @ConfigurationProperties("load-balancing")
    public record LoadBalancing(
        @Bindable(defaultValue = "ROUND_ROBIN")
        Policy policy
    ) {
        /**
         * Load balancing policy.
         */
        public enum Policy {
            /**
             * Round-robin load balancing (default gRPC policy).
             */
            ROUND_ROBIN,
            /**
             * Pick first available endpoint.
             */
            PICK_FIRST
        }
    }

    /**
     * Health check configuration for dead controller detection.
     */
    @ConfigurationProperties("health-check")
    public record HealthCheck(
        @Bindable(defaultValue = "true")
        boolean enabled
    ) {
    }
}
