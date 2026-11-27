package io.kestra.core.models.dashboards.charts;

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
public class LegendOption {
    @Schema(title = "Whether the legend is shown.")
    @Builder.Default
    private boolean enabled = true;
}
