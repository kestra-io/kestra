package io.kestra.core.models.dashboards;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.kestra.plugin.core.dashboard.data.Logs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@EqualsAndHashCode
public abstract class DataFilter<F extends Enum<F>, C extends ColumnDescriptor<F>> implements io.kestra.core.models.Plugin {
    @NotNull
    @NotBlank
    @Pattern(regexp = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    private String type;


    private Map<String, C> columns;

    @Setter
    private List<AbstractFilter<F>> where;

    private List<OrderBy> orderBy;

    public Set<F> aggregationForbiddenFields() {
        return Collections.emptySet();
    }

    public abstract Class<? extends QueryBuilderInterface<F>> repositoryClass();

    public abstract void setGlobalFilter(GlobalFilter globalFilter);

}
