package io.kestra.runner.mysql;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Starts a shared MySQL container once per test JVM, then publishes its
 * connection coordinates as system properties so Micronaut's property resolution
 * (which runs later, in each test class's {@code beforeAll}) picks them up and
 * overrides the static {@code localhost:3306} values in {@code application-test.yml}.
 *
 * <p>Registered via {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}.
 * With Develocity test distribution each remote agent gets its own container instance.</p>
 *
 * <p>When Docker is not reachable, the listener skips container startup and logs a warning.
 * Tests will then fall back to the connection coordinates in {@code application-test.yml},
 * which requires an externally-managed database (e.g. via {@code docker-compose-ci.yml}).</p>
 */
public class MysqlContainerSetup implements LauncherSessionListener {

    private static final Logger log = LoggerFactory.getLogger(MysqlContainerSetup.class);

    // Static so the container is shared across all test classes in the same JVM.
    private static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("kestra_unit")
            .withUsername("kestra")
            .withPassword("k3str4")
            // Mirror the flags used by docker-compose-ci.yml:
            // - log-bin-trust-function-creators: required for stored-function creation (V1_1 migration)
            // - innodb_ft_min_token_size / ft_min_word_len: fulltext index minimum word length (V1_13 migration)
            // - sort-buffer-size: prevents OOM on large sorts during migration
            // - max_connections: parallel test classes each open a HikariCP pool; default 151 is not enough
            .withCommand(
                "--log-bin-trust-function-creators=1",
                "--innodb_ft_min_token_size=1",
                "--ft_min_word_len=1",
                "--sort-buffer-size=10485760",
                "--max_connections=500"
            );

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (!MYSQL.isRunning()) {
            MYSQL.start();
        }
        // System properties win over application-test.yml in Micronaut's priority chain.
        System.setProperty("datasources.mysql.url",      MYSQL.getJdbcUrl());
        System.setProperty("datasources.mysql.username", MYSQL.getUsername());
        System.setProperty("datasources.mysql.password", MYSQL.getPassword());
        log.info("[MysqlContainerSetup] Container started → {}", MYSQL.getJdbcUrl());
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        // Testcontainers JVM shutdown hook stops the container; no explicit stop needed.
    }
}
