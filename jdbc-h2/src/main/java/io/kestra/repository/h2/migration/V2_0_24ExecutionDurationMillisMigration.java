package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * H2-only migration regenerating {@code executions.state_duration} in milliseconds.
 *
 * <p>
 * The baseline generated the column from the raw {@code .state.duration} JSON value, which is expressed in
 * seconds, while the Postgres and MySQL columns and every consumer of the column expect milliseconds. Dashboard
 * duration aggregations on H2 were therefore off by a factor of 1000.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_24ExecutionDurationMillisMigration extends AbstractSQLMigrationScript {

    private final DataSource dataSource;

    @Inject
    public V2_0_24ExecutionDurationMillisMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return "2.0.24-execution-duration-millis";
    }

    @Override
    public String description() {
        return "Executions: regenerate the H2 state_duration column in milliseconds";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.24-execution-duration-millis-h2.sql");
    }
}
