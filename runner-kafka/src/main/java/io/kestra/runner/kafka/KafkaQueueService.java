package io.kestra.runner.kafka;

import lombok.extern.slf4j.Slf4j;
import io.kestra.core.queues.QueueService;
import io.kestra.runner.kafka.configs.TopicsConfig;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class KafkaQueueService {
    public <T> void log(Logger log, TopicsConfig topicsConfig, String key, T object, String message) {
        if (log.isTraceEnabled()) {
            log.trace("{} on  topic '{}', value {}", message, topicsConfig.getName(), object);
        } else if (log.isDebugEnabled()) {
            log.trace("{} on topic '{}', key {}", message, topicsConfig.getName(), key);
        }
    }
}
