package io.kestra.controller.config;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerControllersConfigurationTest {

    @Test
    void shouldParseStaticConfiguration() {
        Map<String, Object> properties = Map.of(
            "kestra.worker.controllers.type", "STATIC",
            "kestra.worker.controllers.static.endpoints[0].host", "controller-1.example.com",
            "kestra.worker.controllers.static.endpoints[0].port", "9096",
            "kestra.worker.controllers.static.endpoints[1].host", "controller-2.example.com",
            "kestra.worker.controllers.static.endpoints[1].port", "9097",
            "kestra.worker.controllers.load-balancing.policy", "ROUND_ROBIN",
            "kestra.worker.controllers.health-check.enabled", "true",
            "kestra.worker.controllers.health-check.interval", "10s"
        );

        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            WorkerControllersConfiguration config = context.getBean(WorkerControllersConfiguration.class);

            assertThat(config.type()).isEqualTo(WorkerControllersConfiguration.DiscoveryType.STATIC);
            assertThat(config.staticConfig()).isNotNull();
            assertThat(config.staticConfig().endpoints()).hasSize(2);
            assertThat(config.staticConfig().endpoints().get(0).host()).isEqualTo("controller-1.example.com");
            assertThat(config.staticConfig().endpoints().get(0).port()).isEqualTo(9096);
            assertThat(config.staticConfig().endpoints().get(1).host()).isEqualTo("controller-2.example.com");
            assertThat(config.staticConfig().endpoints().get(1).port()).isEqualTo(9097);
            assertThat(config.loadBalancing().policy()).isEqualTo(WorkerControllersConfiguration.LoadBalancing.Policy.ROUND_ROBIN);
            assertThat(config.healthCheck().enabled()).isTrue();
        }
    }

    @Test
    void shouldParseDnsSrvConfiguration() {
        Map<String, Object> properties = Map.of(
            "kestra.worker.controllers.type", "DNS",
            "kestra.worker.controllers.dns.hostname", "kestra-controller.default.svc.cluster.local",
            "kestra.worker.controllers.dns.record-type", "SRV",
            "kestra.worker.controllers.dns.refresh-interval", "30s"
        );

        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            WorkerControllersConfiguration config = context.getBean(WorkerControllersConfiguration.class);

            assertThat(config.type()).isEqualTo(WorkerControllersConfiguration.DiscoveryType.DNS);
            assertThat(config.dnsConfig()).isNotNull();
            assertThat(config.dnsConfig().hostname()).isEqualTo("kestra-controller.default.svc.cluster.local");
            assertThat(config.dnsConfig().recordType()).isEqualTo(WorkerControllersConfiguration.DnsConfig.DnsRecordType.SRV);
            assertThat(config.dnsConfig().refreshInterval()).isEqualTo(Duration.ofSeconds(30));
        }
    }

    @Test
    void shouldParseDnsARecordConfiguration() {
        Map<String, Object> properties = Map.of(
            "kestra.worker.controllers.type", "DNS",
            "kestra.worker.controllers.dns.hostname", "controllers.internal.company.com",
            "kestra.worker.controllers.dns.record-type", "A",
            "kestra.worker.controllers.dns.default-port", "9096",
            "kestra.worker.controllers.dns.refresh-interval", "60s"
        );

        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            WorkerControllersConfiguration config = context.getBean(WorkerControllersConfiguration.class);

            assertThat(config.type()).isEqualTo(WorkerControllersConfiguration.DiscoveryType.DNS);
            assertThat(config.dnsConfig()).isNotNull();
            assertThat(config.dnsConfig().hostname()).isEqualTo("controllers.internal.company.com");
            assertThat(config.dnsConfig().recordType()).isEqualTo(WorkerControllersConfiguration.DnsConfig.DnsRecordType.A);
            assertThat(config.dnsConfig().defaultPort()).isEqualTo(9096);
            assertThat(config.dnsConfig().refreshInterval()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    @Test
    void shouldUseDefaultValues() {
        Map<String, Object> properties = Map.of(
            "kestra.worker.controllers.type", "STATIC",
            "kestra.worker.controllers.static.endpoints[0].host", "localhost"
        );

        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            WorkerControllersConfiguration config = context.getBean(WorkerControllersConfiguration.class);

            // Check default port
            assertThat(config.staticConfig().endpoints().getFirst().port()).isNotNull(); // use controller random port

            // Check default load balancing policy
            assertThat(config.loadBalancing().policy()).isEqualTo(WorkerControllersConfiguration.LoadBalancing.Policy.ROUND_ROBIN);

            // Check default health check settings
            assertThat(config.healthCheck().enabled()).isTrue();
        }
    }

    @Test
    void shouldParsePickFirstLoadBalancing() {
        Map<String, Object> properties = Map.of(
            "kestra.worker.controllers.type", "STATIC",
            "kestra.worker.controllers.static.endpoints[0].host", "localhost",
            "kestra.worker.controllers.load-balancing.policy", "PICK_FIRST"
        );

        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            WorkerControllersConfiguration config = context.getBean(WorkerControllersConfiguration.class);

            assertThat(config.loadBalancing().policy()).isEqualTo(WorkerControllersConfiguration.LoadBalancing.Policy.PICK_FIRST);
        }
    }

    @Test
    void shouldParseDisabledHealthCheck() {
        Map<String, Object> properties = Map.of(
            "kestra.worker.controllers.type", "STATIC",
            "kestra.worker.controllers.static.endpoints[0].host", "localhost",
            "kestra.worker.controllers.health-check.enabled", "false"
        );

        try (ApplicationContext context = ApplicationContext.run(PropertySource.of("test", properties))) {
            WorkerControllersConfiguration config = context.getBean(WorkerControllersConfiguration.class);

            assertThat(config.healthCheck().enabled()).isFalse();
        }
    }
}
