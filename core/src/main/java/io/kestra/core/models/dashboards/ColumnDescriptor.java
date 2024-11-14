package io.kestra.core.models.dashboards;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class ColumnDescriptor<F extends Enum<F>> {
    private F field;
    private String displayName;
    private AggregationType agg;
    private String labelKey;
}
