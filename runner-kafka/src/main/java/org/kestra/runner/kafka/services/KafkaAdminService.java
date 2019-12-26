package org.kestra.runner.kafka.services;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.configs.TopicDefaultsConfig;
import org.kestra.runner.kafka.configs.TopicsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class KafkaAdminService {
    @Inject
    private ClientConfig clientConfig;

    @Inject
    private TopicDefaultsConfig topicDefaultsConfig;

    @Inject
    private List<TopicsConfig> topicsConfig;

    public AdminClient of() {
        Properties properties = new Properties();
        properties.putAll(clientConfig.getProperties());

        return AdminClient.create(properties);
    }

    private TopicsConfig getTopicConfig(Class cls) {
        return this.topicsConfig
            .stream()
            .filter(r -> r.getCls().equals(cls.getName().toLowerCase().replace(".", "-")))
            .findFirst()
            .orElseThrow();
    }

    @SuppressWarnings("deprecation")
    public boolean createIfNotExist(Class cls) {
        TopicsConfig topicConfig = this.getTopicConfig(cls);

        AdminClient admin = this.of();
        NewTopic newTopic = new NewTopic(
            topicConfig.getName(),
            topicDefaultsConfig.getPartitions(),
            topicDefaultsConfig.getReplicationFactor()
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
            admin.createTopics(Collections.singletonList(newTopic)).all().get();
            log.info("Topic '{}' created", newTopic.name());
            return true;
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TopicExistsException) {
                try {
                    admin
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

        return false;
    }


    public String getTopicName(Class cls) {
        return this.getTopicConfig(cls).getName();
    }
}
