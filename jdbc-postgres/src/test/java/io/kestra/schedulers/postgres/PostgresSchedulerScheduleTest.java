package io.kestra.schedulers.postgres;

import io.kestra.core.runners.FlowListeners;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.SchedulerScheduleTest;
import io.kestra.jdbc.runner.JdbcScheduler;

class PostgresSchedulerScheduleTest extends SchedulerScheduleTest {
    @Override
    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }
}
