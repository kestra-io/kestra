package io.kestra.jdbc.runner;

import io.kestra.jdbc.JooqDSLContextWrapper;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

@Singleton
@JdbcRunnerEnabled
public record JdbcQueueDependencies(JooqDSLContextWrapper jooqDSLContextWrapper) {
}
