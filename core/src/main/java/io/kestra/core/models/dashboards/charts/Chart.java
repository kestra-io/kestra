package io.kestra.core.models.dashboards.charts;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ChartOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
public abstract class Chart<P extends ChartOption> implements io.kestra.core.models.Plugin {
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    private String id;

    @NotNull
    @NotBlank
    @Pattern(regexp = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    protected String type;

    private P chartOptions;
}
