package io.kestra.core.models.dashboards;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.plugin.core.dashboard.data.IData;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.kestra.core.utils.RegexPatterns.JAVA_IDENTIFIER_REGEX;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@EqualsAndHashCode
public abstract class DataFilter<F extends Enum<F>, C extends ColumnDescriptor<F>> implements io.kestra.core.models.Plugin, IData<F> {
    @NotNull
    @NotBlank
    @Pattern(regexp = JAVA_IDENTIFIER_REGEX)
    private String type;

    @Valid
    private Map<String, C> columns;

    @Setter
    @Valid
    @Nullable
    private List<AbstractFilter<F>> where;

    private List<OrderBy> orderBy;

    public Set<F> aggregationForbiddenFields() {
        return Collections.emptySet();
    }

    public void updateWhereWithGlobalFilters(List<QueryFilter> queryFilterList, ZonedDateTime startDate, ZonedDateTime endDate) {
        this.where = whereWithGlobalFilters(queryFilterList, startDate, endDate, this.where);
    }

    public abstract Class<? extends QueryBuilderInterface<F>> repositoryClass();

}
