package io.kestra.core.models.dashboards;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class DashboardValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldRejectChartsSharingAnId() {
        Dashboard dashboard = YamlParser.parse(
            """
                id: dashboard
                title: Dashboard
                charts:
                  - id: dup
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Successes
                    data:
                      type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                      columns:
                        field: ID
                        agg: COUNT
                  - id: dup
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Failures
                    data:
                      type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                      columns:
                        field: ID
                        agg: COUNT
                """,
            Dashboard.class
        );

        assertThat(modelValidator.isValid(dashboard))
            .hasValueSatisfying(violation -> assertThat(violation.getMessage()).contains("Duplicate chart id [dup]"));
    }
}
