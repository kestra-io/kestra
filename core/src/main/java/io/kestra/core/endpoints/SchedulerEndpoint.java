package io.kestra.core.endpoints;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.schedulers.AbstractScheduler;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Endpoint(id = "scheduler", defaultSensitive = false)
@Requires(property = "kestra.server-type", pattern = "(SCHEDULER|STANDALONE)")
public class SchedulerEndpoint {
    @Inject
    AbstractScheduler scheduler;

    @Read
    public SchedulerEndpointResult running() {
        Map<String, AbstractScheduler.FlowWithWorkerTriggerNextDate> schedulableNextDate = scheduler.getSchedulableNextDate();

        List<SchedulerEndpointSchedule> result = scheduler.schedulerTriggers()
            .stream()
            .map(flowWithTrigger -> {
                String uid = Trigger.uid(flowWithTrigger.getFlow(), flowWithTrigger.getAbstractTrigger());

                return new SchedulerEndpointSchedule(
                    flowWithTrigger.getFlow().getId(),
                    flowWithTrigger.getFlow().getNamespace(),
                    flowWithTrigger.getFlow().getRevision(),
                    flowWithTrigger.getAbstractTrigger(),
                    schedulableNextDate.containsKey(uid) ? schedulableNextDate.get(uid).getNext() : null
                );
            })
            .toList();

        return SchedulerEndpointResult.builder()
            .schedulableCount(result.size())
            .schedulable(result)
            .build();
    }

    @Getter
    @Builder
    public static class SchedulerEndpointResult {
        private final int schedulableCount;
        private final List<SchedulerEndpointSchedule> schedulable;
    }

    @Getter
    @AllArgsConstructor
    public static class SchedulerEndpointSchedule {
        private final String flowId;
        private final String namespace;
        private final Integer revision;
        private final AbstractTrigger trigger;
        private final ZonedDateTime next;
    }
}
