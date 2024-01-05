package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class StatsControllerTest extends JdbcH2ControllerTest {

    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    void dailyStatistics() {
        var dailyStatistics = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/executions/daily", new StatsController.StatisticRequest(null, null, null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now()))
                .contentType(MediaType.APPLICATION_JSON),
            Argument.listOf(DailyExecutionStatistics.class)
        );

        assertThat(dailyStatistics, notNullValue());
    }

    @Test
    void dailyGroupByFlowStatistics() {
        var dailyStatistics = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/executions/daily/group-by-flow", new StatsController.ByFlowStatisticRequest(null, null, null, null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), null))
                .contentType(MediaType.APPLICATION_JSON),
            Map.class);

        assertThat(dailyStatistics, notNullValue());
    }

    @Test
    void lastExecutions() {
        var dailyStatistics = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/executions/latest/group-by-flow", new StatsController.LastExecutionsRequest(List.of(ExecutionRepositoryInterface.FlowFilter.builder().namespace("io.kestra.test").id("logs").build())))
                .contentType(MediaType.APPLICATION_JSON),
            Argument.listOf(Execution.class)
        );

        assertThat(dailyStatistics, notNullValue());
    }

}