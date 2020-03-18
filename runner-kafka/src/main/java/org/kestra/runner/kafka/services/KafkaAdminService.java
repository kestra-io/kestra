package org.kestra.runner.kafka.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.configs.TopicDefaultsConfig;
import org.kestra.runner.kafka.configs.TopicsConfig;
import org.kestra.runner.kafka.metrics.KafkaClientMetrics;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class KafkaAdminService {
    @Inject
    private AdminClient adminClient;

    @Inject
    private TopicDefaultsConfig topicDefaultsConfig;

    @Inject
    private List<TopicsConfig> topicsConfig;

    private TopicsConfig getTopicConfig(Class<?> cls) {
        return this.topicsConfig
            .stream()
            .filter(r -> r.getCls().equals(cls.getName().toLowerCase().replace(".", "-")))
            .findFirst()
            .orElseThrow();
    }

    @SuppressWarnings("deprecation")
    public void createIfNotExist(Class<?> cls) {
        TopicsConfig topicConfig = this.getTopicConfig(cls);

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
            adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
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

    public String getTopicName(Class<?> cls) {
        return this.getTopicConfig(cls).getName();
    }
}
