package io.kestra.core.models.settings;

import lombok.*;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PreferencesSettings {
    DashboardSettings dashboard;
}
