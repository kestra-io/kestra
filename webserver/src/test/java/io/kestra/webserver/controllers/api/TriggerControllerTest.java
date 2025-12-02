package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.webserver.controllers.api.TriggerController.SetDisabledRequest;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true, startScheduler = true)
class TriggerControllerTest {

    public static final String TENANT_ID = "main";
    public static final String TRIGGER_PATH = "/api/v1/main/triggers";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    AbstractJdbcFlowRepository jdbcFlowRepository;

    @Inject
    AbstractJdbcTriggerRepository jdbcTriggerRepository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    Scheduler scheduler;

    @Inject
    SchedulerConfiguration schedulerConfiguration;

    @BeforeEach
    protected void setup() throws TimeoutException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        Await.until(() -> scheduler.isActive(), Duration.ofMillis(100), Duration.ofSeconds(20));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindTriggersGivenQueryOnIdPrefix() {
        // GIVEN
        Flow flow = generateFlow();
        jdbcFlowRepository.create(GenericFlow.of(flow));
        createTriggersFromFlow(flow).forEach(jdbcTriggerRepository::save);

        // WHEN
        PagedResults<TriggerController.Triggers> triggers = client.toBlocking().retrieve(HttpRequest.GET(
            TRIGGER_PATH + "/search?filters[q][EQUALS]=trigger-nextexec"), Argument.of(PagedResults.class, TriggerController.Triggers.class));

        // THEN
        assertThat(triggers.getResults()).hasSize(2);
        assertThat(triggers.getResults().stream().map(TriggerController.Triggers::getTriggerContext).toList())
            .extracting(
                TriggerState::getTriggerId,
                TriggerState::getNamespace,
                TriggerState::getFlowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-polling", flow.getNamespace(), flow.getId()),
                tuple("trigger-nextexec-schedule", flow.getNamespace(), flow.getId())
            );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindTriggersGivenQueryOnNamespace() {
        // GIVEN
        Flow flow = generateFlow();
        jdbcFlowRepository.create(GenericFlow.of(flow));
        createTriggersFromFlow(flow).forEach(jdbcTriggerRepository::save);

        // WHEN
        PagedResults<TriggerController.Triggers> triggers = client.toBlocking().retrieve(HttpRequest.GET(
            TRIGGER_PATH + "/search?filters[q][EQUALS]=%s".formatted(flow.getNamespace())), Argument.of(PagedResults.class, TriggerController.Triggers.class));

        // THEN
        assertThat(triggers.getResults()).hasSize(2);
        assertThat(triggers.getResults().stream().map(TriggerController.Triggers::getTriggerContext).toList())
            .extracting(
                TriggerState::getTriggerId,
                TriggerState::getNamespace,
                TriggerState::getFlowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-polling", flow.getNamespace(), flow.getId()),
                tuple("trigger-nextexec-schedule", flow.getNamespace(), flow.getId())
            );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindTriggersGivenFilterOnNamespace() {
        // GIVEN
        Flow flow = generateFlow();
        jdbcFlowRepository.create(GenericFlow.of(flow));
        List<TriggerState> states = createTriggersFromFlow(flow);
        states.forEach(jdbcTriggerRepository::save);

        // WHEN
        PagedResults<TriggerController.Triggers> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(TRIGGER_PATH
                + "/search?filters[namespace][STARTS_WITH]=%s&sort=triggerId:asc".formatted(flow.getNamespace())),
            Argument.of(PagedResults.class, TriggerController.Triggers.class)
        );

        //THEN
        assertThat(triggers.getTotal()).isGreaterThanOrEqualTo(2L);
        assertThat(triggers.getResults().stream().map(TriggerController.Triggers::getTriggerContext).toList())
            .extracting(
                TriggerState::getTriggerId,
                TriggerState::getNamespace,
                TriggerState::getFlowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-polling", flow.getNamespace(), flow.getId()),
                tuple("trigger-nextexec-schedule", flow.getNamespace(), flow.getId())
            );
    }

    @Test
    void shouldGetSuccessWhenUnlockingTriggerGivenLocked() {
        // GIVEN
        TriggerState trigger = createTriggerWith(IdUtils.create(), IdUtils.create(), TENANT_ID).locked(Clock.systemDefaultZone(), true);
        jdbcTriggerRepository.save(trigger);

        // WHEN
        HttpResponse<Object> exchange = client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
            trigger.getNamespace(),
            trigger.getFlowId(),
            trigger.getTriggerId()
        ), null));

        // THEN
        assertThat(exchange.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
    }

    @Test
    void shouldGetConflictWhenUnlockingTriggerGivenUnlocked() {
        // GIVEN
        TriggerState trigger = createTriggerWith(IdUtils.create(), IdUtils.create(), TENANT_ID).locked(Clock.systemDefaultZone(), false);
        jdbcTriggerRepository.save(trigger);

        // WHEN
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
                trigger.getNamespace(),
                trigger.getFlowId(),
                trigger.getTriggerId()
            ), null)));

        // THEN
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(e.getMessage()).isEqualTo("Conflict: trigger [tenant=%s, namespace=%s, flow=%s, trigger=%s] is already unlocked"
            .formatted(trigger.getTenantId(), trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId()));
    }

    @Test
    void shouldGetNotFoundWhenUnlockingTriggerGivenUnlocked() {
        // WHEN
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
                "???",
                "???",
                "???"
            ), null)));
        // THEN
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(e.getMessage()).isEqualTo("Not Found");
    }

    @Test
    void shouldGetNoContentWhenRestartingTriggerGivenExist() {
        // GIVEN
        Flow flow = generateFlow();
        jdbcFlowRepository.create(GenericFlow.of(flow));

        TriggerState trigger = TriggerState.builder()
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .tenantId(TENANT_ID)
            .triggerId("trigger-to-restart")
            .locked(true)
            .disabled(true)
            .build();

        jdbcTriggerRepository.create(trigger);

        // WHEN
        HttpResponse<?> restarted = client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH
            + "/%s/%s/%s/restart".formatted(flow.getNamespace(), flow.getId(), trigger.getTriggerId())), null));

        // THEN
        assertThat(restarted.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
    }

    @Test
    void shouldGetNotFoundWhenRestartingTriggerGivenNotExist() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().
            exchange(HttpRequest.POST((TRIGGER_PATH + "/???/???/???/restart"), null)));

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldBulkDeleteTriggersByIds() {
        // GIVEN
        TriggerState trigger1 = jdbcTriggerRepository.save(newRandomTriggerState());
        TriggerState trigger2 = jdbcTriggerRepository.save(newRandomTriggerState());

        List<TriggerController.ApiTriggerId> triggers = Stream.of(trigger1, trigger2)
            .map(it -> new TriggerController.ApiTriggerId(it.getNamespace(), it.getFlowId(), it.getTriggerId()))
            .toList();

        // WHEN
        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.DELETE(
            TRIGGER_PATH + "/delete/by-triggers", triggers), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(2);
    }

    @Test
    void shouldDeleteTriggerById() {
        // GIVEN
        TriggerState state = newRandomTriggerState();
        jdbcTriggerRepository.save(state);

        // WHEN
        HttpResponse<Void> response = client.toBlocking()
            .exchange(
                HttpRequest.DELETE(TRIGGER_PATH + "/" + state.getNamespace() + "/" + state.getFlowId() + "/" + state.getTriggerId()),
                Void.class
            );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
        assertThat(jdbcTriggerRepository.findById(state)).isEmpty();
    }

    @Test
    void shouldUnlockTriggerByIdsGivenLocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), true);

        jdbcTriggerRepository.save(state);

        // WHEN
        List<TriggerController.ApiTriggerId> triggers = List.of(new TriggerController.ApiTriggerId(state.getNamespace(), state.getFlowId(), state.getTriggerId()));
        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(TRIGGER_PATH + "/unlock/by-triggers", triggers), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(1);
    }

    @Test
    void shouldNotUnlockTriggerByIdsGivenUnlocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), false);
        jdbcTriggerRepository.save(state);

        // WHEN
        List<TriggerController.ApiTriggerId> triggers = List.of(new TriggerController.ApiTriggerId(state.getNamespace(), state.getFlowId(), state.getTriggerId()));
        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/unlock/by-triggers", triggers), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(0);
    }

    @Test
    void shouldUnlockTriggerByQueryGivenLocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), true);

        jdbcTriggerRepository.save(state);

        // WHEN
        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/unlock/by-query?namespace=" + state.getNamespace(), null), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(1);
    }

    @Test
    void shouldNotUnlockTriggerByQueryGivenUnlocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), false);

        jdbcTriggerRepository.save(state);

        // WHEN
        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/unlock/by-query?namespace=" + state.getNamespace(), null), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(0);
    }

    @Test
    void shouldSetDisabledByTriggerIdsGivenFalse() {
        // GIVEN
        String namespace = IdUtils.create();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);

        jdbcFlowRepository.create(GenericFlow.of(flow1));
        jdbcFlowRepository.create(GenericFlow.of(flow2));

        TriggerState triggerDisabled = jdbcTriggerRepository.save(createTriggerFromFlow(flow1, true));
        TriggerState triggerNotDisabled = jdbcTriggerRepository.save(createTriggerFromFlow(flow2, false));

        List<TriggerController.ApiTriggerId> triggers = Stream.of(triggerDisabled, triggerNotDisabled)
            .map(it -> new TriggerController.ApiTriggerId(it.getNamespace(), it.getFlowId(), it.getTriggerId()))
            .toList();

        // WHEN
        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, false)), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(2);
        try {
            Await.until(() -> !jdbcTriggerRepository.findById(triggerDisabled).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be disabled");
        }
    }

    @Test
    void shouldSetDisabledByTriggerIdsGivenTrue() {
        // GIVEN
        String namespace = IdUtils.create();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);

        jdbcFlowRepository.create(GenericFlow.of(flow1));
        jdbcFlowRepository.create(GenericFlow.of(flow2));

        TriggerState triggerDisabled = jdbcTriggerRepository.save(createTriggerFromFlow(flow1, true));
        TriggerState triggerToDisable = jdbcTriggerRepository.save(createTriggerFromFlow(flow2, false));

        // WHEN
        List<TriggerController.ApiTriggerId> triggers = Stream.of(triggerDisabled, triggerToDisable)
            .map(it -> new TriggerController.ApiTriggerId(it.getNamespace(), it.getFlowId(), it.getTriggerId()))
            .toList();

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, true)), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(2);

        try {
            Await.until(() -> jdbcTriggerRepository.findById(triggerToDisable).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(10));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be disabled");
        }
    }

    @Test
    void shouldSetDisabledByQueryGivenTrue() {
        // GIVEN
        String namespace = IdUtils.create();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);

        jdbcFlowRepository.create(GenericFlow.of(flow1));
        jdbcFlowRepository.create(GenericFlow.of(flow2));

        jdbcTriggerRepository.save(createTriggerFromFlow(flow1, true));
        TriggerState toDisable = jdbcTriggerRepository.save(createTriggerFromFlow(flow2, false));

        // WHEN
        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/set-disabled/by-query?namespace=%s&disabled=true".formatted(namespace), null), BulkResponse.class);

        // THEN
        assertThat(bulkResponse.getCount()).isEqualTo(2);
        try {
            Await.until(() -> jdbcTriggerRepository.findById(toDisable).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be disabled");
        }
    }

    @Test
    void disableByTriggersBadRequest() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.POST(
                    TRIGGER_PATH + "/set-disabled/by-triggers", new SetDisabledRequest(null, null)),
                BulkResponse.class));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    private Flow generateFlow() {
        return Flow.builder()
            .tenantId(TENANT_ID)
            .namespace(IdUtils.create())
            .id(IdUtils.create())
            .tasks(Collections.singletonList(Return.builder()
                .id("task")
                .type(Return.class.getName())
                .format(Property.ofValue("return data"))
                .build()))
            .triggers(List.of(
                Schedule.builder()
                    .id("trigger-nextexec-schedule")
                    .type(Schedule.class.getName())
                    .cron("*/1 * * * *")
                    .build(),
                PollingTrigger.builder()
                    .id("trigger-nextexec-polling")
                    .type(PollingTrigger.class.getName())
                    .build()
            ))
            .build();
    }

    private TriggerState newRandomTriggerState() {
        String random = IdUtils.create();
        return TriggerState.builder()
            .tenantId(TENANT_ID)
            .namespace(random)
            .flowId(random)
            .triggerId(random)
            .vnode(VNodes.computeVNodeFromFlow(FlowId.of(TENANT_ID, random, random, null), schedulerConfiguration.vnodes()))
            .build();
    }

    private Flow generateFlowWithTrigger(String namespace) {
        return Flow.builder()
            .id(IdUtils.create())
            .tenantId(TENANT_ID)
            .namespace(namespace)
            .tasks(Collections.singletonList(Return.builder()
                .id("task")
                .type(Return.class.getName())
                .format(Property.ofValue("return data"))
                .build()))
            .triggers(List.of(Schedule.builder()
                .id(IdUtils.create())
                .type(Schedule.class.getName())
                .cron("*/1 * * * *")
                .build()
            ))
            .build();
    }

    private TriggerState createTriggerWith(String flow, String namespace, String triggerId) {
        return TriggerState.builder()
            .tenantId(TENANT_ID)
            .flowId(flow)
            .namespace(namespace)
            .triggerId(triggerId)
            .evaluatedAt(Instant.now())
            .vnode(VNodes.computeVNodeFromFlow(FlowId.of(TENANT_ID, namespace, flow, null), schedulerConfiguration.vnodes()))
            .build();
    }

    private List<TriggerState> createTriggersFromFlow(Flow flow) {
        return flow.getTriggers().stream().map(it ->
            TriggerState.builder()
                .flowId(flow.getId())
                .tenantId(flow.getTenantId())
                .namespace(flow.getNamespace())
                .triggerId(it.getId())
                .vnode(VNodes.computeVNodeFromFlow(flow, schedulerConfiguration.vnodes()))
                .build()
        ).toList();
    }

    private TriggerState createTriggerFromFlow(Flow flow, Boolean disabled) {
        return TriggerState.builder()
            .flowId(flow.getId())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .triggerId(flow.getTriggers().getFirst().getId())
            .disabled(disabled)
            .vnode(VNodes.computeVNodeFromFlow(flow, schedulerConfiguration.vnodes()))
            .build();
    }
}
