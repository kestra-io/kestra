package io.kestra.core.models.dashboards;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;

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

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@ToString
public class Dashboard implements HasUID, SoftDeletable<Dashboard> {
    // Reserved sentinel id resolving to the built-in default dashboard; not a real stored dashboard, never shown in autocompletion
    public static final String DEFAULT_DASHBOARD_ID = "_default";
    private static final String DEFAULT_MAIN_DEFINITION_RESOURCE = "dashboards/default_main_definition.yaml";

    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

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

    @Hidden
    private String sourceCode;

    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(
            tenantId,
            id
        );
    }

    @Override
    public Dashboard toDeleted() {
        return this.toBuilder()
            .deleted(true)
            .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Dashboard dashboard = (Dashboard) o;
        return deleted == dashboard.deleted && Objects.equals(tenantId, dashboard.tenantId) && Objects.equals(id, dashboard.id) && Objects.equals(title, dashboard.title)
            && Objects.equals(description, dashboard.description) && Objects.equals(timeWindow, dashboard.timeWindow) && Objects.equals(charts, dashboard.charts)
            && Objects.equals(sourceCode, dashboard.sourceCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, id, title, description, timeWindow, charts, deleted, sourceCode);
    }

    public static Dashboard defaultDashboard(String tenantId) {
        String yaml = readClasspathResource(DEFAULT_MAIN_DEFINITION_RESOURCE);

        return YamlParser.parse(yaml, Dashboard.class).toBuilder()
            .id(DEFAULT_DASHBOARD_ID)
            .tenantId(tenantId)
            .sourceCode("id: " + DEFAULT_DASHBOARD_ID + "\n" + yaml)
            .deleted(false)
            .build();
    }

    public static String readClasspathResource(String path) {
        try (var is = Dashboard.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
