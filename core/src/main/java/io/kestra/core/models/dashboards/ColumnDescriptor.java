package io.kestra.core.models.dashboards;

import com.fasterxml.jackson.annotation.JsonAlias;

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
    // `labelKey` is the legacy name (Executions LABELS); kept as a read alias for backward compatibility.
    @JsonAlias("labelKey")
    private String key;
}
