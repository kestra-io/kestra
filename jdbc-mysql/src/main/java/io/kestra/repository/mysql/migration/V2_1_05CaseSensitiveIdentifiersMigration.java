package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration moving the identifier columns of {@code flows}, {@code triggers} and {@code kv_metadata} to a case-sensitive collation.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_1_05CaseSensitiveIdentifiersMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.05-case-sensitive-identifiers";

    private final DataSource dataSource;

    @Inject
    public V2_1_05CaseSensitiveIdentifiersMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL: make the flows, triggers and kv_metadata identifier columns case-sensitive";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.05-case-sensitive-identifiers-mysql.sql");
    }
}
