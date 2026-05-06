package io.kestra.repository.postgres.migration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresMigrationHistoryStoreTest {

    @Mock
    private Statement stmt;

    @Mock
    private ResultSet rs;

    @Test
    void shouldResolveSimpleSchemaName() throws SQLException {
        // Given
        mockSearchPath("kestra_unit_webserver");

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isEqualTo("kestra_unit_webserver");
    }

    @Test
    void shouldResolveQuotedSchemaName() throws SQLException {
        // Given - JDBC driver's setSchema() wraps the schema name in double quotes
        mockSearchPath("\"kestra_unit_webserver\"");

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isEqualTo("kestra_unit_webserver");
    }

    @Test
    void shouldSkipDollarUserAndReturnPublic() throws SQLException {
        // Given - default PostgreSQL search_path
        mockSearchPath("\"$user\", public");

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isEqualTo("public");
    }

    @Test
    void shouldSkipPgCatalog() throws SQLException {
        // Given
        mockSearchPath("pg_catalog, pg_temp, my_schema");

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isEqualTo("my_schema");
    }

    @Test
    void shouldReturnNullWhenOnlySystemSchemas() throws SQLException {
        // Given - search_path with only system/special entries
        mockSearchPath("\"$user\", pg_catalog");

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenSearchPathIsEmpty() throws SQLException {
        // Given
        mockSearchPath("");

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenNoResultFromShowSearchPath() throws SQLException {
        // Given
        when(stmt.executeQuery("SHOW search_path")).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldCallCreateSchemaWithQuotedIdentifier() throws SQLException {
        // Given
        mockSearchPath("\"my_schema\"");
        var connection = org.mockito.Mockito.mock(java.sql.Connection.class);

        // When - create the store with a mock DataSource, then call prepareDatabase
        var dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        var store = new PostgresMigrationHistoryStore(dataSource);
        store.prepareDatabase(connection, stmt);

        // Then - schema name should be properly quoted in the DDL
        verify(stmt).execute("CREATE SCHEMA IF NOT EXISTS \"my_schema\"");
    }

    @Test
    void shouldEscapeDoubleQuotesInSchemaName() throws SQLException {
        // Given - pathological schema name containing double quotes
        mockSearchPath("\"has\"\"quotes\"");

        // When
        String result = PostgresMigrationHistoryStore.resolveSchemaFromSearchPath(stmt);

        // Then
        assertThat(result).isEqualTo("has\"quotes");
    }

    private void mockSearchPath(String searchPath) throws SQLException {
        when(stmt.executeQuery("SHOW search_path")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn(searchPath);
    }
}
