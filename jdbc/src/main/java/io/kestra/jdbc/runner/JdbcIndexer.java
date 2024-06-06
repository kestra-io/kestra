package io.kestra.jdbc.runner;

import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.IndexerInterface;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Replaces(Indexer.class)
@JdbcRunnerEnabled
@Slf4j
public class JdbcIndexer implements IndexerInterface {
    public void run() {
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public ServiceType getType() {
        return null;
    }

    @Override
    public ServiceState getState() {
        return ServiceState.RUNNING;
    }

    @Override
    public void close() {

    }
}
