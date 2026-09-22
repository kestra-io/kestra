package io.kestra.jdbc;

/**
 * The contract for a command that runs against a throwaway database instead of the configured one.
 * <p>
 * A command opting in sets {@link #URL_PROPERTY} to an in-memory H2 URL. Every JDBC datasource is
 * then repointed at that URL, and no pool is ever opened against the datasource the user configured
 * — see {@code io.kestra.runner.memory.EphemeralDatasourceRewriter} and
 * {@link LogJdbcDataSourceProvider}, the only two places in the codebase that build a connection
 * pool.
 */
public final class EphemeralDatabase {
    /**
     * The in-memory H2 URL to run against; absent unless the command opted in. Always include
     * {@code DB_CLOSE_DELAY=-1}, since H2 discards an in-memory database once the last connection
     * to it closes.
     */
    public static final String URL_PROPERTY = "kestra.ephemeral-database.url";

    /**
     * Whether {@link #URL_PROPERTY} opts this run in, so that everything deciding on it agrees on
     * what a blank value means.
     *
     * @param url the resolved {@link #URL_PROPERTY}, empty when unset.
     */
    public static boolean isEnabled(final String url) {
        return url != null && !url.isBlank();
    }

    private EphemeralDatabase() {
    }
}
