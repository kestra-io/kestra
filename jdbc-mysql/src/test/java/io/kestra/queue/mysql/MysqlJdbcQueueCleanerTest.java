package io.kestra.queue.mysql;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.queues.QueueException;
import io.kestra.jdbc.queue.AbstractJdbcQueueCleanerTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest(rebuildContext = true)
@Property(name = "kestra.jdbc.queue.cleaner.retention", value = "PT0S")
public class MysqlJdbcQueueCleanerTest extends AbstractJdbcQueueCleanerTest {
    @Test
    @Override
    @FlakyTest(description = "Zero-retention queue cleanup races with DB transaction commit timing")
    protected void shouldClean() throws QueueException, InterruptedException {
        super.shouldClean();
    }
}
