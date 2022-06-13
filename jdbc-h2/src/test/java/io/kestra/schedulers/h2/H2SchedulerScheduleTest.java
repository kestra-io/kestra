package io.kestra.schedulers.h2;

import io.kestra.core.runners.FlowListeners;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.SchedulerExecutionStateInterface;
import io.kestra.core.schedulers.SchedulerScheduleTest;
import io.kestra.jdbc.runner.JdbcScheduler;

class H2SchedulerScheduleTest extends SchedulerScheduleTest {
    @Override
    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy, SchedulerExecutionStateInterface executionStateSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }
}
