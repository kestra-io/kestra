package io.kestra.webserver.controllers.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.core.services.FlowService;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.webserver.controllers.api.TriggerController.SetDisabledRequest;
import io.kestra.webserver.models.api.ApiAsyncOperationResponse;
import io.kestra.webserver.models.api.ApiTriggerAndState;
import io.kestra.webserver.models.api.ApiTriggerState;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.kestra.core.junit.assertions.Problems;
import io.kestra.webserver.errors.ProblemTypes;
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
    Scheduler scheduler;

    @Inject
    SchedulerConfiguration schedulerConfiguration;

    // Every test here scopes itself to a namespace of its own, so the tables are deliberately not
    // truncated between tests: this class starts the runner, and truncating QUEUES underneath a
    // running executor deadlocks on the table lock as soon as any test drives a real execution.
    @BeforeEach
    protected void setup() {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(() -> scheduler.isActive());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindTriggersGivenQueryOnIdPrefix() throws FlowProcessingException, QueueException {
        // GIVEN two triggers whose ids share the queried prefix, and one in the same namespace whose
        // id does not: the namespace filter isolates this test from the rest of the class, the extra
        // trigger keeps the assertion below a test of `q` rather than of the namespace filter alone.
        Flow flow = generateFlow();
        flowService.create(GenericFlow.of(flow));
        createTriggersFromFlow(flow).forEach(jdbcTriggerRepository::save);

        Flow unmatched = generateFlowWithTrigger(flow.getNamespace());
        TriggerState unmatchedState = createTriggerFromFlow(unmatched, false);
        flowService.create(GenericFlow.of(unmatched));
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(unmatchedState).isPresent());

        // WHEN
        PagedResults<ApiTriggerAndState> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(
                TRIGGER_PATH + "/search?filters[q][EQUALS]=trigger-nextexec&filters[namespace][EQUALS]=%s"
                    .formatted(flow.getNamespace())
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
    void shouldFindTriggersTheSchedulerDoesNotEvaluate() throws FlowProcessingException, QueueException {
        // GIVEN a flow mixing a scheduled trigger with two the scheduler never evaluates (kestra-io/kestra#18379)
        Flow flow = generateFlowWithUnscheduledTriggers();
        flowService.create(GenericFlow.of(flow));
        awaitTriggerStates(flow);

        // WHEN
        PagedResults<ApiTriggerAndState> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(TRIGGER_PATH + "/search?filters[namespace][EQUALS]=%s".formatted(flow.getNamespace())),
            Argument.of(PagedResults.class, ApiTriggerAndState.class)
        );

        // THEN
        assertThat(triggers.getResults())
            .extracting(it -> it.state().triggerId(), it -> it.state().kind())
            .containsExactlyInAnyOrder(
                tuple("schedule", TriggerType.SCHEDULE),
                tuple("webhook", TriggerType.UNSCHEDULED),
                tuple("flow-trigger", TriggerType.UNSCHEDULED)
            );
    }

    @Test
    void shouldRecordLastTriggeredDateWhenAnUnscheduledTriggerFires() throws FlowProcessingException, QueueException {
        // GIVEN a webhook trigger, which the scheduler holds a state for but never evaluates
        Flow flow = generateFlowWithUnscheduledTriggers();
        flowService.create(GenericFlow.of(flow));
        awaitTriggerStates(flow);
        TriggerId webhook = TriggerId.of(TENANT_ID, flow.getNamespace(), flow.getId(), "webhook");

        // WHEN the webhook is called
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/executions/webhook/%s/%s/a-secret-key".formatted(flow.getNamespace(), flow.getId()), null)
        );

        // THEN the scheduler records the firing on the trigger state, which would otherwise report it as never fired
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(
            () -> jdbcTriggerRepository.findById(webhook).map(TriggerState::getLastTriggeredDate).isPresent()
        );
        assertThat(jdbcTriggerRepository.findById(webhook).orElseThrow().getExecutionId()).isNotNull();
    }

    @Test
    void shouldRecordLastTriggeredDateWhenAFlowTriggerFires() throws FlowProcessingException, QueueException {
        // GIVEN an upstream flow, and a listener whose flow trigger has no dependsOn so it goes through the
        // executor's simple-conditions path. `when` scopes it to this upstream flow: a flow trigger with
        // neither dependsOn nor conditions is evaluated against every execution of every tenant.
        Flow upstream = generateFlowWithWebhook();
        Flow listener = generateFlowTriggerListener(upstream);
        flowService.create(GenericFlow.of(upstream));
        flowService.create(GenericFlow.of(listener));
        awaitTriggerStates(listener);
        TriggerId flowTrigger = TriggerId.of(TENANT_ID, listener.getNamespace(), listener.getId(), "flow-trigger");

        // WHEN the upstream flow runs to completion
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/executions/webhook/%s/%s/a-secret-key".formatted(upstream.getNamespace(), upstream.getId()), null)
        );

        // THEN the executor tells the scheduler the flow trigger fired
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).until(
            () -> jdbcTriggerRepository.findById(flowTrigger).map(TriggerState::getLastTriggeredDate).isPresent()
        );
        assertThat(jdbcTriggerRepository.findById(flowTrigger).orElseThrow().getExecutionId()).isNotNull();
    }

    @Test
    void shouldReturnConflictWhenBackfillingATriggerTheSchedulerDoesNotEvaluate() throws FlowProcessingException, QueueException {
        // GIVEN a webhook trigger, which now holds a state and so passes the trigger-exists check
        Flow flow = generateFlowWithUnscheduledTriggers();
        flowService.create(GenericFlow.of(flow));
        awaitTriggerStates(flow);

        // WHEN
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(
                HttpRequest.PUT(
                    TRIGGER_PATH + "/backfill/create",
                    new TriggerController.ApiCreateBackfillRequest(
                        flow.getNamespace(),
                        flow.getId(),
                        "webhook",
                        new TriggerController.ApiCreateBackfillRequest.Backfill(
                            ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), Map.of(), List.of()
                        )
                    )
                )
            )
        );

        // THEN it is refused rather than accepted into a backfill that could never run
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(e.getMessage()).contains("not evaluated by the scheduler");
    }

    @Test
    void searchTriggersWithUnknownSortFieldReturns422() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.GET(TRIGGER_PATH + "/search?sort=nonexistent:asc"))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(422);
        String body = e.getResponse().getBody(String.class).orElse("");
        assertThat(body).contains("nonexistent");
        // regression guard: the generated SQL must never reach the client (kestra-io/kestra#18490)
        assertThat(body).doesNotContainIgnoringCase("select ");
        assertThat(body).doesNotContainIgnoringCase(" from ");
        assertThat(body).doesNotContainIgnoringCase("order by");
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchTriggersSortsByNextExecutionDateAlias() throws FlowProcessingException, QueueException {
        // nextExecutionDate is a pre-2.0 alias of the real column next_evaluation_date
        Flow flow = generateFlow();
        flowService.create(GenericFlow.of(flow));
        createTriggersFromFlow(flow).forEach(jdbcTriggerRepository::save);

        PagedResults<ApiTriggerAndState> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(TRIGGER_PATH + "/search?filters[namespace][STARTS_WITH]=%s&sort=nextExecutionDate:asc".formatted(flow.getNamespace())),
            Argument.of(PagedResults.class, ApiTriggerAndState.class)
        );

        assertThat(triggers.getTotal()).isGreaterThanOrEqualTo(2L);
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
    void shouldUnlockTriggerWhenLocked() throws FlowProcessingException, QueueException {
        // GIVEN
        TriggerState trigger = newLockedFlowBackedTrigger();

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
        // GIVEN
        TriggerState trigger = newRandomTriggerState()
            .locked(Clock.systemDefaultZone(), false);
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
        Problems.assertProblem(e, ProblemTypes.CONFLICT);
        assertThat(Problems.detail(e)).isEqualTo(
            "trigger [tenant=%s, namespace=%s, flow=%s, trigger=%s] is already unlocked"
                .formatted(trigger.getTenantId(), trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId())
        );
    }

    @Test
    void shouldReturnConflictWhenUnlockingRealtimeTrigger() {
        // GIVEN — locked is the normal running state of a realtime trigger
        TriggerState trigger = newRandomTriggerState(TriggerType.REALTIME).locked(Clock.systemDefaultZone(), true);
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
        Problems.assertProblem(e, ProblemTypes.CONFLICT);
        assertThat(Problems.detail(e)).isEqualTo(
            "trigger [tenant=%s, namespace=%s, flow=%s, trigger=%s] is a realtime trigger, reset it to kill and restart it"
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
        Problems.assertProblem(e, ProblemTypes.NOT_FOUND);
    }

    @Test
    void shouldReturnNotFoundWhenUnlockingOrphanedTrigger() {
        // GIVEN — a locked trigger row whose flow doesn't exist (e.g. deleted after the trigger was
        // locked, before orphan-GC caught up). Unlocking it must fail fast instead of racing that GC.
        TriggerState trigger = newRandomTriggerState().locked(Clock.systemDefaultZone(), true);
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
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldRestartTriggerWhenExists() throws FlowProcessingException, QueueException {
        // GIVEN — a real, flow-backed trigger: restarting a flow-less trigger would race the
        // scheduler's orphan-GC the same way unlocking one does (see newLockedFlowBackedTrigger javadoc).
        TriggerState trigger = newLockedFlowBackedTrigger();

        // WHEN
        HttpResponse<ApiTriggerState> restarted = client.toBlocking().exchange(
            HttpRequest.POST(
                (TRIGGER_PATH
                    + "/%s/%s/%s/restart".formatted(trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId())),
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
    void shouldReturnNotFoundWhenRestartingOrphanedTrigger() {
        // GIVEN — a locked trigger row whose flow doesn't exist (e.g. deleted after the trigger was
        // locked, before orphan-GC caught up). Restarting it must fail fast instead of racing that GC.
        TriggerState trigger = newRandomTriggerState().locked(Clock.systemDefaultZone(), true);
        jdbcTriggerRepository.save(trigger);

        // WHEN
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    (TRIGGER_PATH + "/%s/%s/%s/restart").formatted(
                        trigger.getNamespace(),
                        trigger.getFlowId(),
                        trigger.getTriggerId()
                    ), null
                )
            )
        );

        // THEN
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
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
    void shouldAcceptUnlockByIdsWhenLocked() throws FlowProcessingException, QueueException {
        // GIVEN
        TriggerState state = newLockedFlowBackedTrigger();

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
    void shouldAcceptUnlockByIdsWithZeroItemsWhenRealtime() {
        // GIVEN
        TriggerState state = newRandomTriggerState(TriggerType.REALTIME).locked(Clock.systemDefaultZone(), true);
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
    void shouldAcceptUnlockByQueryWhenLocked() throws FlowProcessingException, QueueException {
        // GIVEN
        TriggerState state = newLockedFlowBackedTrigger();

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
    void shouldAcceptUnlockByQueryWithZeroItemsWhenRealtime() {
        // GIVEN
        TriggerState state = newRandomTriggerState(TriggerType.REALTIME).locked(Clock.systemDefaultZone(), true);
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
    void shouldAcceptSetDisabledWhenRecoverMissedSchedulesProvided() throws FlowProcessingException, QueueException {
        // GIVEN
        String namespace = "ns-" + IdUtils.create().toLowerCase();
        Flow flow = generateFlowWithTrigger(namespace);
        flowService.create(GenericFlow.of(flow));

        final TriggerState trigger = createTriggerFromFlow(flow, true);
        // Wait for the scheduler to initialize trigger states before updating them
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(trigger).isPresent());
        jdbcTriggerRepository.save(trigger);

        // WHEN
        HttpResponse<ApiTriggerState> response = client.toBlocking().exchange(
            HttpRequest.PUT(
                TRIGGER_PATH + "/set-disabled",
                new TriggerController.ApiDisableTriggerRequest(trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId(), false, true)
            ),
            ApiTriggerState.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.body().disabled()).isFalse();
    }

    @Test
    void shouldAcceptSetDisabledByIdsWhenRecoverMissedSchedulesProvided() throws FlowProcessingException, QueueException {
        // GIVEN
        String namespace = "ns-" + IdUtils.create().toLowerCase();
        Flow flow = generateFlowWithTrigger(namespace);
        flowService.create(GenericFlow.of(flow));

        final TriggerState trigger = createTriggerFromFlow(flow, true);
        // Wait for the scheduler to initialize trigger states before updating them
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(trigger).isPresent());
        jdbcTriggerRepository.save(trigger);

        List<TriggerController.ApiTriggerId> triggers = List.of(
            new TriggerController.ApiTriggerId(trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId())
        );

        // WHEN
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, false, true)),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(1);
        try {
            Await.until(() -> !jdbcTriggerRepository.findById(trigger).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be enabled");
        }
    }

    @Test
    void shouldAcceptSetDisabledByQueryWhenRecoverMissedSchedulesProvided() throws FlowProcessingException, QueueException {
        // GIVEN
        String namespace = "ns-" + IdUtils.create().toLowerCase();
        Flow flow = generateFlowWithTrigger(namespace);
        flowService.create(GenericFlow.of(flow));

        final TriggerState trigger = createTriggerFromFlow(flow, true);
        // Wait for the scheduler to initialize trigger states before updating them
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(trigger).isPresent());
        jdbcTriggerRepository.save(trigger);

        // WHEN
        HttpResponse<ApiAsyncOperationResponse> response = client.toBlocking().exchange(
            HttpRequest.POST(TRIGGER_PATH + "/set-disabled/by-query?filters[namespace][EQUALS]=%s&disabled=false&recoverMissedSchedules=true".formatted(namespace), null),
            ApiAsyncOperationResponse.class
        );

        // THEN
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().totalItems()).isEqualTo(1);
        try {
            Await.until(() -> !jdbcTriggerRepository.findById(trigger).get().isDisabled(), Duration.ofSeconds(1), Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger to be enabled");
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
        return newRandomTriggerState(null);
    }

    private TriggerState newRandomTriggerState(TriggerType type) {
        String random = IdUtils.create();
        // Set a far-future nextEvaluationDate so the scheduler never considers this trigger
        // eligible for evaluation and does not delete it as an orphan (trigger has no associated flow).
        return TriggerState.builder()
            .tenantId(TENANT_ID)
            .namespace(random)
            .flowId(random)
            .triggerId(random)
            .nextEvaluationDate(Instant.now().plus(Duration.ofDays(36500L)))
            .type(type)
            .vnode(VNodes.computeVNodeFromFlow(FlowId.of(TENANT_ID, random, random, null), schedulerConfiguration.vnodes()))
            .build();
    }

    private TriggerState newLockedFlowBackedTrigger() throws FlowProcessingException, QueueException {
        Flow flow = generateFlowWithTrigger(IdUtils.create().toLowerCase());
        TriggerState fixture = createTriggerFromFlow(flow, false);
        flowService.create(GenericFlow.of(flow));

        // The flow's own trigger creation asynchronously seeds a TriggerState row; wait for it
        // instead of racing it with our own save(), then lock that row.
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
            .until(() -> jdbcTriggerRepository.findById(fixture).isPresent());
        TriggerState trigger = jdbcTriggerRepository.findById(fixture).orElseThrow()
            .locked(Clock.systemDefaultZone(), true);
        jdbcTriggerRepository.save(trigger);
        return trigger;
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

    private Flow generateFlowWithUnscheduledTriggers() {
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
                        .id("schedule")
                        .type(Schedule.class.getName())
                        .cron("*/1 * * * *")
                        .build(),
                    Webhook.builder()
                        .id("webhook")
                        .type(Webhook.class.getName())
                        .key("a-secret-key")
                        .build(),
                    io.kestra.plugin.core.trigger.Flow.builder()
                        .id("flow-trigger")
                        .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                        .build()
                )
            )
            .build();
    }

    private Flow generateFlowWithWebhook() {
        return Flow.builder()
            .tenantId(TENANT_ID)
            .namespace("ns-" + IdUtils.create().toLowerCase())
            .id(IdUtils.create())
            .tasks(Collections.singletonList(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("ok")).build()))
            .triggers(List.of(Webhook.builder().id("webhook").type(Webhook.class.getName()).key("a-secret-key").build()))
            .build();
    }

    private Flow generateFlowTriggerListener(Flow upstream) {
        return Flow.builder()
            .tenantId(TENANT_ID)
            .namespace(upstream.getNamespace())
            .id(IdUtils.create())
            .tasks(Collections.singletonList(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("ok")).build()))
            .triggers(
                List.of(
                    io.kestra.plugin.core.trigger.Flow.builder()
                        .id("flow-trigger")
                        .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                        .when("{{ flow.id == '%s' }}".formatted(upstream.getId()))
                        .build()
                )
            )
            .build();
    }

    private void awaitTriggerStates(Flow flow) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(
            () -> flow.getTriggers().stream().allMatch(
                trigger -> jdbcTriggerRepository.findById(TriggerId.of(flow, trigger)).isPresent()
            )
        );
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

    @Test
    void shouldExportTriggersWithoutTenantId() throws FlowProcessingException, QueueException {
        Flow flow = generateFlow();
        flowService.create(GenericFlow.of(flow));
        createTriggersFromFlow(flow).forEach(jdbcTriggerRepository::save);

        byte[] csvBytes = client.toBlocking().retrieve(
            HttpRequest.GET(
                TRIGGER_PATH + "/export/by-query/csv?filters[namespace][EQUALS]=%s".formatted(flow.getNamespace())
            ),
            Argument.of(byte[].class)
        );

        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("tenantId");
    }
}
