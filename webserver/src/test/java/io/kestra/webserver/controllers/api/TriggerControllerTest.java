package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true, startScheduler = true)
class TriggerControllerTest {

    public static final String TENANT_ID = "main";
    public static final String NAMESPACE = "io.kestra.unittest";
    public static final String TRIGGER_PATH = "/api/v1/main/triggers";
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    AbstractJdbcFlowRepository jdbcFlowRepository;

    @Inject
    AbstractJdbcTriggerRepository jdbcTriggerRepository;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search() {
        String triggerFlowId = "schedule-trigger-search";
        String triggerNamespace = "io.kestra.tests.schedule";

        Flow flow = generateFlow(triggerFlowId);
        jdbcFlowRepository.create(GenericFlow.of(flow));

        Trigger trigger = Trigger.builder()
            .flowId(triggerFlowId)
            .namespace(triggerNamespace)
            .tenantId(TENANT_ID)
            .triggerId("trigger-nextexec-schedule")
            .date(ZonedDateTime.now())
            .build();

        jdbcTriggerRepository.save(trigger);
        jdbcTriggerRepository.save(trigger.toBuilder().triggerId("trigger-nextexec-polling").build());

        PagedResults<TriggerController.Triggers> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(TRIGGER_PATH
                + "/search?filters[q][EQUALS]=schedule-trigger-search&filters[namespace][STARTS_WITH]=io.kestra.tests&sort=triggerId:asc"),
            Argument.of(PagedResults.class, TriggerController.Triggers.class)
        );
        assertThat(triggers.getTotal()).isGreaterThanOrEqualTo(2L);

        assertThat(triggers.getResults().stream().map(TriggerController.Triggers::getTriggerContext).toList())
            .extracting(
                TriggerContext::getTriggerId,
                TriggerContext::getNamespace,
                TriggerContext::getFlowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-schedule", triggerNamespace, triggerFlowId),
                tuple("trigger-nextexec-polling", triggerNamespace, triggerFlowId)
            );
        PagedResults<TriggerController.Triggers> triggers_oldParameters = client.toBlocking().retrieve(
            HttpRequest.GET(TRIGGER_PATH
                + "/search?q=schedule-trigger-search&namespace=io.kestra.tests&sort=triggerId:asc"),
            Argument.of(PagedResults.class, TriggerController.Triggers.class)
        );
        assertThat(triggers_oldParameters.getTotal()).isGreaterThanOrEqualTo(2L);

