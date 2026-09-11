package io.kestra.repository.h2;

import io.kestra.jdbc.repository.AbstractTaskRunStatisticsCompactorTest;
import io.micronaut.context.annotation.Property;

@Property(name = "kestra.task-run-statistics.enabled", value = "true")
public class H2TaskRunStatisticsCompactorTest extends AbstractTaskRunStatisticsCompactorTest {
}