package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.QueryTimeoutException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.services.ChartDataService;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class DashboardControllerQueryTimeoutTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @MockBean(ChartDataService.class)
    ChartDataService chartDataService() throws IOException {
        ChartDataService chartDataService = mock(ChartDataService.class);
        when(chartDataService.generate(any(), any(), any(), any(), any())).thenThrow(new QueryTimeoutException(Duration.ofSeconds(30), null));
        return chartDataService;
    }

    @Test
    void shouldReportATimedOutChartQueryAsABadRequestCarryingItsMessage() {
        String chartYaml = """
            id: executions
            type: io.kestra.plugin.core.dashboard.chart.Table
            data:
              type: io.kestra.plugin.core.dashboard.data.Executions
              columns:
                id:
                  field: ID
            """;

        HttpClientResponseException exception = catchThrowableOfType(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST("/api/v1/main/dashboards/charts/preview", new DashboardController.PreviewRequest(chartYaml, null)), PagedResults.class)
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(400);
        assertThat(exception.getMessage()).contains("did not complete within 30 seconds");
    }
}
