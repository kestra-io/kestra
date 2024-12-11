package io.kestra.core.schedulers;

import java.util.function.Consumer;

// For tests purpose
public class DefaultScheduleContext implements ScheduleContextInterface {
    @Override
    public void doInTransaction(Consumer<ScheduleContextInterface> consumer) {
        consumer.accept(this);
    }
}
