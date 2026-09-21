package io.kestra.core.repositories;

import java.util.Set;

/**
 * Field names that no repository may expose as a sort key, regardless of backend: {@code key}/{@code value} carry
 * the whole entity, {@code sourceCode}/{@code fulltext} are large text fields, and {@code deleted}/{@code tenantId}/
 * {@code revision} are internal bookkeeping. Shared so the JDBC and Elasticsearch backends stay in lockstep; each
 * backend compares case-insensitively, and the JDBC backend converts these to snake_case before comparing.
 */
public final class NonSortableFields {
    public static final Set<String> DEFAULT = Set.of(
        "key", "value", "fulltext", "sourceCode", "deleted", "tenantId", "revision"
    );

    private NonSortableFields() {
    }
}
