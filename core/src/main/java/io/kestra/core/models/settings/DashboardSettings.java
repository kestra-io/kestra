package io.kestra.core.models.settings;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
@Introspected
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSettings {
    String defaultHomeDashboard;
    String defaultFlowOverviewDashboard;
    String defaultNamespaceOverviewDashboard;
}
