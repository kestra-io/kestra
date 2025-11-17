package io.kestra.jdbc;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor
@Getter
public class JdbcQueueItem {
    Long offset;
    Integer type;
    String routingKey;
    String key;
    String value;
    Instant created;
}
