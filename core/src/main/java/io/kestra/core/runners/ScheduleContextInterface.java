package io.kestra.core.runners;

import java.util.function.Consumer;

/**
 * This context is used by the Scheduler to allow evaluating and updating triggers in a transaction from the main evaluation loop.
 * See AbstractScheduler.handle().
 */
public interface ScheduleContextInterface {
    /**
     * Do trigger retrieval and updating in a single transaction.
     */
    void doInTransaction(Consumer<ScheduleContextInterface> consumer);
}
