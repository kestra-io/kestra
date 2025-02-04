package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.GlobalFilter;
import io.kestra.core.models.dashboards.filters.*;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.core.validations.ExecutionsDataFilterValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@ExecutionsDataFilterValidation
@Schema(title = "Executions")
@JsonTypeName("Executions")
public class Executions<C extends ColumnDescriptor<Executions.Fields>> extends DataFilter<Executions.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Executions.Fields>> repositoryClass() {
        return ExecutionRepositoryInterface.class;
    }

    @Override
    public void setGlobalFilter(GlobalFilter globalFilter) {
        List<AbstractFilter<Fields>> where = this.getWhere() != null ? new ArrayList<>(this.getWhere()) : new ArrayList<>();

        if (globalFilter.getNamespace() != null) {
            where.removeIf(f -> f.getField().equals(Fields.NAMESPACE));
            where.add(EqualTo.<Executions.Fields>builder().field(Fields.NAMESPACE).value(globalFilter.getNamespace()).build());
        }
        if (globalFilter.getLabels() != null) {
            where.removeIf(f -> f.getField().equals(Fields.LABELS));
            where.add(Contains.<Executions.Fields>builder().field(Fields.LABELS).value(globalFilter.getLabels()).build());
        }
        if (globalFilter.getStartDate() != null || globalFilter.getEndDate() != null) {
            where.removeIf(f -> f.getField().equals(Fields.START_DATE));
            if (globalFilter.getStartDate() != null) {
                where.add(GreaterThanOrEqualTo.<Executions.Fields>builder().field(Fields.START_DATE).value(globalFilter.getStartDate().toOffsetDateTime()).build());
            }
            if (globalFilter.getEndDate() != null) {
                where.add(LessThanOrEqualTo.<Executions.Fields>builder().field(Fields.START_DATE).value(globalFilter.getEndDate().toOffsetDateTime()).build());
            }
        }

        this.setWhere(where);
    }

    public enum Fields {
        ID,
        NAMESPACE,
        FLOW_ID,
        FLOW_REVISION,
        STATE,
        DURATION,
        LABELS,
        START_DATE,
        END_DATE,
        TRIGGER_EXECUTION_ID
    }
}
