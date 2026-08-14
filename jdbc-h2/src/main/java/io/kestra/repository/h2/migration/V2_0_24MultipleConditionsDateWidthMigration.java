package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * H2 migration: make {@code multipleconditions.start_date} / {@code end_date} tolerate any
 * fractional-second width.
 *
 * <p>
 * {@code PARSEDATETIME(..., 'SSSXXX')} demands exactly three fractional digits and fails on any
 * other width. The {@code ZonedDateTime} serializer now uses {@code JacksonMapper} and
 * aligns with {@code Instant} at six digits, so the fraction is normalized before parsing and the
 * {@code 'Z'}/offset suffix is left for {@code XXX} to handle.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_24MultipleConditionsDateWidthMigration extends AbstractSQLMigrationScript {

    private final DataSource dataSource;

    @Inject
    public V2_0_24MultipleConditionsDateWidthMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return "2.0.24-multipleconditions-date-width";
    }

    @Override
    public String description() {
        return "H2: accept any fractional-second width in multipleconditions date columns";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.24-multipleconditions-date-width-h2.sql");
    }
}
