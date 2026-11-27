package io.kestra.plugin.core.dashboard.chart.tables;

import io.kestra.core.models.dashboards.ChartOption;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(title = "Header row of the table.")
    @Builder.Default
    private HeaderOption header = HeaderOption.builder().build();

    @Schema(title = "Pagination of the table.")
    @Builder.Default
    private PaginationOption pagination = PaginationOption.builder().build();

    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class HeaderOption {
        @Schema(title = "Whether the header row is shown.")
        @Builder.Default
        private boolean enabled = true;
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class PaginationOption {
        @Schema(title = "Whether the rows are split into pages.")
        @Builder.Default
        private boolean enabled = true;
    }
}
