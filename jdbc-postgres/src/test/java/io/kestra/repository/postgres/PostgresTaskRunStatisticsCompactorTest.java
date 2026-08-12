package io.kestra.repository.postgres;

import io.kestra.jdbc.repository.AbstractTaskRunStatisticsCompactorTest;
import io.micronaut.context.annotation.Property;

@Property(name = "kestra.task-run-statistics.enabled", value = "true")
public class PostgresTaskRunStatisticsCompactorTest extends AbstractTaskRunStatisticsCompactorTest {
}
