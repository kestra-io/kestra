package io.kestra.schedulers.mysql;

import io.kestra.core.runners.FlowListeners;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.SchedulerScheduleTest;
import io.kestra.jdbc.runner.JdbcScheduler;

class MysqlSchedulerScheduleTest extends SchedulerScheduleTest {
    @Override
    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }
}
