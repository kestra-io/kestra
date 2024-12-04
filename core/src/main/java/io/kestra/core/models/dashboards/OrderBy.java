package io.kestra.core.models.dashboards;

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
    @NotNull
    @NotBlank
    private String column;

    @Builder.Default
    private Order order = Order.ASC;
}
