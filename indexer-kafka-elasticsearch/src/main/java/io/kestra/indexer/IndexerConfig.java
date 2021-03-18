package io.kestra.indexer;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ConfigurationProperties("kestra.indexer")
@Getter
public class IndexerConfig {
    Integer batchSize = 500;
    Duration batchDuration = Duration.of(1, ChronoUnit.SECONDS);
    List<Class<?>> models;
}
