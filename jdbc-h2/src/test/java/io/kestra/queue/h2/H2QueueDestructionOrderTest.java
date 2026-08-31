package io.kestra.queue.h2;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.factory.QueueFactoryInterface;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

class H2QueueDestructionOrderTest {

    @Singleton
    @Requires(env = "destruction-order")
    static class DestructionRecorder implements BeanDestroyedEventListener<Object> {
        static final List<Object> DESTROYED = new CopyOnWriteArrayList<>();

        @Override
        public void onDestroyed(BeanDestroyedEvent<Object> event) {
            DESTROYED.add(event.getBean());
        }
    }

    @Test
    void shouldDestroyQueuesBeforeTheFactoryAndTheDatasourceWhenTheContextCloses() {
        ApplicationContext context = ApplicationContext.run("test", "queue", "destruction-order");
        Object queue = context.getBean(DispatchQueueInterface.class, Qualifiers.byTypeArguments(Execution.class));
        Object factory = context.getBean(QueueFactoryInterface.class);
        Object jdbcQueueClient = context.getBean(JdbcQueueClient.class);
        Object dataSource = context.getBean(DataSource.class);

        context.close();

        List<Object> destroyed = DestructionRecorder.DESTROYED;
        assertThat(destroyed).contains(queue, factory, jdbcQueueClient, dataSource);
        assertThat(destroyed.indexOf(queue)).isLessThan(destroyed.indexOf(factory));
        assertThat(destroyed.indexOf(factory)).isLessThan(destroyed.indexOf(jdbcQueueClient));
        assertThat(destroyed.indexOf(jdbcQueueClient)).isLessThan(destroyed.indexOf(dataSource));
    }
}
