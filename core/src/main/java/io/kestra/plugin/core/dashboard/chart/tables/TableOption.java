package io.kestra.plugin.core.dashboard.chart.tables;

import io.kestra.core.models.dashboards.ChartOption;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class TableOption extends ChartOption {
    @Builder.Default
    private HeaderOption header = HeaderOption.builder().build();

    @Builder.Default
    private PaginationOption pagination = PaginationOption.builder().build();

    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class HeaderOption {
        @Builder.Default
        private boolean enabled = true;
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class PaginationOption {
        @Builder.Default
        private boolean enabled = true;
    }
}
