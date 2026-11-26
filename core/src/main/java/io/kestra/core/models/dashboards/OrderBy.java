package io.kestra.core.models.dashboards;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class OrderBy {
    @Schema(title = "Column to sort by, named as in `columns`.")
    @NotNull
    @NotBlank
    private String column;

    @Schema(title = "Sort direction: `ASC` or `DESC`.")
    @Builder.Default
    private Order order = Order.ASC;
}
