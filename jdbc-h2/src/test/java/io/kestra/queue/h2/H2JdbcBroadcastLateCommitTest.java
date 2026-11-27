package io.kestra.queue.h2;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.jdbc.queue.AbstractJdbcBroadcastLateCommitTest;
import io.kestra.queue.AbstractBroadcastQueueTest.TestBroadcast;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest(environments = { "test", "queue" })
@Execution(ExecutionMode.SAME_THREAD)
class H2JdbcBroadcastLateCommitTest extends AbstractJdbcBroadcastLateCommitTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    private BroadcastQueueInterface<TestBroadcast> testQueue;

    @Inject
    private QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldDeliverWhenTheFirstOffsetOfTheTypeIsFarAboveTheSeed() throws Exception {
        queueJdbcDataSourceProvider.wrapper().transaction(
            configuration -> DSL.using(configuration).execute("ALTER TABLE queues ALTER COLUMN \"offset\" RESTART WITH 3000000000")
        );
        List<Integer> received = new CopyOnWriteArrayList<>();
        QueueSubscriber<TestBroadcast> subscriber = testQueue.subscriber().subscribe(either -> received.add(either.getLeft().id()));

        try {
            testQueue.emit(new TestBroadcast("far", 1));

            await().atMost(Duration.ofSeconds(10)).until(() -> received.contains(1));
            assertThat(subscriber.isActive()).isTrue();
        } finally {
            subscriber.close();
        }
    }
}
