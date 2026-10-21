package io.kestra.queue.h2;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.factory.QueueFactoryInterface;
import io.kestra.queue.jdbc.JdbcDependencies;
import io.kestra.queue.jdbc.JdbcQueueFactory;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = { "test", "queue" })
@Property(name = "kestra.queue.type", value = "H2")
class H2MixedCaseQueueTypeTest {
    @Inject
    private ApplicationContext applicationContext;

    @Test
    void shouldEnableJdbcQueueWhenTypeCaseDiffers() {
        assertThat(applicationContext.containsBean(JdbcDependencies.class)).isTrue();
        assertThat(applicationContext.getBean(QueueFactoryInterface.class)).isInstanceOf(JdbcQueueFactory.class);
    }
}
