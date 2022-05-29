package io.kestra.jdbc.runner;

import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.IndexerInterface;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Singleton
@Replaces(Indexer.class)
@JdbcRunnerEnabled
@Slf4j
public class JdbcIndexer implements IndexerInterface {
    public void run() {
    }

    @Override
    public void close() throws IOException {

    }
}
