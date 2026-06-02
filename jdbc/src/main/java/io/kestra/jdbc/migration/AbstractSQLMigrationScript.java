package io.kestra.jdbc.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;

/**
 * Base class for migration scripts, providing utilities for executing SQL resources from the
 * classpath.
 *
 * <p>
 * Subclasses are Micronaut {@code @Singleton} beans that use constructor injection.
 */
public abstract class AbstractSQLMigrationScript implements MigrationScript {

    /**
     * Loads a SQL file from the classpath and executes all statements against the given
     * {@link DataSource}.
     *
     * <p>
     * The SQL is split into individual statements using a parser that correctly handles:
     * <ul>
     * <li>Single-quoted string literals ({@code '...'})</li>
     * <li>PostgreSQL dollar-quoted blocks ({@code $$...$$}, {@code $tag$...$tag$})</li>
     * <li>Single-line comments ({@code --})</li>
     * <li>Block comments ({@code /* ... *\/})</li>
     * </ul>
     *
     * @param dataSource the data source to obtain a connection from
     * @param resourcePath classpath resource path to the SQL file (e.g.
     *        {@code "/migrations/baseline-h2.sql"})
     * @throws IOException if the resource cannot be read
     * @throws SQLException if a statement fails to execute
     */
    protected void executeSqlResource(final DataSource dataSource, final String resourcePath)
        throws IOException, SQLException {
        executeSqlScript(dataSource, resourcePath);
    }

    /**
     * Loads a SQL file from the classpath and executes all statements against the given
     * {@link DataSource}.
     *
     * <p>
     * The SQL is split into individual statements using a parser that correctly handles:
     * <ul>
     * <li>Single-quoted string literals ({@code '...'})</li>
     * <li>PostgreSQL dollar-quoted blocks ({@code $$...$$}, {@code $tag$...$tag$})</li>
     * <li>Single-line comments ({@code --})</li>
     * <li>Block comments ({@code /* ... *\/})</li>
     * </ul>
     *
     * <p>
     * Also available as a static method for classes that extend
     * {@link io.kestra.core.migration.AbstractV2_0_01UpgradeMigration} instead of this class.
     *
     * @param dataSource the data source to obtain a connection from
     * @param resourcePath classpath resource path to the SQL file (e.g.
     *        {@code "/migrations/baseline-h2.sql"})
     * @throws IOException if the resource cannot be read
     * @throws SQLException if a statement fails to execute
     */
    public static void executeSqlScript(final DataSource dataSource, final String resourcePath)
        throws IOException, SQLException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = AbstractSQLMigrationScript.class.getClassLoader();
        }
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream is = cl.getResourceAsStream(normalizedPath)) {
            if (is == null) {
                throw new IllegalArgumentException("SQL resource not found on classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // Unwrap any Micronaut Data AOP proxy so getConnection() works without a @Connectable context.
            DataSource raw = DelegatingDataSource.unwrapDataSource(dataSource);
            try (Connection connection = raw.getConnection()) {
                connection.setAutoCommit(true);
                for (String statement : splitStatements(sql)) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(statement);
                    }
                }
            }
        }
    }

    /**
     * Splits a multi-statement SQL string into individual statements, correctly handling
     * quoted strings, dollar-quote blocks, and comments.
     *
     * @param sql the full SQL content
     * @return a list of individual SQL statements (without the trailing {@code ;})
     */
    static List<String> splitStatements(final String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int length = sql.length();
        int i = 0;

        boolean inSingleQuote = false;
        boolean inDollarQuote = false;
        String dollarTag = null;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        while (i < length) {
            char c = sql.charAt(i);

            // --- Line comment ---
            if (
                !inSingleQuote && !inDollarQuote && !inBlockComment
                    && c == '-' && i + 1 < length && sql.charAt(i + 1) == '-'
            ) {
                inLineComment = true;
                current.append(c);
                i++;
                continue;
            }
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                current.append(c);
                i++;
                continue;
            }

            // --- Block comment ---
            if (
                !inSingleQuote && !inDollarQuote
                    && c == '/' && i + 1 < length && sql.charAt(i + 1) == '*'
            ) {
                inBlockComment = true;
                current.append(c);
                i++;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < length && sql.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    current.append(c);
                    current.append('/');
                    i += 2;
                    continue;
                }
                current.append(c);
                i++;
                continue;
            }

            // --- Dollar quote (PostgreSQL) ---
            if (!inSingleQuote && c == '$') {
                int tagEnd = sql.indexOf('$', i + 1);
                if (tagEnd != -1) {
                    String tag = sql.substring(i, tagEnd + 1);
                    // Dollar tags contain only letters, digits, underscores
                    if (isValidDollarTag(tag)) {
                        if (!inDollarQuote) {
                            inDollarQuote = true;
                            dollarTag = tag;
                            current.append(tag);
                            i = tagEnd + 1;
                            continue;
                        } else if (tag.equals(dollarTag)) {
                            inDollarQuote = false;
                            dollarTag = null;
                            current.append(tag);
                            i = tagEnd + 1;
                            continue;
                        }
                    }
                }
            }

            // --- Single quote ---
            if (!inDollarQuote && c == '\'') {
                // Handle escaped quote ('')
                if (inSingleQuote && i + 1 < length && sql.charAt(i + 1) == '\'') {
                    current.append(c);
                    current.append('\'');
                    i += 2;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                current.append(c);
                i++;
                continue;
            }

            // --- Statement delimiter ---
            if (!inSingleQuote && !inDollarQuote && c == ';') {
                String statement = current.toString().strip();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current = new StringBuilder();
                i++;
                continue;
            }

            current.append(c);
            i++;
        }

        // Handle any trailing statement without a terminator
        String last = current.toString().strip();
        if (!last.isEmpty()) {
            statements.add(last);
        }

        return statements;
    }

    private static boolean isValidDollarTag(final String tag) {
        if (tag.length() < 2 || tag.charAt(0) != '$' || tag.charAt(tag.length() - 1) != '$') {
            return false;
        }
        String inner = tag.substring(1, tag.length() - 1);
        for (char c : inner.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
