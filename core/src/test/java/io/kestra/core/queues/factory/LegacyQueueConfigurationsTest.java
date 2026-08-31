package io.kestra.core.queues.factory;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;

import org.assertj.core.api.InstanceOfAssertFactories;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyQueueConfigurationsTest {

    @LegacyQueueConfiguration(prefix = "kestra.legacy")
    @LegacyQueueConfiguration(prefix = "kestra.server.metrics.legacy", path = "metrics")
    static class LegacyPlugin {
    }

    @Test
    void shouldMapLegacyPrefixesIntoTheNestedPluginConfigurationWhenSet() {
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of(Map.of(
            "kestra.legacy.url", "legacy://host",
            "kestra.legacy.client.properties.bootstrap.servers", "legacy:9092",
            "kestra.legacy.defaults.topic.replication-factor", 2,
            "kestra.server.metrics.legacy.admin", false
        )));

        Map<String, Object> configuration = LegacyQueueConfigurations.apply(LegacyPlugin.class, "legacy", Map.of("url", "new://host"), resolver);

        assertThat(configuration)
            .containsEntry("url", "new://host")
            .containsEntry("client", Map.of("properties", Map.of("bootstrap", Map.of("servers", "legacy:9092"))))
            .containsEntry("defaults", Map.of("topic", Map.of("replicationFactor", 2)))
            .containsEntry("metrics", Map.of("admin", false));
    }

    @Test
    void shouldKeepConfiguredValuesWhenLegacyAndConfiguredShareANestedKey() {
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of(Map.of(
            "kestra.legacy.client.prefix", "legacy_",
            "kestra.legacy.client.properties.acks", "all"
        )));

        Map<String, Object> configuration = LegacyQueueConfigurations.apply(LegacyPlugin.class, "legacy", Map.of("client", Map.of("prefix", "new_")), resolver);

        assertThat(configuration).containsExactly(Map.entry("client", Map.of("prefix", "new_", "properties", Map.of("acks", "all"))));
    }

    @Test
    void shouldReadLegacyPropertiesWhenProvidedAsEnvironmentVariables() {
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of(
            "env",
            Map.of("KESTRA_LEGACY_DEFAULTS_TOPIC_REPLICATION_FACTOR", "2"),
            PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE
        ));

        Map<String, Object> configuration = LegacyQueueConfigurations.apply(LegacyPlugin.class, "legacy", Map.of(), resolver);

        assertThat(configuration)
            .extractingByKey("defaults", InstanceOfAssertFactories.MAP)
            .extractingByKey("topic", InstanceOfAssertFactories.MAP)
            .containsEntry("replicationFactor", "2");
    }

    @Test
    void shouldLeaveTheConfigurationUntouchedWhenNoLegacyPropertyIsSet() {
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of(Map.of("kestra.queue.type", "legacy")));

        Map<String, Object> configuration = LegacyQueueConfigurations.apply(LegacyPlugin.class, "legacy", Map.of("url", "new://host"), resolver);

        assertThat(configuration).containsExactly(Map.entry("url", "new://host"));
    }
}
