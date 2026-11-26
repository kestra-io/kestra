package io.kestra.core.models.dashboards.charts;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ChartOption;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.utils.RegexPatterns.JAVA_IDENTIFIER_REGEX;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
public abstract class Chart<P extends ChartOption> implements io.kestra.core.models.Plugin {
    @Schema(title = "Identifier of the chart, unique within the dashboard.")
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    private String id;

    @Schema(title = "Type of the chart, such as `io.kestra.plugin.core.dashboard.chart.TimeSeries`.")
    @NotNull
    @NotBlank
    @Pattern(regexp = JAVA_IDENTIFIER_REGEX)
    protected String type;

    @Schema(title = "Title, width and display settings of the chart.")
    @Valid
    private P chartOptions;
}
