package io.kestra.core.models.settings;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@ToString
@AllArgsConstructor
public class DashboardSettings {
    String defaultHomeDashboard;
    String defaultFlowOverviewDashboard;
    String defaultNamespaceOverviewDashboard;
}
