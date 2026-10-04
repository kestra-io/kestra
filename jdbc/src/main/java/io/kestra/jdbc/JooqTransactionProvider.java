package io.kestra.jdbc;

import org.jooq.TransactionContext;

import io.micronaut.configuration.jooq.MicronautTransactionProvider;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;

@EachBean(DataSourceTransactionManager.class)
@Replaces(MicronautTransactionProvider.class)
public class JooqTransactionProvider extends MicronautTransactionProvider {
    public JooqTransactionProvider(DataSourceTransactionManager transactionManager) {
        super(transactionManager);
    }

    @Override
    public void rollback(TransactionContext context) {
        // jOOQ also rolls back a failed begin, before Micronaut has created a transaction (#7396).
        if (context.transaction() != null) {
            super.rollback(context);
        }
    }
}
