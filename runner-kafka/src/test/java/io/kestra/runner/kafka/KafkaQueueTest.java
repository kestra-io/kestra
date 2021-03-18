package io.kestra.runner.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;

import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class KafkaQueueTest {
    @Inject
    ApplicationContext applicationContext;

    @Test
    void topicsConfig() {
        TopicsConfig topicsConfig = KafkaQueue.topicsConfig(applicationContext, Execution.class);
        assertThat(topicsConfig.getCls(), is(Execution.class));

        TopicsConfig byName = KafkaQueue.topicsConfigByTopicName(applicationContext, topicsConfig.getName());
        assertThat(byName, is(topicsConfig));

        topicsConfig = KafkaQueue.topicsConfig(applicationContext, KafkaStreamSourceService.TOPIC_EXECUTOR);
        assertThat(topicsConfig.getKey(), is(KafkaStreamSourceService.TOPIC_EXECUTOR));

        byName = KafkaQueue.topicsConfigByTopicName(applicationContext, topicsConfig.getName());
        assertThat(byName, is(topicsConfig));
    }
}