package io.kestra.core.models.settings;

import io.micronaut.core.annotation.Introspected;
import lombok.*;

@Getter
@Builder(toBuilder = true)
@Introspected
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PreferencesSettings {
    DashboardSettings dashboard;
}
