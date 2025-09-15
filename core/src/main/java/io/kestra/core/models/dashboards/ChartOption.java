package io.kestra.core.models.dashboards;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class ChartOption {
    @NotNull
    @NotBlank
    private String displayName;

    private String description;

    @Builder.Default
    @Min(1)
    @Max(12)
    private int width = 6;

    public List<String> neededColumns() {
        return Collections.emptyList();
    }
}
