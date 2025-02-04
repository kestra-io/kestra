package io.kestra.webserver.controllers.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;
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
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true, startScheduler = true)
class TriggerControllerTest {
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
        jdbcFlowRepository.create(flow, flow.generateSource(), flow);

        Trigger trigger = Trigger.builder()
            .flowId(triggerFlowId)
            .namespace(triggerNamespace)
            .triggerId("trigger-nextexec-schedule")
            .date(ZonedDateTime.now())
            .build();

        jdbcTriggerRepository.save(trigger);
        jdbcTriggerRepository.save(trigger.toBuilder().triggerId("trigger-nextexec-polling").build());

        PagedResults<TriggerController.Triggers> triggers = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/triggers/search?q=schedule-trigger-search&namespace=io.kestra.tests&sort=triggerId:asc"), Argument.of(PagedResults.class, TriggerController.Triggers.class));
        assertThat(triggers.getTotal(), greaterThanOrEqualTo(2L));

        assertThat(triggers.getResults().stream().map(TriggerController.Triggers::getTriggerContext).toList(), Matchers.hasItems(
                allOf(
                    hasProperty("triggerId", is("trigger-nextexec-schedule")),
                    hasProperty("namespace", is(triggerNamespace)),
                    hasProperty("flowId", is(triggerFlowId))
                ),
                allOf(
                    hasProperty("triggerId", is("trigger-nextexec-polling")),
                    hasProperty("namespace", is(triggerNamespace)),
                    hasProperty("flowId", is(triggerFlowId))
                )
            )
        );
    }

    @Test
    void unlock() {
        Trigger trigger = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(trigger);

        trigger = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/triggers/%s/%s/%s/unlock".formatted(
            trigger.getNamespace(),
            trigger.getFlowId(),
            trigger.getTriggerId()
        ), null), Trigger.class);

        assertThat(trigger.getExecutionId(), is(nullValue()));
        assertThat(trigger.getEvaluateRunningDate(), is(nullValue()));

        Trigger unlockedTrigger = jdbcTriggerRepository.findLast(trigger).orElseThrow();

        assertThat(unlockedTrigger.getExecutionId(), is(nullValue()));
        assertThat(unlockedTrigger.getEvaluateRunningDate(), is(nullValue()));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.POST("/api/v1/triggers/%s/%s/%s/unlock".formatted(
                unlockedTrigger.getNamespace(),
                unlockedTrigger.getFlowId(),
                unlockedTrigger.getTriggerId()
            ), null)));

        assertThat(e.getStatus(), is(HttpStatus.CONFLICT));
        assertThat(e.getMessage(), is("Illegal state: Trigger is not locked"));

        e = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.POST("/api/v1/triggers/%s/%s/%s/unlock".formatted(
                "bad.namespace",
                "some-flow-id",
                "some-trigger-id"
            ), null))
        );

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void updated() {
        Flow flow = generateFlow("flow-with-triggers-updated");
        jdbcFlowRepository.create(flow, flow.generateSource(), flow);

        Trigger trigger = Trigger.builder()
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .triggerId("trigger-nextexec-schedule")
            .executionId(IdUtils.create())
            .disabled(true)
            .build();

        jdbcTriggerRepository.create(trigger);

        Trigger updatedBad = trigger
            .toBuilder()
            .executionId("hello")
            .disabled(false)
            .build();

        Trigger afterUpdated = client.toBlocking().retrieve(HttpRequest.PUT(("/api/v1/triggers"), updatedBad), Trigger.class);

        // Assert that executionId cannot be edited
        assertThat(afterUpdated.getExecutionId(), not("hello"));
        // Assert that disabled can be edited
        assertThat(afterUpdated.getDisabled(), is(false));
    }

    @Test
    void restart() {
        Flow flow = generateFlow("flow-with-triggers");
        jdbcFlowRepository.create(flow, flow.generateSource(), flow);

        Trigger trigger = Trigger.builder()
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .triggerId("trigger-to-restart")
            .executionId(IdUtils.create())
            .disabled(true)
            .build();

        jdbcTriggerRepository.create(trigger);

        HttpResponse<?> restarted = client.toBlocking().exchange(HttpRequest.POST(("/api/v1/triggers/io.kestra.tests.schedule/flow-with-triggers/trigger-to-restart/restart"), null));
        assertThat(restarted.getStatus(), is(HttpStatus.OK));

        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST(("/api/v1/triggers/notfound/notfound/notfound/restart"), null)));
    }

    @Test
    void unlockByTriggers() {
        Trigger triggerLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .build();

        Trigger triggerNotLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerLock);
        jdbcTriggerRepository.save(triggerNotLock);

        List<Trigger> triggers = List.of(triggerLock, triggerNotLock);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/triggers/unlock/by-triggers", triggers), BulkResponse.class);

        assertThat(bulkResponse.getCount(), is(1));
    }

    @Test
    void unlockByQuery() {
        Trigger triggerLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .build();

        Trigger triggerNotLock = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerLock);
        jdbcTriggerRepository.save(triggerNotLock);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/triggers/unlock/by-query?namespace=io.kestra.unittest", null), BulkResponse.class);

        assertThat(bulkResponse.getCount(), is(1));
    }

    @Test
    void enableByTriggers() {
        Trigger triggerDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .disabled(true)
            .build();

        Trigger triggerNotDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        List<Trigger> triggers = List.of(triggerDisabled, triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/triggers/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, false)), BulkResponse.class);

        assertThat(bulkResponse.getCount(), is(2));
        assertThat(jdbcTriggerRepository.findLast(triggerDisabled).get().getDisabled(), is(false));
    }

    @Test
    void enableByQuery() {
        Trigger triggerDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .disabled(true)
            .build();

        Trigger triggerNotDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/triggers/set-disabled/by-query?namespace=io.kestra.unittest&disabled=false", null), BulkResponse.class);

        assertThat(bulkResponse.getCount(), is(2));
        assertThat(jdbcTriggerRepository.findLast(triggerDisabled).get().getDisabled(), is(false));
    }

    @Test
    void disableByTriggers() {
        Trigger triggerDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .disabled(true)
            .build();

        Trigger triggerNotDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        List<Trigger> triggers = List.of(triggerDisabled, triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/triggers/set-disabled/by-triggers", new TriggerController.SetDisabledRequest(triggers, true)), BulkResponse.class);

        assertThat(bulkResponse.getCount(), is(2));
        assertThat(jdbcTriggerRepository.findLast(triggerNotDisabled).get().getDisabled(), is(true));
    }

    @Test
    void disableByQuery() {
        Trigger triggerDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .disabled(true)
            .build();

        Trigger triggerNotDisabled = Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .build();

        jdbcTriggerRepository.save(triggerDisabled);
        jdbcTriggerRepository.save(triggerNotDisabled);

        BulkResponse bulkResponse = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/triggers/set-disabled/by-query?namespace=io.kestra.unittest&disabled=true", null), BulkResponse.class);

        assertThat(bulkResponse.getCount(), is(2));
        assertThat(jdbcTriggerRepository.findLast(triggerNotDisabled).get().getDisabled(), is(true));
    }

    @Test
    void nextExecutionDate() throws InterruptedException, TimeoutException {
        Flow flow = generateFlow("flow-with-triggers");
        jdbcFlowRepository.create(flow, flow.generateSource(), flow);
        Await.until(
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/triggers/search?q=trigger-nextexec"), Argument.of(PagedResults.class, Trigger.class)).getTotal() >= 2,
            Duration.ofMillis(100),
            Duration.ofMinutes(2)
        );
        PagedResults<TriggerController.Triggers> triggers = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/triggers/search?q=trigger-nextexec"), Argument.of(PagedResults.class, TriggerController.Triggers.class));
        assertThat(triggers.getResults().getFirst().getTriggerContext().getNextExecutionDate(), notNullValue());
        assertThat(triggers.getResults().get(1).getTriggerContext().getNextExecutionDate(), notNullValue());
    }

    private Flow generateFlow(String flowId) {
        return Flow.builder()
            .id(flowId)
            .namespace("io.kestra.tests.schedule")
            .tasks(Collections.singletonList(Return.builder()
                .id("task")
                .type(Return.class.getName())
                .format(Property.of("return data"))
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


}
