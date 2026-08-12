package io.kestra.repository.mysql;

import io.kestra.jdbc.repository.AbstractTaskRunStatisticsCompactorTest;;
import io.micronaut.context.annotation.Property;

@Property(name = "kestra.task-run-statistics.enabled", value = "true")
public class MysqlTaskRunStatisticsCompactorTest extends AbstractTaskRunStatisticsCompactorTest {
}
