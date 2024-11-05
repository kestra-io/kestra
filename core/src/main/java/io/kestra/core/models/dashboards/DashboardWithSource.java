package io.kestra.core.models.dashboards;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
public class DashboardWithSource extends Dashboard {
    String sourceCode;

    public Dashboard toDashboard() {
        return Dashboard.builder()
            .tenantId(this.tenantId)
            .id(this.id)
            .title(this.title)
            .description(this.description)
            .deleted(this.deleted)
            .timeWindow(this.timeWindow)
            .charts(this.charts)
            .created(this.created)
            .updated(this.updated)
            .build();
    }

    @SuppressWarnings("deprecation")
    public static DashboardWithSource of(Dashboard dashboard, String source) {
        return DashboardWithSource.builder()
            .tenantId(dashboard.tenantId)
            .id(dashboard.id)
            .title(dashboard.title)
            .description(dashboard.description)
            .deleted(dashboard.deleted)
            .timeWindow(dashboard.timeWindow)
            .charts(dashboard.charts)
            .sourceCode(source)
            .created(dashboard.created)
            .updated(dashboard.updated)
            .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DashboardWithSource that = (DashboardWithSource) o;
        return super.equals(o) && Objects.equals(sourceCode, that.sourceCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sourceCode);
    }
}
