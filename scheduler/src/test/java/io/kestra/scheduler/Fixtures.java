package io.kestra.scheduler;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.plugin.core.trigger.ScheduleOnDates;

public interface Fixtures {

    String TEST_TENANT = "tenant";
    String TEST_NAMESPACE = "io.kestra.unittest";
    String TEST_FLOW_ID = "test";
    String TEST_TRIGGER_ID = "trigger";

    static TriggerId triggerId() {
        return triggerId(TEST_TRIGGER_ID);
    }

    static TriggerId triggerId(String triggerId) {
        return TriggerId.of(TEST_TENANT, TEST_NAMESPACE, TEST_FLOW_ID, triggerId);
    }

    static FlowWithSource defaultFlow() {
        return defaultFlow(Schedule.ScheduleBuilder::build);
    }

    static FlowWithSource defaultFlow(Function<Schedule.ScheduleBuilder<?, ?>, Schedule> builder) {
        return flowWithSchedulePT15M("Europe/Paris", builder);
    }

    static FlowWithSource flowWithTrigger(AbstractTrigger trigger) {
        return FlowWithSource.builder()
            .tenantId(TEST_TENANT)
            .id(TEST_FLOW_ID)
            .revision(0)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("return").type(Return.class.getName()).build()))
            .triggers(List.of(trigger))
            .build();
    }

    static FlowWithSource flowWithTrigger(AbstractTrigger trigger, String flowId) {
        return FlowWithSource.builder()
            .tenantId(TEST_TENANT)
            .id(flowId)
            .revision(0)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("return").type(Return.class.getName()).build()))
            .triggers(List.of(trigger))
            .build();
    }

    static FlowWithSource flowWithSchedulePT15M(String timeZone) {
        return flowWithSchedulePT15M(timeZone, Schedule.ScheduleBuilder::build);
    }

    static FlowWithSource flowWithScheduleOnDate(String timeZone, List<ZonedDateTime> dates) {
        return flowWithScheduleOnDate(timeZone, dates, ScheduleOnDates.ScheduleOnDatesBuilder::build);
    }

    static FlowWithSource flowWithScheduleOnDate(String timeZone, List<ZonedDateTime> dates, Function<ScheduleOnDates.ScheduleOnDatesBuilder<?, ?>, ScheduleOnDates> builder) {

        ScheduleOnDates.ScheduleOnDatesBuilder<?, ?> schedule = ScheduleOnDates.builder()
            .id(TEST_TRIGGER_ID)
            .type(ScheduleOnDates.class.getName())
            .dates(Property.ofValue(dates))
            .timezone(timeZone);

        return flowWithTrigger(builder.apply(schedule));
    }

    static FlowWithSource flowWithSchedulePT15M(String timeZone, Function<Schedule.ScheduleBuilder<?, ?>, Schedule> builder) {

        Schedule.ScheduleBuilder<?, ?> schedule = Schedule.builder()
            .id(TEST_TRIGGER_ID)
            .type(Schedule.class.getName())
            .cron("*/15 * * * *")
            .timezone(timeZone);

        return flowWithTrigger(builder.apply(schedule));
    }
}
