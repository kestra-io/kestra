package io.kestra.jdbc.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AbstractSQLMigrationScript#splitStatements(String)}.
 */
class AbstractSQLMigrationScriptTest {

    @Test
    void splitStatements_singleStatement() {
        String sql = "CREATE TABLE foo (id INT)";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).containsExactly("CREATE TABLE foo (id INT)");
    }

    @Test
    void splitStatements_multipleStatements() {
        String sql = "CREATE TABLE foo (id INT); CREATE TABLE bar (id INT);";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).containsExactly("CREATE TABLE foo (id INT)", "CREATE TABLE bar (id INT)");
    }

    @Test
    void splitStatements_semicolonInsideSingleQuote() {
        String sql = "INSERT INTO foo VALUES ('a;b'); INSERT INTO foo VALUES ('c');";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).containsExactly(
            "INSERT INTO foo VALUES ('a;b')",
            "INSERT INTO foo VALUES ('c')"
        );
    }

    @Test
    void splitStatements_escapedSingleQuote() {
        String sql = "INSERT INTO foo VALUES ('it''s a test');";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).containsExactly("INSERT INTO foo VALUES ('it''s a test')");
    }

    @Test
    void splitStatements_dollarQuotePostgres() {
        String sql = """
            CREATE FUNCTION test() RETURNS void AS $$
            BEGIN
              -- semicolon; inside
            END;
            $$ LANGUAGE plpgsql;
            """;
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("BEGIN");
    }

    @Test
    void splitStatements_namedDollarQuote() {
        String sql = """
            CREATE FUNCTION test() RETURNS void AS $body$
            BEGIN NULL; END;
            $body$ LANGUAGE plpgsql;
            """;
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).hasSize(1);
    }

    @Test
    void splitStatements_lineComment() {
        String sql = "-- this is a comment\nCREATE TABLE foo (id INT);";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("CREATE TABLE");
    }

    @Test
    void splitStatements_blockComment() {
        String sql = "/* create table */ CREATE TABLE foo (id INT);";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("CREATE TABLE");
    }

    @Test
    void splitStatements_trailingStatementWithoutSemicolon() {
        String sql = "CREATE TABLE foo (id INT);\nCREATE TABLE bar (id INT)";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).containsExactly("CREATE TABLE foo (id INT)", "CREATE TABLE bar (id INT)");
    }

    @Test
    void splitStatements_emptyInput() {
        assertThat(AbstractSQLMigrationScript.splitStatements("")).isEmpty();
        assertThat(AbstractSQLMigrationScript.splitStatements("   ")).isEmpty();
        assertThat(AbstractSQLMigrationScript.splitStatements("\n\n")).isEmpty();
    }

    @Test
    void splitStatements_onlyComments() {
        // Comments with no semicolon are treated as a single trailing statement
        // (the parser preserves comment text in the buffer rather than discarding it)
        String sql = "-- just a comment\n/* another */";
        List<String> result = AbstractSQLMigrationScript.splitStatements(sql);
        assertThat(result).hasSize(1);
    }
}
