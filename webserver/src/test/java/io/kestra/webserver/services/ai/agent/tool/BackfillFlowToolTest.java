package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@KestraTest(startRunner = true, startScheduler = true)
class BackfillFlowToolTest {
    private static final String NAMESPACE = "io.kestra.tests.backfill";

    @Inject
    private BackfillFlowTool tool;

    @Inject
    private FlowService flowService;

    @Inject
    private TriggerRepositoryInterface triggerRepository;

    @Test
    void shouldBackfillTheOnlyScheduleTriggerOverARelativeWindowWhenNoTriggerIsGiven() throws FlowProcessingException, QueueException {
        // Given
        Flow flow = createFlow(List.of(schedule()));
        TriggerId triggerId = TriggerId.of(MAIN_TENANT, flow.getNamespace(), flow.getId(), flow.getTriggers().getFirst().getId());
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> triggerRepository.findByIdWithoutAcl(triggerId).isPresent());

        // When
        Map<String, Object> result = CatalogDispatcher.dispatch(
            tool, "backfill_flow", """
                { "namespace": "%s", "flowId": "%s", "start": "P1D", "labels": { "origin": "agent" } }""".formatted(flow.getNamespace(), flow.getId()), MAIN_TENANT
        );

        // Then
        assertThat(result).containsEntry("triggerId", triggerId.getTriggerId());
        assertThat(ZonedDateTime.parse((String) result.get("start"))).isCloseTo(ZonedDateTime.now().minusDays(1), within(1, ChronoUnit.MINUTES));
        assertThat(ZonedDateTime.parse((String) result.get("end"))).isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.MINUTES));
        TriggerState state = triggerRepository.findByIdWithoutAcl(triggerId).orElseThrow();
        assertThat(state.getBackfill()).isNotNull();
        assertThat(state.getBackfill().getLabels()).contains(new Label("origin", "agent"));
    }

    @Test
    void shouldRefuseBackfillWhenFlowHasSeveralScheduleTriggersAndNoneIsGiven() throws FlowProcessingException, QueueException {
        Flow flow = createFlow(List.of(schedule(), schedule()));

        assertThatThrownBy(
            () -> CatalogDispatcher.dispatch(
                tool, "backfill_flow", "{ \"namespace\": \"%s\", \"flowId\": \"%s\", \"start\": \"P1D\" }".formatted(flow.getNamespace(), flow.getId()), MAIN_TENANT
            )
        ).hasMessageContaining("has 2 schedule triggers").hasMessageContaining("pass the triggerId");
    }

    private Flow createFlow(final List<AbstractTrigger> triggers) throws FlowProcessingException, QueueException {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(MAIN_TENANT)
            .namespace(NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("ok")).build()))
            .triggers(triggers)
            .build();
        flowService.create(GenericFlow.of(flow));
        return flow;
    }

    private static Schedule schedule() {
        return Schedule.builder().id(IdUtils.create()).type(Schedule.class.getName()).cron("0 0 * * *").build();
    }
}
