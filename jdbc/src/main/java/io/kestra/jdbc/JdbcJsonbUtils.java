package io.kestra.jdbc;

import org.jooq.JSONB;

/**
 * Strips PostgreSQL-incompatible null bytes from JSONB payloads.
 */
public final class JdbcJsonbUtils {
    private JdbcJsonbUtils() {}

    public static JSONB valueOf(String json) {
        return json == null ? null : JSONB.valueOf(json.replace("\u0000", ""));
    }
}
