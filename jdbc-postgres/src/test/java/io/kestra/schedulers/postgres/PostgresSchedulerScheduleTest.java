package io.kestra.schedulers.postgres;

import io.kestra.core.runners.FlowListeners;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.scheduler.SchedulerExecutionStateInterface;
import io.kestra.scheduler.SchedulerScheduleTest;

class PostgresSchedulerScheduleTest extends SchedulerScheduleTest {
    @Override
    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy, SchedulerExecutionStateInterface executionStateSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }
}
