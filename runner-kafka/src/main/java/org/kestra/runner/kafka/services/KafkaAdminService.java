package org.kestra.runner.kafka.services;

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.configs.TopicDefaultsConfig;
import org.kestra.runner.kafka.configs.TopicsConfig;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class KafkaAdminService implements AutoCloseable {
    @Inject
    private TopicDefaultsConfig topicDefaultsConfig;

    @Inject
    private List<TopicsConfig> topicsConfig;

    @Inject
    private ClientConfig clientConfig;

    @Inject
    private MetricRegistry metricRegistry;

    private AdminClient adminClient;

    private KafkaClientMetrics kafkaClientMetrics;

    public AdminClient of() {
        if (this.adminClient == null) {
            Properties properties = new Properties();
            properties.putAll(clientConfig.getProperties());

            adminClient = AdminClient.create(properties);

            kafkaClientMetrics = new KafkaClientMetrics(adminClient);
            metricRegistry.bind(kafkaClientMetrics);
        }

        return adminClient;
    }

    @PreDestroy
    @Override
    public void close() throws Exception {
        if (adminClient != null) {
            adminClient.close();
        }

        if (kafkaClientMetrics != null) {
            kafkaClientMetrics.close();
        }
    }

    private TopicsConfig getTopicConfig(Class<?> cls) {
        return this.topicsConfig
            .stream()
            .filter(r -> r.getCls() == cls)
            .findFirst()
            .orElseThrow();
    }

    private TopicsConfig getTopicConfig(String key) {
        return this.topicsConfig
            .stream()
            .filter(r -> r.getKey().equals(key))
            .findFirst()
            .orElseThrow();
    }

    public void createIfNotExist(String key) {
        this.createIfNotExist(this.getTopicConfig(key));
    }

    public void createIfNotExist(Class<?> cls) {
        this.createIfNotExist(this.getTopicConfig(cls));
    }

    @SuppressWarnings("deprecation")
    public void createIfNotExist(TopicsConfig topicConfig) {
        NewTopic newTopic = new NewTopic(
            topicConfig.getName(),
            topicConfig.getPartitions() != null ? topicConfig.getPartitions() : topicDefaultsConfig.getPartitions(),
            topicConfig.getReplicationFactor() != null ? topicConfig.getReplicationFactor() : topicDefaultsConfig.getReplicationFactor()
        );

        Map<String, String> properties = new HashMap<>();

        if (topicDefaultsConfig.getProperties() != null) {
            properties.putAll(topicDefaultsConfig.getProperties());
        }

        if (topicConfig.getProperties() != null) {
            properties.putAll(topicConfig.getProperties());
        }

        newTopic.configs(properties);

        try {
            this.of().createTopics(Collections.singletonList(newTopic)).all().get();
            log.info("Topic '{}' created", newTopic.name());
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TopicExistsException) {
                try {
                    adminClient
                        .alterConfigs(new HashMap<>() {{
                            put(
                                new ConfigResource(ConfigResource.Type.TOPIC, newTopic.name()),
                                new org.apache.kafka.clients.admin.Config(
                                    newTopic.configs()
                                        .entrySet()
                                        .stream()
                                        .map(config -> new ConfigEntry(config.getKey(), config.getValue()))
                                        .collect(Collectors.toList())
                                )
                            );
                        }}).all().get();

                    log.info("Topic Config '{}' updated", newTopic.name());

                } catch (InterruptedException | ExecutionException e1) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public void delete(String key) {
        this.delete(this.getTopicConfig(key));
    }

    public void delete(Class<?> cls) {
        this.delete(this.getTopicConfig(cls));
    }

    public void delete(TopicsConfig topicConfig) {
        try {
            this.of().deleteTopics(Collections.singletonList(topicConfig.getName())).all().get();
            log.info("Topic '{}' deleted", topicConfig.getName());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTopicName(Class<?> cls) {
        return this.getTopicConfig(cls).getName();
    }

    public String getTopicName(String key) {
        return this.getTopicConfig(key).getName();
    }
}
