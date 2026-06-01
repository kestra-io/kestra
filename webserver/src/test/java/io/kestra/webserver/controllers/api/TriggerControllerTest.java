package io.kestra.webserver.controllers.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.webserver.models.api.ApiTriggerAndState;
import io.kestra.webserver.models.api.ApiTriggerState;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.core.services.FlowService;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.webserver.controllers.api.TriggerController.SetDisabledRequest;
import io.kestra.webserver.models.api.ApiAsyncOperationResponse;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

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
    FlowService flowService;

    @Inject
    AbstractJdbcTriggerRepository jdbcTriggerRepository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    Scheduler scheduler;

    @Inject
    SchedulerConfiguration schedulerConfiguration;

    @BeforeEach
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(() -> scheduler.isActive());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindTriggersGivenQueryOnIdPrefix() throws FlowProcessingException, QueueException {
        // GIVEN
        Flow flow = generateFlow();
        flowService.create(GenericFlow.of(flow));
        createTriggersFromFlow(flow).forEach(jdbcTriggerRepository::save);

        // WHEN
        PagedResults<ApiTriggerAndState> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(
                TRIGGER_PATH + "/search?filters[q][EQUALS]=trigger-nextexec"
            ), Argument.of(PagedResults.class, ApiTriggerAndState.class)
        );

        // THEN
        assertThat(triggers.getResults()).hasSize(2);
        assertThat(triggers.getResults().stream().map(ApiTriggerAndState::state).toList())
            .extracting(
                ApiTriggerState::triggerId,
                ApiTriggerState::namespace,
                ApiTriggerState::flowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-polling", flow.getNamespace(), flow.getId()),
                tuple("trigger-nextexec-schedule", flow.getNamespace(), flow.getId())
            );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindTriggersGivenQueryOnNamespace() throws FlowProcessingException, QueueException {
        // GIVEN
        Flow flow = generateFlow();
        flowService.create(GenericFlow.of(flow));
        createTriggersFromFlow(flow).forEach(jdbcTriggerRepository::save);

        // WHEN
        PagedResults<ApiTriggerAndState> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(
                TRIGGER_PATH + "/search?filters[q][EQUALS]=%s".formatted(flow.getNamespace())
            ), Argument.of(PagedResults.class, ApiTriggerAndState.class)
        );

        // THEN
        assertThat(triggers.getResults()).hasSize(2);
        assertThat(triggers.getResults().stream().map(ApiTriggerAndState::state).toList())
            .extracting(
                ApiTriggerState::triggerId,
                ApiTriggerState::namespace,
                ApiTriggerState::flowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-polling", flow.getNamespace(), flow.getId()),
                tuple("trigger-nextexec-schedule", flow.getNamespace(), flow.getId())
            );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindTriggersGivenFilterOnNamespace() throws FlowProcessingException, QueueException {
        // GIVEN
        Flow flow = generateFlow();
        flowService.create(GenericFlow.of(flow));
        List<TriggerState> states = createTriggersFromFlow(flow);
        states.forEach(jdbcTriggerRepository::save);

        // WHEN
        PagedResults<ApiTriggerAndState> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(
                TRIGGER_PATH
                    + "/search?filters[namespace][STARTS_WITH]=%s&sort=triggerId:asc".formatted(flow.getNamespace())
            ),
            Argument.of(PagedResults.class, ApiTriggerAndState.class)
        );

        //THEN
        assertThat(triggers.getTotal()).isGreaterThanOrEqualTo(2L);
        assertThat(triggers.getResults().stream().map(ApiTriggerAndState::state).toList())
            .extracting(
                ApiTriggerState::triggerId,
                ApiTriggerState::namespace,
                ApiTriggerState::flowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-polling", flow.getNamespace(), flow.getId()),
                tuple("trigger-nextexec-schedule", flow.getNamespace(), flow.getId())
            );
    }

    @Test
    void shouldUnlockTriggerWhenLocked() {
        // GIVEN
        TriggerState trigger = createTriggerWith(IdUtils.create(), IdUtils.create(), TENANT_ID).locked(Clock.systemDefaultZone(), true);
        jdbcTriggerRepository.save(trigger);

        // WHEN
        HttpResponse<ApiTriggerState> exchange = client.toBlocking().exchange(
            HttpRequest.POST(
                (TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
                    trigger.getNamespace(),
                    trigger.getFlowId(),
                    trigger.getTriggerId()
                ), null
            ),
            ApiTriggerState.class
        );

        // THEN
        assertThat(exchange.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(exchange.body()).isNotNull();
        assertThat(exchange.body().triggerId()).isEqualTo(trigger.getTriggerId());
    }

    @Test
    void shouldReturnConflictWhenUnlockingTriggerAlreadyUnlocked() {
        // GIVEN: use newRandomTriggerState (no evaluatedAt) so the scheduler does not pick it up as eligible
        // and delete it as an orphan before we query it.
        TriggerState trigger = newRandomTriggerState().locked(Clock.systemDefaultZone(), false);
        jdbcTriggerRepository.save(trigger);

        // WHEN
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    (TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
                        trigger.getNamespace(),
                        trigger.getFlowId(),
                        trigger.getTriggerId()
                    ), null
                )
            )
        );

        // THEN
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(e.getMessage()).isEqualTo(
            "Conflict: trigger [tenant=%s, namespace=%s, flow=%s, trigger=%s] is already unlocked"
                .formatted(trigger.getTenantId(), trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId())
        );
    }

    @Test
    void shouldReturnNotFoundWhenUnlockingMissingTrigger() {
        // WHEN
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    (TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
                        "???",
                        "???",
                        "???"
                    ), null
                )
            )
        );
        // THEN
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(e.getMessage()).isEqualTo("Not Found");
    }

    @Test
    void shouldRestartTriggerWhenExists() throws FlowProcessingException, QueueException {
        // GIVEN
        Flow flow = generateFlow();
        flowService.create(GenericFlow.of(flow));

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
        HttpResponse<ApiTriggerState> restarted = client.toBlocking().exchange(
            HttpRequest.POST(
                (TRIGGER_PATH
                    + "/%s/%s/%s/restart".formatted(flow.getNamespace(), flow.getId(), trigger.getTriggerId())),
                null
            ),
            ApiTriggerState.class
        );

        // THEN
        assertThat(restarted.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(restarted.body()).isNotNull();
        assertThat(restarted.body().triggerId()).isEqualTo(trigger.getTriggerId());
    }

    @Test
    void shouldReturnNotFoundWhenRestartingMissingTrigger() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH + "/???/???/???/restart"), null))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldAcceptDeleteTriggersByIdsWhenExist() {
        // GIVEN
        TriggerState trigger1 = jdbcTriggerRepository.save(newRandomTriggerState());
        TriggerState trigger2 = jdbcTriggerRepository.save(newRandomTriggerState());

        List<TriggerController.ApiTriggerId> triggers = Stream.of(trigger1, trigger2)
            .map(it -> new TriggerController.ApiTriggerId(it.getNamespace(), it.getFlowId(), it.getTriggerId()))
            .toList();

        // WHEN
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.DELETE(TRIGGER_PATH + "/delete/by-triggers", triggers),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body()).isNotNull();
        assertThat(response.body().operationId()).isNotBlank();
        assertThat(response.body().totalItems()).isEqualTo(2);
    }

    @Test
    void shouldDeleteTriggerWhenExists() throws FlowProcessingException, QueueException {
        // GIVEN
        Flow flow1 = generateFlowWithTrigger(IdUtils.create().toLowerCase());
        TriggerState state = createTriggerFromFlow(flow1, true);
        flowService.create(GenericFlow.of(flow1));

        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(() -> jdbcTriggerRepository.findById(state).isPresent());

        // WHEN
        HttpResponse<Void> response = client.toBlocking()
            .exchange(
                HttpRequest.DELETE(TRIGGER_PATH + "/" + state.getNamespace() + "/" + state.getFlowId() + "/" + state.getTriggerId()),
                Void.class
            );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(state).isEmpty());
    }

    @Test
    void shouldAcceptUnlockByIdsWhenLocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), true);

        jdbcTriggerRepository.save(state);

        // WHEN
        List<TriggerController.ApiTriggerId> triggers = List.of(new TriggerController.ApiTriggerId(state.getNamespace(), state.getFlowId(), state.getTriggerId()));
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/unlock/by-triggers", triggers),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body()).isNotNull();
        assertThat(response.body().operationId()).isNotBlank();
        assertThat(response.body().totalItems()).isEqualTo(1);
    }

    @Test
    void shouldAcceptUnlockByIdsWithZeroItemsWhenUnlocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), false);
        jdbcTriggerRepository.save(state);

        // WHEN
        List<TriggerController.ApiTriggerId> triggers = List.of(new TriggerController.ApiTriggerId(state.getNamespace(), state.getFlowId(), state.getTriggerId()));
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/unlock/by-triggers", triggers),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(0);
    }

    @Test
    void shouldAcceptUnlockByQueryWhenLocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), true);

        jdbcTriggerRepository.save(state);

        // WHEN
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/unlock/by-query?filters[namespace][EQUALS]=" + state.getNamespace(), null),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(1);
    }

    @Test
    void shouldAcceptUnlockByQueryWithZeroItemsWhenUnlocked() {
        // GIVEN
        TriggerState state = newRandomTriggerState().locked(Clock.systemDefaultZone(), false);

        jdbcTriggerRepository.save(state);

        // WHEN
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/unlock/by-query?filters[namespace][EQUALS]=" + state.getNamespace(), null),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(0);
    }

    @Test
    void shouldAcceptSetDisabledByIdsWhenFalse() throws FlowProcessingException, QueueException {
        // GIVEN
        String namespace = "ns-" + IdUtils.create().toLowerCase();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);

        flowService.create(GenericFlow.of(flow1));
        flowService.create(GenericFlow.of(flow2));

        final TriggerState triggerDisabled = createTriggerFromFlow(flow1, true);
        final TriggerState triggerNotDisabled = createTriggerFromFlow(flow2, false);
        // Wait for the scheduler to initialize trigger states before updating them
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(triggerDisabled).isPresent());
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(triggerNotDisabled).isPresent());

        List<TriggerController.ApiTriggerId> triggers = Stream.of(
            jdbcTriggerRepository.save(triggerDisabled),
                jdbcTriggerRepository.save(triggerNotDisabled)
            )
            .map(it -> new TriggerController.ApiTriggerId(it.getNamespace(), it.getFlowId(), it.getTriggerId()))
            .toList();

        // WHEN
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, false)),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(2);
        try {
            Await.until(() -> !jdbcTriggerRepository.findById(triggerDisabled).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be disabled");
        }
    }

    @Test
    void shouldAcceptSetDisabledByIdsWhenTrue() throws FlowProcessingException, QueueException {
        // GIVEN
        String namespace = "ns-" + IdUtils.create().toLowerCase();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);

        flowService.create(GenericFlow.of(flow1));
        flowService.create(GenericFlow.of(flow2));

        final TriggerState triggerDisabled = createTriggerFromFlow(flow1, true);
        final TriggerState triggerToDisable = createTriggerFromFlow(flow2, false);
        // Wait for the scheduler to initialize trigger states before updating them
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(triggerDisabled).isPresent());
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(triggerToDisable).isPresent());

        // WHEN
        List<TriggerController.ApiTriggerId> triggers = Stream.of(
            jdbcTriggerRepository.save(triggerDisabled),
                jdbcTriggerRepository.save(triggerToDisable)
            ).map(it -> new TriggerController.ApiTriggerId(it.getNamespace(), it.getFlowId(), it.getTriggerId()))
            .toList();

        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, true)),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(2);

        try {
            Await.until(() -> jdbcTriggerRepository.findById(triggerToDisable).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(10));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be disabled");
        }
    }

    @Test
    void shouldAcceptSetDisabledByQueryWhenTrue() throws FlowProcessingException, QueueException {
        // GIVEN
        String namespace = "ns-" + IdUtils.create().toLowerCase();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);

        flowService.create(GenericFlow.of(flow1));
        flowService.create(GenericFlow.of(flow2));

        TriggerState trigger1 = createTriggerFromFlow(flow1, true);
        final TriggerState toDisable = createTriggerFromFlow(flow2, false);
        // Wait for the scheduler to initialize trigger states before updating them
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(trigger1).isPresent());
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(toDisable).isPresent());
        jdbcTriggerRepository.save(trigger1);
        jdbcTriggerRepository.save(toDisable);

        // WHEN
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/set-disabled/by-query?filters[namespace][EQUALS]=%s&disabled=true".formatted(namespace), null),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(2);
        try {
            Await.until(() -> jdbcTriggerRepository.findById(toDisable).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be disabled");
        }
    }

    @Test
    void shouldReturnBadRequestWhenDisableByTriggersMissingBody() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.POST(
                    TRIGGER_PATH + "/set-disabled/by-triggers", new SetDisabledRequest(null, null)
                ),
                ApiAsyncOperationResponse.class
            )
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    private Flow generateFlow() {
        return Flow.builder()
            .tenantId(TENANT_ID)
            .namespace("ns-" + IdUtils.create().toLowerCase())
            .id(IdUtils.create())
            .tasks(
                Collections.singletonList(
                    Return.builder()
                        .id("task")
                        .type(Return.class.getName())
                        .format(Property.ofValue("return data"))
                        .build()
                )
            )
            .triggers(
                List.of(
                    Schedule.builder()
                        .id("trigger-nextexec-schedule")
                        .type(Schedule.class.getName())
                        .cron("*/1 * * * *")
                        .build(),
                    PollingTrigger.builder()
                        .id("trigger-nextexec-polling")
                        .type(PollingTrigger.class.getName())
                        .build()
                )
            )
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
            .tasks(
                Collections.singletonList(
                    Return.builder()
                        .id("task")
                        .type(Return.class.getName())
                        .format(Property.ofValue("return data"))
                        .build()
                )
            )
            .triggers(
                List.of(
                    Schedule.builder()
                        .id(IdUtils.create())
                        .type(Schedule.class.getName())
                        .cron("*/1 * * * *")
                        .build()
                )
            )
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
        return flow.getTriggers().stream().map(
            it -> TriggerState.builder()
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
