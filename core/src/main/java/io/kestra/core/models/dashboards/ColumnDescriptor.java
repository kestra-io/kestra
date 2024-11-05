package io.kestra.core.models.dashboards;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
@JsonTypeInfo(use=JsonTypeInfo.Id.DEDUCTION)
public class ColumnDescriptor<F extends Enum<F>> {
    @NotNull
    @NotBlank
    private F field;
    private String displayName;
    private AggregationType agg;
    private String labelKey;
}
