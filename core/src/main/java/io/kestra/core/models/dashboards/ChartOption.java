package io.kestra.core.models.dashboards;

import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class ChartOption {
    @Schema(title = "Title shown above the chart.")
    @NotNull
    @NotBlank
    private String displayName;

    @Schema(title = "Subtitle shown below the title.")
    private String description;

    @Schema(title = "Width of the chart on the dashboard's 12-column grid.", description = "From `1` to `12`: `6` takes half a row, `12` a full row.")
    @Builder.Default
    @Min(1)
    @Max(12)
    private int width = 6;

    public List<String> neededColumns() {
        return Collections.emptyList();
    }
}
