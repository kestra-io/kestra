package io.kestra.jdbc.runner;

import org.jooq.Condition;

/**
 * This service is used solely by the {@link JdbcCleaner} to handle database-specific queries.
 */
public interface JdbcCleanerService {
    /**
     * Build the condition for the <code>types</code> column of the <code>queues</code> table.
     */
    Condition buildTypeCondition(String type);
}
