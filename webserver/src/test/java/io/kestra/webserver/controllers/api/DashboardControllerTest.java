package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.webserver.models.GlobalFilter;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DashboardControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void full() {
        String dashboardYaml = """
            title: Some Dashboard
            description: Default overview dashboard
            timeWindow:
              default: P30D # P30DT30H
              max: P365D

            charts:
              - id: logs_timeseries
                type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                chartOptions:
                  displayName: Error Logs
                  description: Count of ERROR logs per date
                  legend:
                    enabled: true
                  column: date
                  colorByColumn: level
                data:
                  type: io.kestra.plugin.core.dashboard.data.Logs
                  columns:
                    date:
                      field: DATE
                      displayName: Execution Date
                    level:
                      field: LEVEL
                    total:
                      displayName: Total Error Logs
                      agg: COUNT
                      graphStyle: BARS
                  where:
                    - field: LEVEL
                      type: IN
                      values:
                        - ERROR""";

        // Create a dashboard
        Dashboard dashboard = client.toBlocking().retrieve(
            POST("/api/v1/dashboards", dashboardYaml).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        );
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getId()).isNotNull();
        assertThat(dashboard.getTitle()).isEqualTo("Some Dashboard");
        assertThat(dashboard.getDescription()).isEqualTo("Default overview dashboard");

        // Get a dashboard
        Dashboard get = client.toBlocking().retrieve(
            GET("/api/v1/dashboards/" + dashboard.getId()),
            Dashboard.class
        );
        assertThat(get).isNotNull();
        assertThat(get.getId()).isEqualTo(dashboard.getId());

        // List dashboards
        List<Dashboard> dashboards = client.toBlocking().retrieve(
            GET("/api/v1/dashboards"),
            Argument.listOf(Dashboard.class)
        );
        assertThat(dashboards).hasSize(1);

        // Compute a dashboard
        List<Map> chartData = client.toBlocking().retrieve(
            POST("/api/v1/dashboards/" + dashboard.getId() + "/charts/logs_timeseries", GlobalFilter.builder().filters(Collections.emptyList()).build()),
            Argument.listOf(Map.class)
        );
        assertThat(chartData).isNotNull();
        assertThat(chartData).hasSize(1);

        // Delete a dashboard
        HttpResponse<Void> deleted = client.toBlocking().exchange(
            DELETE("/api/v1/dashboards/" + dashboard.getId())
        );
        assertThat(deleted).isNotNull();
        assertThat(deleted.code()).isEqualTo(204);
    }
}