package io.kestra.runner.postgres;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresContainerSetup implements LauncherSessionListener {

    private static final Logger log = LoggerFactory.getLogger(PostgresContainerSetup.class);

    // Static so the container is shared across all test classes in the same JVM.
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:14.13")
            .withDatabaseName("kestra_unit")
            .withUsername("kestra")
            .withPassword("k3str4");

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        // System properties win over application-test.yml in Micronaut's priority chain.
        System.setProperty("datasources.postgres.url",      POSTGRES.getJdbcUrl());
        System.setProperty("datasources.postgres.username", POSTGRES.getUsername());
        System.setProperty("datasources.postgres.password", POSTGRES.getPassword());
        log.info("[PostgresContainerSetup] Container started → {}", POSTGRES.getJdbcUrl());
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        // Testcontainers JVM shutdown hook stops the container; no explicit stop needed.
    }
}
