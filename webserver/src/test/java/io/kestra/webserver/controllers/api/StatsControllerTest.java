package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCountStatistics;
import io.kestra.core.models.executions.statistics.LogStatistics;
import io.kestra.core.models.stats.SummaryStatistics;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
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
    ReactorHttpClient client;

    @Test
    void dailyStatistics() {
        var dailyStatistics = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/executions/daily", new StatsController.StatisticRequest(null, null, null, null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), null))
                .contentType(MediaType.APPLICATION_JSON),
            Argument.listOf(DailyExecutionStatistics.class)
        );

        assertThat(dailyStatistics, notNullValue());
    }

    @Test
    void logsDailyStatistics() {
        var dailyStatistics = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/logs/daily", new StatsController.LogStatisticRequest(null, null, null, null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now()))
                .contentType(MediaType.APPLICATION_JSON),
            Argument.listOf(LogStatistics.class)
        );

        assertThat(dailyStatistics, notNullValue());
    }

    @Test
    void logDailyExecutions() {
        var dailyStatistics = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/executions/latest/group-by-flow", new StatsController.LastExecutionsRequest(List.of(ExecutionRepositoryInterface.FlowFilter.builder().namespace("io.kestra.test").id("logs").build())))
                .contentType(MediaType.APPLICATION_JSON),
            Argument.listOf(Execution.class)
        );

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

    @Test
    void shouldGetDailyStatisticsGroupByNamespace() {
        // Given
        StatsController.ByNamespaceStatisticRequest body = new StatsController.ByNamespaceStatisticRequest(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now());

        // When
        Map<String, ExecutionCountStatistics> response = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/executions/daily/group-by-namespace", body)
                .contentType(MediaType.APPLICATION_JSON),
            Argument.mapOf(String.class, ExecutionCountStatistics.class)
        );

        // Then
        assertThat(response, notNullValue());
    }

    @Test
    void shouldGetSummary() {
        // Given
        StatsController.SummaryRequest body = new StatsController.SummaryRequest(null, null, null);

        // When
        SummaryStatistics response = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/stats/summary", body)
                .contentType(MediaType.APPLICATION_JSON),
            SummaryStatistics.class
        );

        // Then
        assertThat(response, notNullValue());
    }

}