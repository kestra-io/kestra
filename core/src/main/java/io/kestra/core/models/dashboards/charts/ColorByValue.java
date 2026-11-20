package io.kestra.core.models.dashboards.charts;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class ColorByValue {
    @NotNull
    @NotBlank
    @Schema(title = "Value of the chart's column that this entry applies to.")
    private String value;

    @Schema(title = "Text shown for the value in the legend and tooltips.")
    private String label;

    @Schema(
        title = "Color of the value.",
        description = "A hex color such as `#d32f2f`, or an execution state or log level name such as `failed`, `warning` or `success` to reuse its color. A state or level name follows the light and dark themes, a hex color does not."
    )
    private String color;
}
