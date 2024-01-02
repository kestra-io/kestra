package io.kestra.jdbc.runner;

import io.kestra.core.schedulers.ScheduleContextInterface;
import lombok.Getter;
import org.jooq.DSLContext;

@Getter
public class JdbcSchedulerContext implements ScheduleContextInterface {

    private final DSLContext context;

    public JdbcSchedulerContext(DSLContext context) {
        this.context = context;
    }

    public void commit() {
        this.context.commits();
    }
}