package io.kestra.webserver.controllers;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TriggerControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    AbstractJdbcFlowRepository jdbcFlowRepository;

    @Inject
    AbstractJdbcTriggerRepository jdbcTriggerRepository;

    @BeforeEach
    protected void init() {
        super.setup();
    }

    @SuppressWarnings("unchecked")
    @Test
    void search() {
        String triggerFlowId = "schedule-trigger";
        String triggerNamespace = "io.kestra.tests.schedule";

        Trigger trigger = Trigger.builder()
            .flowId(triggerFlowId)
            .namespace(triggerNamespace)
            .triggerId("schedule-every-min")
            .build();

        jdbcTriggerRepository.save(trigger);
        jdbcTriggerRepository.save(trigger.toBuilder().triggerId("schedule-5-min").build());

        PagedResults<Trigger> triggers = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/triggers/search?q=schedule-trigger&namespace=io.kestra.tests&sort=triggerId:asc"), Argument.of(PagedResults.class, Trigger.class));
        assertThat(triggers.getTotal(), is(2L));


        assertThat(triggers.getResults(), Matchers.hasItems(
                allOf(
                    hasProperty("triggerId", is("schedule-every-min")),
                    hasProperty("namespace", is(triggerNamespace)),
                    hasProperty("flowId", is(triggerFlowId))
                ),
                allOf(
                    hasProperty("triggerId", is("schedule-5-min")),
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
}
