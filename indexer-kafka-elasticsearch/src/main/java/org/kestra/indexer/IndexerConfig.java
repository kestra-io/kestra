package org.kestra.indexer;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigurationProperties("kestra.indexer")
@Getter
public class IndexerConfig {
    private final int batchSize = 500;
    private final Duration batchDuration = Duration.of(1, ChronoUnit.SECONDS);
}
