package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * MySQL migration: fix the UTC-offset extraction in the {@code multipleconditions} date columns.
 *
 * <p>
 * The offset written by the {@code .SSSXXX} serializer is six characters, but the extraction read
 * five of them, so {@code +05:30} was interpreted as {@code +05:03}. Only timezones with a non-zero
 * minutes part were affected; the rest survived because the dropped character was a {@code 0}.
 * {@code MultipleConditionWindow.start} / {@code end} are {@link java.time.ZonedDateTime}, so the
 * window lookup could match a window up to 41 minutes away from the intended one.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_23FixDatetimeOffsetMigration extends AbstractSQLMigrationScript {

    private final DataSource dataSource;

    @Inject
    public V2_0_23FixDatetimeOffsetMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return "2.0.23-fix-datetime-offset";
    }

    @Override
    public String description() {
        return "MySQL: fix UTC offset extraction in multipleconditions date columns";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.23-fix-datetime-offset-mysql.sql");
    }
}
