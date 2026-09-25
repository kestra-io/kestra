package io.kestra.queue.h2;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.factory.QueueBackendDependencies;
import io.kestra.core.queues.factory.QueueFactoryInterface;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = { "test", "queue" })
class H2QueueBackendDependenciesTest {
    @Inject
    private ApplicationContext applicationContext;

    @Test
    void shouldDeclareBackendDependenciesAsRequiredComponentOfQueueFactory() {
        assertThat(applicationContext.getBeanDefinition(QueueFactoryInterface.class).getRequiredComponents())
            .contains(QueueBackendDependencies.class);
    }
}
