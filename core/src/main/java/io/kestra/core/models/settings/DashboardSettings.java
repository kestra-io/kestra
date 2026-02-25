package io.kestra.core.models.settings;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
@Introspected
@NoArgsConstructor
@ToString
@AllArgsConstructor
public class DashboardSettings {
    String defaultHomeDashboard;
    String defaultFlowOverviewDashboard;
    String defaultNamespaceOverviewDashboard;
}
