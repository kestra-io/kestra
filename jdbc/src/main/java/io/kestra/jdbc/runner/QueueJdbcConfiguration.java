package io.kestra.jdbc.runner;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.queue.jdbc")
public record QueueJdbcConfiguration(
    @Nullable String type,
    @Nullable String url,
    @Nullable String username,
    @Nullable String password,
    @Nullable String table) {
}
