package io.kestra.core.models.settings;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@Introspected
@NoArgsConstructor
@AllArgsConstructor
public class PreferencesSettings {
    DashboardSettings dashboard;
}
