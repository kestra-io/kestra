package io.kestra.jdbc;

import java.time.Instant;

public record JdbcQueueItem(
    Long offset,
    Integer type,
    String routingKey,
    String key,
    String value,
    Instant created) {
}
