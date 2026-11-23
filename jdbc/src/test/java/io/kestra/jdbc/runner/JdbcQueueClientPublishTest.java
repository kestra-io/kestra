package io.kestra.jdbc.runner;

import java.util.List;

import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
abstract public class JdbcQueueClientPublishTest {
    @Inject
    protected JdbcQueueClient jdbcQueueClient;

    @Inject
    protected QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Test
    public void shouldCommitImmediatelyWhenPublishingOutsideATransaction() throws Exception {
        // Given
        String queue = IdUtils.create();

        // When
        jdbcQueueClient.publish(queue, null, "key", "{}");

        // Then
        assertThat(jdbcQueueClient.queueLag(queue, null)).isEqualTo(1);
    }

    @Test
    public void shouldCommitEveryMessageWhenPublishingABatchOutsideATransaction() throws Exception {
        // Given
        String queue = IdUtils.create();

        // When
        jdbcQueueClient.publish(List.of(
            new JdbcQueueClient.PublishedMessage(queue, null, "key1", "{}"),
            new JdbcQueueClient.PublishedMessage(queue, null, "key2", "{}")
        ));

        // Then
        assertThat(jdbcQueueClient.queueLag(queue, null)).isEqualTo(2);
    }

    @Test
    public void shouldRollBackWithTheOuterTransactionWhenPublishingInsideOne() {
        // Given
        String queue = IdUtils.create();

        // When
        assertThatThrownBy(() -> queueJdbcDataSourceProvider.wrapper().transaction(configuration ->
        {
            try {
                jdbcQueueClient.publish(queue, null, "key", "{}");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            throw new IllegalArgumentException("rollback");
        })).isInstanceOf(IllegalArgumentException.class);

        // Then
        assertThat(jdbcQueueClient.queueLag(queue, null)).isZero();
    }

    @Test
    public void shouldRollBackTheOuterTransactionWhenAJoinedStatementFails() {
        // Given
        String queue = IdUtils.create();
        JooqDSLContextWrapper wrapper = queueJdbcDataSourceProvider.wrapper();

        // When
        assertThatThrownBy(() -> wrapper.transaction(configuration ->
        {
            try {
                jdbcQueueClient.publish(queue, null, "key", "{}");
                wrapper.joinTransactionIfPresent(inner -> DSL.using(inner).fetch("select * from kestra_missing_table"));
            } catch (DataAccessException ignored) {
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        })).isInstanceOf(UnexpectedRollbackException.class);

        // Then
        assertThat(jdbcQueueClient.queueLag(queue, null)).isZero();
    }
}
