package io.kestra.core.models.dashboards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
public class Dashboard implements HasUID, DeletedInterface {
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @Hidden
    @NotNull
    @NotBlank
    private String id;

    @NotNull
    @NotBlank
    private String title;

    private String description;

    @Valid
    @Builder.Default
    private TimeWindow timeWindow = TimeWindow.builder().build();

    @Valid
    private List<Chart<?>> charts;

    @Hidden
    @NotNull
    @Builder.Default
    private boolean deleted = false;

    @Hidden
    private Instant created;

    @Hidden
    private Instant updated;

    private String sourceCode;

    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(
            tenantId,
            id
        );
    }

    public Dashboard toDeleted() {
        return this.toBuilder()
            .deleted(true)
            .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dashboard dashboard = (Dashboard) o;
        return deleted == dashboard.deleted && Objects.equals(tenantId, dashboard.tenantId) && Objects.equals(id, dashboard.id) && Objects.equals(title, dashboard.title) && Objects.equals(description, dashboard.description) && Objects.equals(timeWindow, dashboard.timeWindow) && Objects.equals(charts, dashboard.charts) && Objects.equals(sourceCode, dashboard.sourceCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, id, title, description, timeWindow, charts, deleted, sourceCode);
    }
}
