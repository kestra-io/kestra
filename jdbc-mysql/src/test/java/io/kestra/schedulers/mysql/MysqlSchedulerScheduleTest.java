package io.kestra.schedulers.mysql;

import io.kestra.core.runners.FlowListeners;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.scheduler.SchedulerExecutionStateInterface;
import io.kestra.scheduler.SchedulerScheduleTest;

class MysqlSchedulerScheduleTest extends SchedulerScheduleTest {
    @Override
    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy, SchedulerExecutionStateInterface executionStateSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }
}