        assertThat(triggers_oldParameters.getResults().stream().map(TriggerController.Triggers::getTriggerContext).toList())
            .extracting(
                TriggerContext::getTriggerId,
                TriggerContext::getNamespace,
                TriggerContext::getFlowId
            )
            .containsExactlyInAnyOrder(
                tuple("trigger-nextexec-schedule", triggerNamespace, triggerFlowId),
                tuple("trigger-nextexec-polling", triggerNamespace, triggerFlowId)
            );
    }

    @Test
    void unlockTrigger() {
        Trigger trigger = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace(NAMESPACE)
            .tenantId(TENANT_ID)
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(trigger);

        trigger = client.toBlocking().retrieve(HttpRequest.POST((TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
            trigger.getNamespace(),
            trigger.getFlowId(),
            trigger.getTriggerId()
        ), null), Trigger.class);

        assertThat(trigger.getExecutionId()).isNull();
        assertThat(trigger.getEvaluateRunningDate()).isNull();

        Trigger unlockedTrigger = jdbcTriggerRepository.findLast(trigger).orElseThrow();

        assertThat(unlockedTrigger.getExecutionId()).isNull();
        assertThat(unlockedTrigger.getEvaluateRunningDate()).isNull();

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
                unlockedTrigger.getNamespace(),
                unlockedTrigger.getFlowId(),
                unlockedTrigger.getTriggerId()
            ), null)));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(e.getMessage()).isEqualTo("Illegal state: Trigger is not locked");

        e = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH + "/%s/%s/%s/unlock").formatted(
                "bad.namespace",
                "some-flow-id",
                "some-trigger-id"
            ), null))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void updated() {
        Flow flow = generateFlow("flow-with-triggers-updated");
        jdbcFlowRepository.create(GenericFlow.of(flow));

        Trigger trigger = Trigger.builder()
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .tenantId(TENANT_ID)
            .triggerId("trigger-nextexec-schedule")
            .executionId(IdUtils.create())
            .disabled(true)
            .build();

        jdbcTriggerRepository.create(trigger);

        Trigger updatedBad = trigger
            .toBuilder()
            .executionId("hello")
            .disabled(false)
            .tenantId(null)
            .build();

        Trigger afterUpdated = client.toBlocking().retrieve(HttpRequest.PUT(TRIGGER_PATH, updatedBad), Trigger.class);

        // Assert that executionId cannot be edited
        assertThat(afterUpdated.getExecutionId()).isNotEqualTo("hello");
        // Assert that disabled can be edited
        assertThat(afterUpdated.getDisabled()).isFalse();
    }

    @Test
    void restartTrigger() {
        Flow flow = generateFlow("flow-with-triggers");
        jdbcFlowRepository.create(GenericFlow.of(flow));

        Trigger trigger = Trigger.builder()
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .tenantId(TENANT_ID)
            .triggerId("trigger-to-restart")
            .executionId(IdUtils.create())
            .disabled(true)
            .build();

        jdbcTriggerRepository.create(trigger);

        HttpResponse<?> restarted = client.toBlocking().exchange(HttpRequest.POST((TRIGGER_PATH
            + "/io.kestra.tests.schedule/flow-with-triggers/trigger-to-restart/restart"), null));
        assertThat(restarted.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST((
            TRIGGER_PATH + "/notfound/notfound/notfound/restart"), null)));
    }

    @Test
    void unlockTriggerByTriggers() {
        Trigger triggerLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace(NAMESPACE)
            .tenantId(TENANT_ID)
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .build();

        Trigger triggerNotLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace(NAMESPACE)
            .tenantId(TENANT_ID)
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerLock);
        jdbcTriggerRepository.save(triggerNotLock);

        List<Trigger> triggers = List.of(triggerLock, triggerNotLock);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/unlock/by-triggers", triggers), BulkResponse.class);

        assertThat(bulkResponse.getCount()).isEqualTo(1);
    }

    @Test
    void unlockTriggerByQuery() {
        Trigger triggerLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace(NAMESPACE)
            .tenantId(TENANT_ID)
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .build();

        Trigger triggerNotLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace(NAMESPACE)
            .tenantId(TENANT_ID)
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerLock);
        jdbcTriggerRepository.save(triggerNotLock);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/unlock/by-query?namespace=io.kestra.unittest", null), BulkResponse.class);

        assertThat(bulkResponse.getCount()).isEqualTo(1);
    }

    @Test
    void enableByTriggers() {
        String namespace = IdUtils.create();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);
        
        jdbcFlowRepository.create(GenericFlow.of(flow1));
        jdbcFlowRepository.create(GenericFlow.of(flow2));
        
        Trigger triggerDisabled = createTriggerFromFlow(flow1, true);
        Trigger triggerNotDisabled = createTriggerFromFlow(flow2, false);

        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        List<Trigger> triggers = List.of(triggerDisabled, triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, false)), BulkResponse.class);

        assertThat(bulkResponse.getCount()).isEqualTo(2);
        assertThat(jdbcTriggerRepository.findLast(triggerDisabled).get().getDisabled()).isFalse();
    }
    
    @Test
    void enableByQuery() {
        String namespace = IdUtils.create();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);
        
        jdbcFlowRepository.create(GenericFlow.of(flow1));
        jdbcFlowRepository.create(GenericFlow.of(flow2));
        
        Trigger triggerDisabled = createTriggerFromFlow(flow1, true);
        Trigger triggerNotDisabled = createTriggerFromFlow(flow2, false);

        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/set-disabled/by-query?namespace=%s&disabled=false".formatted(namespace), null), BulkResponse.class);

        assertThat(bulkResponse.getCount()).isEqualTo(2);
        assertThat(jdbcTriggerRepository.findLast(triggerDisabled).get().getDisabled()).isFalse();
    }

    @Test
    void disableByTriggers() {
        String namespace = IdUtils.create();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);
        
        jdbcFlowRepository.create(GenericFlow.of(flow1));
        jdbcFlowRepository.create(GenericFlow.of(flow2));
        
        Trigger triggerDisabled = createTriggerFromFlow(flow1, true);
        Trigger triggerNotDisabled = createTriggerFromFlow(flow2, false);
        
        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        List<Trigger> triggers = List.of(triggerDisabled, triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, true)), BulkResponse.class);

        assertThat(bulkResponse.getCount()).isEqualTo(2);
        assertThat(jdbcTriggerRepository.findLast(triggerNotDisabled).get().getDisabled()).isTrue();
    }

    @Test
    void disableByTriggersBadRequest() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.POST(
                    TRIGGER_PATH + "/set-disabled/by-triggers", new SetDisabledRequest(null, null)),
                BulkResponse.class));


        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void disableByQuery() {
        String namespace = IdUtils.create();
        Flow flow1 = generateFlowWithTrigger(namespace);
        Flow flow2 = generateFlowWithTrigger(namespace);
        
        jdbcFlowRepository.create(GenericFlow.of(flow1));
        jdbcFlowRepository.create(GenericFlow.of(flow2));
        
        Trigger triggerDisabled = createTriggerFromFlow(flow1, true);
        Trigger triggerNotDisabled = createTriggerFromFlow(flow2, false);

        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST(
            TRIGGER_PATH + "/set-disabled/by-query?namespace=%s&disabled=true".formatted(namespace), null), BulkResponse.class);

        assertThat(bulkResponse.getCount()).isEqualTo(2);
        assertThat(jdbcTriggerRepository.findLast(triggerNotDisabled).get().getDisabled()).isTrue();
    }

    @Test
    void nextExecutionDate() throws TimeoutException {
        Flow flow = generateFlow("flow-with-triggers");
        jdbcFlowRepository.create(GenericFlow.of(flow));
        Await.until(
            () -> client.toBlocking().retrieve(HttpRequest.GET(
                TRIGGER_PATH + "/search?filters[q][EQUALS]=trigger-nextexec"), Argument.of(PagedResults.class, Trigger.class)).getTotal() >= 2,
            Duration.ofMillis(100),
            Duration.ofSeconds(20)
        );
        PagedResults<TriggerController.Triggers> triggers = client.toBlocking().retrieve(HttpRequest.GET(
            TRIGGER_PATH + "/search?filters[q][EQUALS]=trigger-nextexec"), Argument.of(PagedResults.class, TriggerController.Triggers.class));
        assertThat(triggers.getResults().getFirst().getTriggerContext().getNextExecutionDate()).isNotNull();
        assertThat(triggers.getResults().get(1).getTriggerContext().getNextExecutionDate()).isNotNull();
    }

    private Flow generateFlow(String flowId) {
        return Flow.builder()
            .id(flowId)
            .namespace("io.kestra.tests.schedule")
            .tenantId(TENANT_ID)
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
    
    
    private static Trigger createTriggerFromFlow(Flow flow1, Boolean disabled) {
        return Trigger.builder()
            .flowId(flow1.getId())
            .tenantId(flow1.getTenantId())
            .namespace(flow1.getNamespace())
            .triggerId(flow1.getTriggers().getFirst().getId())
            .disabled(disabled)
            .build();
    }

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Inject 
    TriggerRepositoryInterface triggerRepository;

    @SuppressWarnings("unchecked")
    @Test
    void testGetTrigger() {
        Flow flow = createTestFlow();
        flowRepositoryInterface.create(flow);
        
        Trigger trigger = Trigger.builder()
            .triggerId("test-trigger")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .flowRevision(1)
            .triggerId("test-trigger")
            .build();
        
        triggerRepository.create(trigger);
        
        HttpResponse<Trigger> response = client.toBlocking()
            .exchange(
                HttpRequest.GET("/triggers/" + flow.getNamespace() + "/" + flow.getId() + "/test-trigger"),
                Trigger.class
            );
        
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.body());
        assertEquals("test-trigger", response.body().getId());
        assertEquals(flow.getId(), response.body().getFlowId());
    }

    @Test
    void testGetTriggerNotFound() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.GET("/triggers/nonexistent/flow/trigger"),
                Trigger.class
            )
        );
        
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void testDeleteTriggersByQuery() {
        Flow flow = createTestFlow();
        flowRepositoryInterface.create(flow);
        
        Trigger trigger = Trigger.builder()
            .id("delete-test-trigger")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .flowRevision(1)
            .triggerId("delete-test-trigger")
            .build();

        Trigger triggerByQuery1 = Trigger.builder()
            .id("query-test-trigger-1")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .flowRevision(1)
            .triggerId("query-test-trigger-1")
            .build();

        Trigger triggerByQuery2 = Trigger.builder()
            .id("query-test-trigger-2")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .flowRevision(1)
            .triggerId("query-test-trigger-2")
            .build();

        triggerRepository.save(trigger);
        triggerRepository.save(triggerByQuery1);
        triggerRepository.save(triggerByQuery2);

        triggerRepository.create(trigger);

        List<Trigger> allBeforeDelete = triggerRepository.findByQuery(flow.getNamespace(), flow.getId());
        assertEquals(3, allBeforeDelete.size(), "Expected 3 triggers before deletion");
        
        HttpResponse<Integer> firstDeleteResponse = client.toBlocking()
            .exchange(
                 HttpRequest.DELETE("/triggers/query" + "?filters[namespace][EQUALS]=" + flow.getNamespace() + "&filters[flowId][EQUALS]=" + flow.getId() + "&filters[triggerId][EQUALS]=delete-test-trigger"),
                Integer.class
            );
        
        assertEquals(HttpStatus.OK, firstDeleteResponse.getStatus());
        assertEquals(1, firstDeleteResponse.body(), "Expected 1 trigger deleted");

        List<Trigger> remainingAfterFirstDelete = triggerRepository.findByQuery(flow.getNamespace(), flow.getId());
        assertEquals(2, remainingAfterFirstDelete.size(), "Expected 2 triggers remaining after first delete");

        HttpResponse<Integer> secondDeleteResponse = client.toBlocking()
            .exchange(
                HttpRequest.DELETE("/triggers/query" + "?filters[namespace][EQUALS]=" + flow.getNamespace() + "&filters[flowId][EQUALS]=" + flow.getId()),
                Integer.class
            );

        assertEquals(HttpStatus.OK, secondDeleteResponse.getStatus());
        assertEquals(2, secondDeleteResponse.body(), "Expected 2 remaining triggers deleted");

        List<Trigger> finalRemaining = triggerRepository.findByQuery(flow.getNamespace(), flow.getId());
        assertEquals(0, finalRemaining.size(), "Expected no triggers after final deletion");
        
        Optional<Trigger> deletedTrigger = triggerRepository.findById(
            flow.getNamespace(),
            flow.getId(),
            "delete-test-trigger"
        );
        
        assertFalse(deletedTrigger.isPresent());
    }

    @Test
    void testDeleteTriggerById() {
        Flow flow = createTestFlow();
        flowRepositoryInterface.create(flow);
        
        Trigger trigger = Trigger.builder()
            .id("delete-by-id-trigger")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .flowRevision(1)
            .triggerId("delete-by-id-trigger")
            .build();
        
        triggerRepository.create(trigger);
        
        HttpResponse<Void> response = client.toBlocking()
            .exchange(
                HttpRequest.DELETE("/triggers/" + flow.getNamespace() + "/" + flow.getId() + "/delete-by-id-trigger"),
                Void.class
            );
        
        assertEquals(HttpStatus.NO_CONTENT, response.getStatus());
        
        Optional<Trigger> deletedTrigger = triggerRepository.findById(
            flow.getNamespace(),
            flow.getId(),
            "delete-by-id-trigger"
        );
        
        assertFalse(deletedTrigger.isPresent());
    }
    
    private Flow createTestFlow() {
        return Flow.builder()
            .id("trigger-test-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .tasks(List.of())
            .build();
    }
}
