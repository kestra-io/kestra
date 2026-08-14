package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * H2 migration: stop the {@code triggers} date columns from depending on the stored
 * {@code Instant} being exactly 27 characters wide.
 *
 * <p>
 * {@code LEFT(value, 26)} drops the trailing {@code Z} only when the fraction is exactly six
 * digits. At any other width the {@code Z} survives, H2 reads the value as a zoned timestamp and
 * shifts it into the session timezone — silently wrong on any server not running in UTC. No
 * current writer produces another width, so this is hardening rather than a live bug; removing the
 * {@code Z} explicitly is correct for every fraction width.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_23FixTriggerDateColumnsMigration extends AbstractSQLMigrationScript {

    private final DataSource dataSource;

    @Inject
    public V2_0_23FixTriggerDateColumnsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return "2.0.23-fix-trigger-date-columns";
    }

    @Override
    public String description() {
        return "H2: fix timezone shift in triggers next_evaluation_date and last_triggered_date";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.23-fix-trigger-date-columns-h2.sql");
    }
}
