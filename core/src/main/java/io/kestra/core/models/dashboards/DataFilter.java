package io.kestra.core.models.dashboards;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.repositories.QueryBuilderInterface;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    private List<AbstractFilter<F>> where;

    private Map<String, Order> orderBy;

    public Set<F> aggregationForbiddenFields() {
        return Collections.emptySet();
    }

    public abstract Class<? extends QueryBuilderInterface<F>> repositoryClass();
}
