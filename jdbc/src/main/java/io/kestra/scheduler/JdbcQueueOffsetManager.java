package io.kestra.scheduler;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcQueueEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

@Singleton
@JdbcQueueEnabled
public class JdbcQueueOffsetManager {
    
    // Columns
    private static final Field<Long> OFFSET_FIELD = DSL.field(DSL.quotedName("offset"), Long.class);
    protected static final Field<Object> VNODE_FIELD = DSL.field(DSL.quotedName("vnode"));
    
    private final JooqDSLContextWrapper dslContextWrapper;
    
    @Inject
    public JdbcQueueOffsetManager(JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }
    
    public long fetchLastestOffset(final String queue) {
        return dslContextWrapper.transactionResult(configuration -> {
            DSLContext ctx = DSL.using(configuration);
            SelectJoinStep<Record1<Long>> select = ctx
                .select(DSL.coalesce(DSL.max(OFFSET_FIELD), DSL.inline(0L)))
                .from(DSL.table(queue));
            
            Long offset = select.fetchOne(0, Long.class);
            
            return offset != null ? offset : 0L;
        });
    }
    
    public long fetchEarliestOffset(final String queue, final Integer vNode) {
        return dslContextWrapper.transactionResult(configuration -> {
            DSLContext ctx = DSL.using(configuration);
            SelectConditionStep<Record1<Long>> select = ctx
                .select(DSL.coalesce(DSL.min(OFFSET_FIELD), DSL.inline(0L)))
                .from(DSL.table(queue))
                .where(VNODE_FIELD.eq(vNode));
            
            Long offset = select.fetchOne(0, Long.class);
            
            return offset != null ? offset : 0L;
        });
    }
}
