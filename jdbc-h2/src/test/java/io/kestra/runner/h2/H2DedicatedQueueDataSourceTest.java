package io.kestra.runner.h2;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dedicated-queue-datasource path: with {@code kestra.queue.jdbc.type=h2} + a distinct
 * {@code kestra.queue.jdbc.url}, the queue writes to a separate H2 database (created by the
 * {@code 0-init-queue} migration), and the primary database does not have the queues table.
 */
@MicronautTest
@Property(name = "kestra.queue.type", value = "h2")
@Property(name = "kestra.repository.type", value = "h2")
@Property(name = "kestra.queue.jdbc.type", value = "h2")
@Property(name = "kestra.queue.jdbc.url", value = "jdbc:h2:mem:queue_dedicated;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE")
@Property(name = "kestra.queue.jdbc.username", value = "sa")
@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:queue_dedicated_primary;LOCK_TIMEOUT=30000;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class H2DedicatedQueueDataSourceTest {

    @Inject
    BroadcastQueueInterface<FlowInterface> flowQueue;

    @Inject
    QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Inject
    JooqDSLContextWrapper primaryWrapper;

    @Test
    void shouldUseDedicatedQueueDatasourceAndNotPrimary() throws QueueException {
        // A dedicated queue datasource must be configured
        assertThat(queueJdbcDataSourceProvider.isDedicated()).isTrue();

        // The dedicated queue wrapper must be different from the primary wrapper
        JooqDSLContextWrapper queueWrapper = queueJdbcDataSourceProvider.wrapper();
        assertThat(queueWrapper).isNotSameAs(primaryWrapper);

        // The queues table should be queryable from the dedicated queue database
        Integer countInDedicated = queueWrapper.transactionResult(
            conf -> org.jooq.impl.DSL.using(conf)
                .selectCount()
                .from(org.jooq.impl.DSL.table("queues"))
                .fetchOne(0, Integer.class)
        );
        assertThat(countInDedicated).isNotNegative();

        // The queues table should NOT exist in the primary database
        boolean queueTableInPrimary = primaryWrapper.transactionResult(conf ->
        {
            try {
                org.jooq.impl.DSL.using(conf)
                    .selectCount()
                    .from(org.jooq.impl.DSL.table("queues"))
                    .fetchOne(0, Integer.class);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        assertThat(queueTableInPrimary).isFalse();

        // When: a message is published to the queue
        flowQueue.emit(
            io.kestra.core.models.flows.FlowWithSource.builder()
                .id("dedicated-queue-test")
                .namespace("io.kestra.tests")
                .tasks(java.util.Collections.emptyList())
                .build()
        );

        // Then: the message should be visible in the dedicated queue database
        Integer afterEmit = queueWrapper.transactionResult(
            conf -> org.jooq.impl.DSL.using(conf)
                .selectCount()
                .from(org.jooq.impl.DSL.table("queues"))
                .fetchOne(0, Integer.class)
        );
        assertThat(afterEmit).isGreaterThan(countInDedicated);
    }
}
