package io.kestra.webserver.controllers;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.Await;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class TriggerControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Inject
    AbstractJdbcFlowRepository jdbcFlowRepository;

    @Inject
    AbstractJdbcTriggerRepository jdbcTriggerRepository;


    @BeforeEach
    protected void init() throws TimeoutException {
        jdbcFlowRepository.findAll()
            .forEach(jdbcFlowRepository::delete);

        super.setup();
        Await.until(() -> jdbcTriggerRepository.findAll().size() > 0, null, Duration.ofSeconds(10));
    }

    @SuppressWarnings("unchecked")
    @Test
    void search() {
        PagedResults<Trigger> triggers = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/triggers/search?q=schedule-trigger&namespace=io.kestra.tests&sort=triggerId:asc"), Argument.of(PagedResults.class, Trigger.class));
        assertThat(triggers.getTotal(), is(2L));

        String searchedTriggerFlowId = "schedule-trigger";
        String searchedTriggerNamespace = "io.kestra.tests.schedule";

        assertThat(triggers.getResults(), Matchers.hasItems(
                allOf(
                    hasProperty("triggerId", is("schedule-every-min")),
                    hasProperty("namespace", is(searchedTriggerNamespace)),
                    hasProperty("flowId", is(searchedTriggerFlowId))
                ),
                allOf(
                    hasProperty("triggerId", is("schedule-5-min")),
                    hasProperty("namespace", is(searchedTriggerNamespace)),
                    hasProperty("flowId", is(searchedTriggerFlowId))
                )
            )
        );
    }
}
