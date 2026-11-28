package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.ChartMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ChartRows;
import io.kestra.core.services.ChartDataService;

import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@KestraTest
class DashboardControllerChartMetadataTest {
    private static final String CHART = """
        id: executions
        type: io.kestra.plugin.core.dashboard.chart.Table
        data:
          type: io.kestra.plugin.core.dashboard.data.Executions
          columns:
            id:
              field: ID
        """;

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ChartDataService chartDataService;

    @MockBean(ChartDataService.class)
    ChartDataService chartDataService() {
        return mock(ChartDataService.class);
    }

    @Test
    void shouldReturnTheMetadataOfTheRowsWhenTheDataSourceHasSome() throws IOException {
        ChartMetadata metadata = new ChartMetadata("exec1", Instant.parse("2026-11-09T10:00:00Z"), "SUCCESS", 10000, true);
        doReturn(new ChartRows(List.of(Map.of("id", "a")), 1, metadata)).when(chartDataService).generate(any(), any(), any(), any(), any());

        assertThat(preview().metadata()).isEqualTo(metadata);
    }

    @Test
    void shouldReturnNoMetadataWhenTheDataSourceHasNone() throws IOException {
        doReturn(new ArrayListTotal<>(List.of(Map.of("id", "a")), 1)).when(chartDataService).generate(any(), any(), any(), any(), any());

        DashboardController.ChartData chartData = preview();

        assertThat(chartData.results()).hasSize(1);
        assertThat(chartData.metadata()).isNull();
    }

    private DashboardController.ChartData preview() {
        return client.toBlocking().retrieve(
            POST("/api/v1/main/dashboards/charts/preview", new DashboardController.PreviewRequest(CHART, null)),
            DashboardController.ChartData.class
        );
    }
}
