package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL namespace_file_metadata case-sensitivity migration script.
 *
 * <p>
 * Switches the {@code key}, {@code path} and {@code parent_path} columns of
 * {@code namespace_file_metadata} to the case-sensitive {@code utf8mb4_bin} collation. Namespace
 * file paths map 1:1 to case-sensitive storage URIs and the primary key {@code key} embeds the
 * path, so the default case-insensitive collation made files that differ only by case collide.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_15NamespaceFileMetadataCaseSensitiveMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.15-namespace-file-metadata-case-sensitive";
    private static final String RESOURCE = "/migrations/2.0.15-namespace-file-metadata-case-sensitive-mysql.sql";

    private final DataSource dataSource;

    @Inject
    public V2_0_15NamespaceFileMetadataCaseSensitiveMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL namespace_file_metadata: make key/path/parent_path case-sensitive";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources(RESOURCE);
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, RESOURCE);
    }
}
