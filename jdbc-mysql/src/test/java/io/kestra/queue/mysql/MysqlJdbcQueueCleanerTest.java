package io.kestra.queue.mysql;

import io.kestra.jdbc.queue.AbstractJdbcQueueCleanerTest;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest(rebuildContext = true)
@Property(name = "kestra.jdbc.queue.cleaner.retention", value = "PT0S")
public class MysqlJdbcQueueCleanerTest extends AbstractJdbcQueueCleanerTest {
}
