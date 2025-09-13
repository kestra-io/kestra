package io.kestra.jdbc.runner;

import io.kestra.core.runners.ScheduleContextInterface;
import io.kestra.jdbc.JooqDSLContextWrapper;
import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.function.Consumer;

@Getter
public class JdbcSchedulerContext implements ScheduleContextInterface {

    private DSLContext context;
    private final JooqDSLContextWrapper dslContextWrapper;

    public JdbcSchedulerContext(JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }

    @Override
    public void doInTransaction(Consumer<ScheduleContextInterface> consumer) {
        this.dslContextWrapper.transaction(configuration -> {
            this.context = DSL.using(configuration);

            consumer.accept(this);

            this.context.commit();
        });
    }
}